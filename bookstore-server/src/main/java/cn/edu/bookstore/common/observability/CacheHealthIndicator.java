package cn.edu.bookstore.common.observability;

import cn.edu.bookstore.common.cache.CacheCircuitBreaker;
import cn.edu.bookstore.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 缓存健康指示器：把「缓存当前是否可用」纳入 {@code /actuator/health} 的组件明细。
 *
 * <h3>一个刻意的设计取舍：熔断时仍然返回 UP</h3>
 * 缓存熔断（Redis 不可用）时，本系统会降级为「全部未命中，请求直接走数据库」，
 * 业务功能<b>依然正常</b>，只是变慢。此时若把健康状态判为 DOWN，
 * 负载均衡器会把实例摘掉、容器编排会重启 Pod —— 等于用一个「本来已经降级扛住」的
 * 局部故障，主动制造一次真正的服务中断。因此这里恒定返回 UP，
 * 把「是否降级」放进 details 里供人判断，而不让它参与整体健康聚合。
 *
 * <p>真正应当判 DOWN 的是「无法降级」的依赖（如数据库）：那才是实例确实不可用。
 * 这类依赖由 actuator 自带的 DataSource / Redis 健康指示器负责。
 *
 * @author 毕设项目组
 */
@Component("bookstoreCacheHealthIndicator")
@RequiredArgsConstructor
public class CacheHealthIndicator implements HealthIndicator {

    private final CacheCircuitBreaker cacheCircuitBreaker;
    private final AppProperties appProperties;

    @Override
    public Health health() {
        boolean enabled = appProperties.getCache().isEnabled();
        boolean circuitOpen = cacheCircuitBreaker.isOpen();
        return Health.up()
                .withDetail("enabled", enabled)
                .withDetail("circuitOpen", circuitOpen)
                // degraded=true 表示缓存当前不可用、请求正在走数据库。功能正常但性能下降，
                // 这是一个「需要关注但不需摘除实例」的状态。
                .withDetail("degraded", enabled && circuitOpen)
                .build();
    }
}
