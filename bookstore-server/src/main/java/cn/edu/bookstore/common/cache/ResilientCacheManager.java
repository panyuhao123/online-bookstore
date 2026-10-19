package cn.edu.bookstore.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 带熔断能力的缓存管理器：包装真实的 Redis CacheManager。
 *
 * <p>设计取舍：Spring Cache 的 {@link org.springframework.cache.interceptor.CacheErrorHandler}
 * 只能在「异常已经发生」之后兜底 —— 也就是说每次请求仍然要付出一次超时代价。
 * 真正要保住响应时间，必须在访问缓存「之前」就拦掉，因此这里在 Cache 层面加了熔断判断：
 * 熔断打开时 get 直接返回未命中、put/evict 直接忽略，请求立刻走数据库，零等待。
 *
 * @author 毕设项目组
 */
@Slf4j
@RequiredArgsConstructor
public class ResilientCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final CacheCircuitBreaker breaker;

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
        return wrapped.computeIfAbsent(name, key -> new ResilientCache(raw, breaker));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    /** 包装后的缓存：所有操作前先看熔断状态，异常计入熔断统计后原样抛出由 CacheErrorHandler 兜底 */
    private static class ResilientCache implements Cache {

        private final Cache delegate;
        private final CacheCircuitBreaker breaker;

        ResilientCache(Cache delegate, CacheCircuitBreaker breaker) {
            this.delegate = delegate;
            this.breaker = breaker;
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
            if (!breaker.isAvailable()) {
                return null;
            }
            try {
                ValueWrapper wrapper = delegate.get(key);
                breaker.recordSuccess();
                return wrapper;
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (!breaker.isAvailable()) {
                return null;
            }
            try {
                T value = delegate.get(key, type);
                breaker.recordSuccess();
                return value;
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            // 熔断期间直接执行加载逻辑（即查库），不做任何 Redis 交互
            if (!breaker.isAvailable()) {
                return loadValue(valueLoader);
            }
            try {
                T value = delegate.get(key, valueLoader);
                breaker.recordSuccess();
                return value;
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (!breaker.isAvailable()) {
                return;
            }
            try {
                delegate.put(key, value);
                breaker.recordSuccess();
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            if (!breaker.isAvailable()) {
                return null;
            }
            try {
                ValueWrapper wrapper = delegate.putIfAbsent(key, value);
                breaker.recordSuccess();
                return wrapper;
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        @Override
        public void evict(Object key) {
            if (!breaker.isAvailable()) {
                return;
            }
            try {
                delegate.evict(key);
                breaker.recordSuccess();
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        @Override
        public void clear() {
            if (!breaker.isAvailable()) {
                return;
            }
            try {
                delegate.clear();
                breaker.recordSuccess();
            } catch (RuntimeException e) {
                breaker.recordFailure();
                throw e;
            }
        }

        private <T> T loadValue(Callable<T> valueLoader) {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(null, valueLoader, e);
            }
        }
    }
}
