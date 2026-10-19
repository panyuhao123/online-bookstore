package cn.edu.bookstore.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 事务感知的缓存管理器：把缓存的「写操作」推迟到事务提交之后再执行。
 *
 * <p><b>为什么需要它（本项目真实存在的不一致窗口）</b><br>
 * 下单、改书、改分类等方法同时带 {@code @Transactional} 与 {@code @CacheEvict}。Spring 的缓存切面
 * 默认在**方法返回后、事务提交前**执行清理，于是存在这样一个时序：
 * <pre>
 *   T1  createOrder()  开启事务
 *   T1  插入订单、扣减库存
 *   T1  evict("book")           ← 缓存已经被清空，但事务尚未提交
 *   T2  读图书详情              → 缓存未命中 → 查库（看不到 T1 未提交的扣减）→ 把旧库存写回缓存
 *   T1  commit
 * </pre>
 * 事务提交后，缓存里躺着的却是 T2 回填的旧值，要等 TTL（默认 30 分钟）才自然过期。
 * 把清理动作推迟到提交之后，上面的 T2 就只可能读到「提交后的新数据」或「提交前的旧数据」，
 * 两者都与数据库当时的状态一致 —— 脏值即使被回填，也会被紧随其后的提交后清理立刻丢弃。
 *
 * <p><b>为什么不用 Spring 自带的 {@code TransactionAwareCacheManagerProxy}</b><br>
 * 它的 {@code evict} 会 {@code registerSynchronization(afterCommit -> target.evict(key))}，
 * 但**没有吞掉异常**：一旦 Redis 在提交之后失败，异常会从 {@code afterCommit} 回调抛出，
 * 一路传播给「数据其实已经提交成功」的业务调用方；而此时已经脱离了缓存切面，
 * {@link org.springframework.cache.interceptor.CacheErrorHandler} 根本兜不住。
 * 结果是「缓存清理失败」被放大成「接口报错」，违背了「缓存只是加速项」的前提。
 * 因此这里自己实现，并在提交后回调里把异常降级为一条 WARN 日志。
 *
 * <p><b>其它说明</b>
 * <ul>
 *     <li>读操作（get）原样穿透，不做任何延迟 —— 延迟读没有意义；</li>
 *     <li>{@code putIfAbsent} 也保持即时执行：它需要返回旧值，推迟之后返回值就失去意义了；</li>
 *     <li>没有事务时行为与不加这一层完全一致（直接执行）；</li>
 *     <li>可通过 {@code bookstore.cache.evict-after-commit=false} 关闭本层，回到旧行为做对照实验。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@RequiredArgsConstructor
public class TransactionAwareCacheManager implements CacheManager {

    /** 内层委托（通常是 {@link ResilientCacheManager}，负责熔断与降级） */
    private final CacheManager delegate;

    /** 缓存名 -> 包装后的 Cache，避免每次请求重复包装 */
    private final ConcurrentMap<String, Cache> wrapped = new ConcurrentHashMap<>();

    @Override
    public Cache getCache(String name) {
        Cache cache = wrapped.get(name);
        if (cache != null) {
            return cache;
        }
        Cache raw = delegate.getCache(name);
        if (raw == null) {
            return null;
        }
        return wrapped.computeIfAbsent(name, key -> new CommitDeferredCache(raw));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    /** 写操作按事务是否活跃决定「立即执行」还是「登记到提交后执行」 */
    private static class CommitDeferredCache implements Cache {

        private final Cache delegate;

        CommitDeferredCache(Cache delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return delegate.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return delegate.get(key, type);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return delegate.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            if (!defer(() -> delegate.put(key, value))) {
                delegate.put(key, value);
            }
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            // 需要同步返回旧值，推迟执行会让返回值失去意义，因此保持即时执行
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            if (!defer(() -> delegate.evict(key))) {
                delegate.evict(key);
            }
        }

        @Override
        public void clear() {
            if (!defer(delegate::clear)) {
                delegate.clear();
            }
        }

        /**
         * 若当前有活跃的事务同步，就把动作登记到「提交后」执行并返回 true；
         * 否则返回 false，由调用方立即执行。
         */
        private boolean defer(Runnable action) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return false;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (RuntimeException e) {
                        // 提交后回调里抛异常会污染「数据已提交」的调用方，且此时已脱离缓存切面，
                        // CacheErrorHandler 兜不住，因此这里自行降级：只记日志，绝不上抛。
                        // 缓存没清干净只是「脏数据多存活一个 TTL」，远好过接口报错。
                        log.warn("事务提交后清理缓存失败，已忽略（缓存为纯加速项，不影响已提交的业务数据）cache={} 原因={}",
                                delegate.getName(), e.getMessage());
                    }
                }
            });
            return true;
        }
    }
}
