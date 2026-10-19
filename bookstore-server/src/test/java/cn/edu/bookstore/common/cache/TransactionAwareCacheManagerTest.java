package cn.edu.bookstore.common.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 缓存「事务提交后失效」行为测试。
 *
 * <p>这里用内存版 Cache 而不是 Mockito 打桩：断言的是「缓存里那条数据到底还在不在」，
 * 用真实容器能同时覆盖 put / evict / clear 的真实语义，比 verify(mock).evict(...) 更接近事实。
 *
 * <p>事务同步用 {@link TransactionSynchronizationManager} 手工激活与触发，
 * 因此不需要启动 Spring 容器，也不需要数据库。
 *
 * @author 毕设项目组
 */
class TransactionAwareCacheManagerTest {

    private final MapCache delegateCache = new MapCache("book");
    private final CacheManager delegateManager = new SingleCacheManager(delegateCache);
    private final TransactionAwareCacheManager manager = new TransactionAwareCacheManager(delegateManager);

    @AfterEach
    void tearDown() {
        // 事务同步状态存在 ThreadLocal 里，测试后必须清掉，避免污染其它用例
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ------------------------------------------------------------ 基本委托

    @Test
    @DisplayName("getCache 对未知缓存名返回 null（不抛异常）")
    void unknownCacheNameReturnsNull() {
        assertNull(manager.getCache("not-exists"));
    }

    @Test
    @DisplayName("getCache 缓存包装实例：同一名字返回同一个对象")
    void getCacheReusesWrapper() {
        assertSame(manager.getCache("book"), manager.getCache("book"));
    }

    @Test
    @DisplayName("getCacheNames 原样委托")
    void getCacheNamesDelegates() {
        assertEquals(List.of("book"), new ArrayList<>(manager.getCacheNames()));
    }

    // ------------------------------------------------------------ 无事务：立即执行

    @Test
    @DisplayName("无事务时：写操作立即生效（与不加这一层完全一致）")
    void writeIsImmediateWithoutTransaction() {
        Cache cache = manager.getCache("book");
        cache.put(1L, "v1");
        assertNotNull(cache.get(1L));

        cache.evict(1L);
        assertNull(cache.get(1L), "没有事务时应当立即清理");
        assertEquals(0, delegateCache.storedCount());

        cache.put(2L, "v2");
        cache.clear();
        assertEquals(0, delegateCache.storedCount(), "没有事务时 clear 应当立即生效");
    }

    // ------------------------------------------------------------ 有事务：推迟到提交后

    @Test
    @DisplayName("事务中：evict 被推迟，提交前缓存仍在、提交后才被清理")
    void evictDeferredUntilCommit() {
        Cache cache = manager.getCache("book");
        cache.put(100L, "旧库存");

        TransactionSynchronizationManager.initSynchronization();
        cache.evict(100L);

        assertNotNull(cache.get(100L),
                "事务尚未提交时不得清缓存 —— 否则并发请求会把库里的旧值回填进来，形成长期脏数据");

        triggerAfterCommit();

        assertNull(cache.get(100L), "事务提交后应当完成清理");
    }

    @Test
    @DisplayName("事务中：clear 同样被推迟到提交后")
    void clearDeferredUntilCommit() {
        Cache cache = manager.getCache("book");
        cache.put(1L, "a");
        cache.put(2L, "b");

        TransactionSynchronizationManager.initSynchronization();
        cache.clear();

        assertEquals(2, delegateCache.storedCount(), "提交前 clear 不应生效");
        triggerAfterCommit();
        assertEquals(0, delegateCache.storedCount(), "提交后 clear 应当生效");
    }

    @Test
    @DisplayName("事务中：put 同样被推迟到提交后")
    void putDeferredUntilCommit() {
        Cache cache = manager.getCache("book");

        TransactionSynchronizationManager.initSynchronization();
        cache.put(9L, "新值");

        assertEquals(0, delegateCache.storedCount(), "提交前不应写入缓存");
        triggerAfterCommit();
        assertEquals(1, delegateCache.storedCount(), "提交后应当完成写入");
    }

    @Test
    @DisplayName("事务回滚时：不做任何清理（数据没变，缓存本来就与库一致）")
    void noEvictOnRollback() {
        Cache cache = manager.getCache("book");
        cache.put(200L, "原值");

        TransactionSynchronizationManager.initSynchronization();
        cache.evict(200L);
        triggerAfterRollback();

        assertNotNull(cache.get(200L), "回滚后缓存应当原封不动");
    }

    @Test
    @DisplayName("事务中：读操作不受影响，立即穿透")
    void readIsNotDeferred() {
        Cache cache = manager.getCache("book");
        cache.put(1L, "v");

        TransactionSynchronizationManager.initSynchronization();
        assertNotNull(cache.get(1L), "读操作必须立即可见，延迟读没有意义");
        assertNotNull(cache.get(1L, String.class));
        assertEquals("v", cache.get(1L, () -> "v"));
    }

    @Test
    @DisplayName("事务中：putIfAbsent 保持即时执行（推迟会让返回值失去意义）")
    void putIfAbsentIsImmediate() {
        Cache cache = manager.getCache("book");

        TransactionSynchronizationManager.initSynchronization();
        Cache.ValueWrapper wrapper = cache.putIfAbsent(3L, "first");

        assertNull(wrapper, "首次写入应当返回 null 表示原有值不存在");
        assertEquals(1, delegateCache.storedCount(), "putIfAbsent 必须即时生效");
    }

    // ------------------------------------------------------------ 提交后失败的降级

    @Test
    @DisplayName("提交后清理失败：只记日志不上抛，不把「缓存没清干净」放大成「接口报错」")
    void failureAfterCommitIsSwallowed() {
        MapCache broken = new MapCache("book");
        broken.failOnEvict = true;
        TransactionAwareCacheManager brokenManager =
                new TransactionAwareCacheManager(new SingleCacheManager(broken));
        Cache cache = brokenManager.getCache("book");

        TransactionSynchronizationManager.initSynchronization();
        cache.evict(1L);

        assertDoesNotThrow(this::triggerAfterCommit,
                "提交后回调抛异常会传播给「数据其实已提交成功」的业务调用方，必须被吞掉");
    }

    // ------------------------------------------------------------ 辅助

    /** 触发已登记事务同步的提交回调（顺序与提交一致） */
    private void triggerAfterCommit() {
        List<TransactionSynchronization> syncs =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        TransactionSynchronizationManager.clearSynchronization();
        syncs.forEach(TransactionSynchronization::afterCommit);
    }

    /** 触发已登记事务同步的回滚回调 */
    private void triggerAfterRollback() {
        List<TransactionSynchronization> syncs =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        TransactionSynchronizationManager.clearSynchronization();
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    /** 可变值容器：lambda 中需要赋值时使用 */
    private static class ValueHolder {
        Cache.ValueWrapper wrapper;
    }

    /** 内存版 Cache：内容放在 Map 里，便于直接断言「清了没清」 */
    private static class MapCache implements Cache {

        private final String name;
        private final Map<Object, Object> raw = new ConcurrentHashMap<>();
        private boolean failOnEvict;

        MapCache(String name) {
            this.name = name;
        }

        int storedCount() {
            return raw.size();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return raw;
        }

        @Override
        public ValueWrapper get(Object key) {
            Object value = raw.get(key);
            return value == null ? null : new SimpleValueWrapper(value);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            Object value = raw.get(key);
            return value == null ? null : type.cast(value);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            Object value = raw.get(key);
            if (value != null) {
                @SuppressWarnings("unchecked")
                T cast = (T) value;
                return cast;
            }
            try {
                T loaded = valueLoader.call();
                raw.put(key, loaded);
                return loaded;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            raw.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            Object existing = raw.putIfAbsent(key, value);
            return existing == null ? null : new SimpleValueWrapper(existing);
        }

        @Override
        public void evict(Object key) {
            if (failOnEvict) {
                throw new IllegalStateException("模拟 Redis 提交后不可用");
            }
            raw.remove(key);
        }

        @Override
        public void clear() {
            raw.clear();
        }
    }

    /** 只提供一个缓存的管理器 */
    private static class SingleCacheManager implements CacheManager {

        private final Cache cache;

        SingleCacheManager(Cache cache) {
            this.cache = cache;
        }

        @Override
        public Cache getCache(String name) {
            return cache.getName().equals(name) ? cache : null;
        }

        @Override
        public Collection<String> getCacheNames() {
            return List.of(cache.getName());
        }
    }
}
