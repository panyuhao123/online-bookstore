package cn.edu.bookstore.common.observability;

import cn.edu.bookstore.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 业务埋点测试。
 *
 * <p>用 {@link SimpleMeterRegistry}（Micrometer 自带的内存实现）承接计数器：
 * 既不需要起监控后端，也能真实走一遍「注册计数器 → 自增 → 查询」的完整链路。
 *
 * <p>测试重点：
 * <ul>
 *     <li>同一指标的不同标签组合要能各自独立计数（否则「按端区分成功率」就无从谈起）；</li>
 *     <li>开关关闭时不应注册任何计数器（应急关闭要真的干净）；</li>
 *     <li>标签值为 null 时必须静默跳过，不能把业务请求打挂。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@DisplayName("业务埋点")
class BusinessMetricsTest {

    private AppProperties appProperties;
    private MeterRegistry meterRegistry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        meterRegistry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(meterRegistry, appProperties);
    }

    /** 读取计数器值；未注册时返回 0（对应「从未发生」） */
    private double count(String name, String... tags) {
        io.micrometer.core.instrument.search.Search search = meterRegistry.find(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            search = search.tag(tags[i], tags[i + 1]);
        }
        Counter counter = search.counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    @DisplayName("登录埋点：按 scope 与 result 分别计数，互不干扰")
    void loginAttemptCountsByScopeAndResult() {
        metrics.loginAttempt("user", BusinessMetrics.RESULT_SUCCESS);
        metrics.loginAttempt("user", BusinessMetrics.RESULT_SUCCESS);
        metrics.loginAttempt("user", BusinessMetrics.RESULT_FAILURE);
        metrics.loginAttempt("admin", BusinessMetrics.RESULT_SUCCESS);

        assertEquals(2.0, count(BusinessMetrics.METRIC_LOGIN_ATTEMPT, "scope", "user", "result", "success"));
        assertEquals(1.0, count(BusinessMetrics.METRIC_LOGIN_ATTEMPT, "scope", "user", "result", "failure"));
        assertEquals(0.0, count(BusinessMetrics.METRIC_LOGIN_ATTEMPT, "scope", "user", "result", "locked"));
        assertEquals(1.0, count(BusinessMetrics.METRIC_LOGIN_ATTEMPT, "scope", "admin", "result", "success"));
        // 管理端不能与 APP 用户共用一条时间序列，否则「管理端是否被爆破」看不出来
        assertEquals(0.0, count(BusinessMetrics.METRIC_LOGIN_ATTEMPT, "scope", "admin", "result", "failure"));
    }

    @Test
    @DisplayName("令牌吊销埋点：按角色计数")
    void tokenRevokedCountsByRole() {
        metrics.tokenRevoked("user");
        metrics.tokenRevoked("admin");
        metrics.tokenRevoked("admin");

        assertEquals(1.0, count(BusinessMetrics.METRIC_TOKEN_REVOKED, "role", "user"));
        assertEquals(2.0, count(BusinessMetrics.METRIC_TOKEN_REVOKED, "role", "admin"));
    }

    @Test
    @DisplayName("订单埋点：下单与支付入账分别计数")
    void orderMetricsCounted() {
        metrics.orderCreated();
        metrics.orderCreated();
        metrics.orderPaid();

        assertEquals(2.0, count(BusinessMetrics.METRIC_ORDER_CREATED));
        assertEquals(1.0, count(BusinessMetrics.METRIC_ORDER_PAID));
    }

    @Test
    @DisplayName("开关关闭：不注册任何计数器（应急关闭要彻底）")
    void disabledMetricsRegisterNothing() {
        appProperties.getObservability().setMetricsEnabled(false);

        metrics.loginAttempt("user", BusinessMetrics.RESULT_SUCCESS);
        metrics.orderCreated();
        metrics.tokenRevoked("user");

        assertNull(meterRegistry.find(BusinessMetrics.METRIC_LOGIN_ATTEMPT).counter(),
                "关闭埋点后不应存在任何计数器");
        assertNull(meterRegistry.find(BusinessMetrics.METRIC_ORDER_CREATED).counter());
        assertNull(meterRegistry.find(BusinessMetrics.METRIC_TOKEN_REVOKED).counter());
    }

    @Test
    @DisplayName("null 标签被跳过的同时不抛异常（埋点绝不拖垮业务）")
    void nullTagIsIgnoredWithoutThrowing() {
        assertDoesNotThrow(() -> metrics.loginAttempt(null, BusinessMetrics.RESULT_SUCCESS));
        assertDoesNotThrow(() -> metrics.loginAttempt("user", null));
        assertDoesNotThrow(() -> metrics.tokenRevoked(null));

        assertNull(meterRegistry.find(BusinessMetrics.METRIC_LOGIN_ATTEMPT).counter());
        assertNull(meterRegistry.find(BusinessMetrics.METRIC_TOKEN_REVOKED).counter());
    }

    @Test
    @DisplayName("计数器实例被缓存复用，重复埋点不会重复注册 meter")
    void counterIsReusedAcrossCalls() {
        for (int i = 0; i < 100; i++) {
            metrics.loginAttempt("user", BusinessMetrics.RESULT_SUCCESS);
        }

        Counter counter = meterRegistry.find(BusinessMetrics.METRIC_LOGIN_ATTEMPT)
                .tag("scope", "user").tag("result", "success").counter();
        assertNotNull(counter);
        assertEquals(100.0, counter.count());
        // 100 次埋点只应产生 1 个 meter（而非 100 个），这正是缓存的意义
        assertEquals(1, meterRegistry.getMeters().size());
    }
}
