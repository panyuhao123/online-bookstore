package cn.edu.bookstore.common.observability;

import cn.edu.bookstore.common.cache.CacheCircuitBreaker;
import cn.edu.bookstore.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存健康指示器测试。
 *
 * <p>本测试真正锁定的是一条<b>设计决策</b>：缓存熔断时健康状态仍必须是 UP。
 * 若哪天有人"顺手"把它改成 DOWN，负载均衡会把一个本来靠降级扛住的实例摘掉，
 * 从局部性能问题升级为真正的服务中断。断言写在这里，就是防止这种改动悄悄溜进来。
 *
 * @author 毕设项目组
 */
@DisplayName("缓存健康指示器")
class CacheHealthIndicatorTest {

    private AppProperties appProperties;
    private CacheCircuitBreaker circuitBreaker;
    private CacheHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        circuitBreaker = new CacheCircuitBreaker();
        indicator = new CacheHealthIndicator(circuitBreaker, appProperties);
    }

    @Test
    @DisplayName("正常情况：UP，熔断未开、未降级")
    void healthyWhenCircuitClosed() {
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("enabled"));
        assertEquals(false, health.getDetails().get("circuitOpen"));
        assertEquals(false, health.getDetails().get("degraded"));
    }

    @Test
    @DisplayName("熔断打开：仍然 UP，但 degraded=true（可观测、不摘实例）")
    void degradedButStillUpWhenCircuitOpen() {
        // 连续失败达到阈值（3 次）触发熔断
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.isOpen(), "前置条件：连续失败 3 次应触发熔断");

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus(),
                "缓存降级后业务仍可走数据库，判为 DOWN 会让负载均衡误摘健康实例");
        assertEquals(true, health.getDetails().get("circuitOpen"));
        assertEquals(true, health.getDetails().get("degraded"));
    }

    @Test
    @DisplayName("缓存整体关闭时不报降级（本来就没启用，谈不上降级）")
    void notDegradedWhenCacheDisabled() {
        appProperties.getCache().setEnabled(false);
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(false, health.getDetails().get("enabled"));
        assertEquals(false, health.getDetails().get("degraded"),
                "缓存未启用时不应被标成 degraded，否则告警会一直响");
        assertFalse(health.getDetails().isEmpty());
    }

    @Test
    @DisplayName("缓存恢复后 degraded 立刻回到 false（无需重启）")
    void recoversAfterSuccess() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertTrue((Boolean) indicator.health().getDetails().get("degraded"));

        circuitBreaker.recordSuccess();

        assertEquals(false, indicator.health().getDetails().get("degraded"));
    }
}
