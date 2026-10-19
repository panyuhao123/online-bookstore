package cn.edu.bookstore.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cn.edu.bookstore.common.cache.CacheCircuitBreaker;
import cn.edu.bookstore.common.cache.ResilientCacheManager;
import cn.edu.bookstore.common.cache.TransactionAwareCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 缓存配置。
 *
 * <p>三个关键点：
 * <ol>
 *     <li><b>可开关</b>：bookstore.cache.enabled=false 时整个配置类不加载，
 *         @EnableCaching 也随之失效，缓存注解自动变成空操作 —— 不用为了关缓存去删代码；</li>
 *     <li><b>容错降级</b>：实现了 {@link CacheErrorHandler}，Redis 连不上或超时只是打日志，
 *         请求照常走数据库。毕设演示最怕「Redis 忘了启动导致整个系统不可用」，
 *         这个取舍让缓存成为纯加速项而不是单点故障；</li>
 *     <li><b>JSON 序列化 + 类型信息</b>：缓存里存的是可读 JSON（方便直接用 redis-cli 排查），
 *         并写入类型信息，保证取出来还是原对象。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookstore.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig implements CachingConfigurer {

    /** 购物车缓存名（与 @Cacheable(value = "cart") 保持一致） */
    public static final String CART_CACHE_NAME = "cart";

    /** 购物车缓存有效期（分钟）：短 TTL，保证库存/下架状态不会长时间过期 */
    private static final int CART_TTL_MINUTES = 5;

    private final AppProperties appProperties;
    private final CacheCircuitBreaker cacheCircuitBreaker;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = baseConfig()
                // 设置过期时间，避免缓存越积越多；同时限制脏数据存活时间
                .entryTtl(Duration.ofMinutes(appProperties.getCache().getTtlMinutes()));

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                // 购物车单独用更短的 TTL：它的内容包含「库存是否充足 / 商品是否下架」等
                // 会随别人下单而变化的信息，缓存太久会让用户看到过期的库存状态。
                // 购物车本身写入频繁、命中率不如详情页高，过短的 TTL 也不会带来太大收益损失，
                // 所以这里把「新鲜度」排在「命中率」前面。
                .withInitialCacheConfigurations(Map.of(
                        CART_CACHE_NAME, baseConfig().entryTtl(Duration.ofMinutes(CART_TTL_MINUTES))))
                .build();

        // ⚠ 这一步不能省（实测踩过的坑）：
        // RedisCacheManager 的「按缓存名区分配置」只在 initializeCaches() 阶段生效，
        // 而该方法由 Spring 在「实例本身作为 Bean 初始化」时调用。
        // 这里把 RedisCacheManager 包进 ResilientCacheManager 后返回，它自己不再是 Bean，
        // initializeCaches() 便永远不会被调用，cacheMap 始终为空 ——
        // 之后每次 getCache(name) 都落到 getMissingCache()，而那条路径只认
        // defaultCacheConfiguration，withInitialCacheConfigurations 里的配置被静默忽略。
        // 表现极具迷惑性：代码写着购物车 5 分钟，redis-cli 里 TTL 却是 1800。
        // 因此必须显式触发一次初始化，让 per-cache 配置真正落地。
        try {
            redisCacheManager.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("Redis 缓存初始化失败", e);
        }

        log.info("Redis 缓存已启用：keyPrefix={} ttlMinutes={} cartTtlMinutes={}",
                appProperties.getCache().getKeyPrefix(), appProperties.getCache().getTtlMinutes(),
                CART_TTL_MINUTES);
        // 再包一层熔断：Redis 不可用时直接跳过缓存，避免每个请求都等一轮超时
        CacheManager resilient = new ResilientCacheManager(redisCacheManager, cacheCircuitBreaker);

        // 最外层包「事务感知」：带 @Transactional 的写操作，缓存清理推迟到事务提交之后执行。
        //
        // 顺序是有意的 —— 外层负责「什么时候清」，内层负责「能不能清」：
        // 提交后真正落到 Redis 时才走熔断判断，判断依据是那一刻的 Redis 状态，而不是事务开始时的状态。
        //
        // 不开启时直接返回内层，退回「方法返回即清理」的旧行为（用于对照实验 / 应急回退）。
        if (!appProperties.getCache().isEvictAfterCommit()) {
            log.warn("缓存清理时机 = 事务提交前（bookstore.cache.evict-after-commit=false）。"
                    + "该形态仅用于对照实验：事务未提交便清空缓存，存在并发回填旧值的窗口");
            return resilient;
        }
        log.info("缓存清理时机 = 事务提交后（事务回滚则不清理）");
        return new TransactionAwareCacheManager(resilient);
    }

    /** 通用缓存配置（键用字符串、值用 JSON，便于直接用 redis-cli 排查） */
    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 统一前缀，便于在 redis-cli 里按业务清理（KEYS bookstore:*）
                .prefixCacheNameWith(appProperties.getCache().getKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer()));
    }

    /** JSON 序列化器：注册 Java 8 时间模块并开启类型信息，保证反序列化后类型正确 */
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * 缓存异常处理：只记录日志，绝不让缓存问题影响业务流程。
     *
     * <p>例如 Redis 未启动时，get 失败会当作「未命中」继续查库，put 失败静默丢弃。
     * 同时把失败上报给熔断器 —— 连续失败后由熔断器在「访问之前」拦掉缓存，
     * 这样故障期间请求不会每次都付出一次超时代价。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                cacheCircuitBreaker.recordFailure();
                log.warn("缓存读取失败，已降级为直接查库 cache={} key={} 原因={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                cacheCircuitBreaker.recordFailure();
                log.warn("缓存写入失败，本次结果不回填 cache={} key={} 原因={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                cacheCircuitBreaker.recordFailure();
                log.warn("缓存清理失败 cache={} key={} 原因={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                cacheCircuitBreaker.recordFailure();
                log.warn("缓存清空失败 cache={} 原因={}", cache.getName(), e.getMessage());
            }
        };
    }
}
