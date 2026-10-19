package cn.edu.bookstore.common.observability;

import cn.edu.bookstore.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 业务埋点：把关键业务事件注册为 Micrometer 计数器，供 {@code /actuator/metrics} 查询。
 *
 * <h3>为什么只做「计数器」</h3>
 * 可观测性分三层：日志（发生了什么）、指标（发生得多少/多快）、链路追踪（一次请求经历了什么）。
 * 本项目已用日志记录细节，本类补上最实用的第二层 —— 计数器。选择计数器而非自定义 Gauge 的原因：
 * <ul>
 *     <li>计数器是单调递增的，服务重启后从 0 开始，语义清晰，不存在「取值时机不同结果不同」的歧义；</li>
 *     <li>「失败率」「峰值 QPS」这类真正有用的指标，都能由计数器在监控侧用 rate() 算出来；</li>
 *     <li>接口耗时不需要自己埋 —— 引入 actuator 后 Spring MVC 会自动产出
 *         {@code http.server.requests} 指标（含 URI、状态码、P95 等），重复造轮子没有意义。</li>
 * </ul>
 *
 * <h3>埋点原则</h3>
 * <ol>
 *     <li><b>绝不因为埋点失败而影响业务。</b> 计数器取值只做内存自增，这里仍对整个过程做了兜底，
 *         即便监控组件异常也不让业务链路中断；</li>
 *     <li><b>不把用户隐私写进标签。</b> 标签只放 {@code scope}（user/admin）、{@code result}（success/failure/locked）
 *         这类低基数枚举值。手机号、订单号是<b>高基数</b>标签，一旦写入会让指标维度爆炸，
 *         每个用户各成一条时间序列，监控系统很快就会被撑爆；</li>
 *     <li><b>计数器按「名称 + 标签组合」缓存。</b> Micrometer 的 {@code registry.counter(name, tags)}
 *         每次都按标签查找，直接调用也无妨，但缓存后能省掉重复的标签排序与查找开销，
 *         让埋点在高频调用路径上更接近「一次 Map 查询」。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
public class BusinessMetrics {

    /** 登录尝试（标签 scope=user|admin，result=success|failure|locked） */
    public static final String METRIC_LOGIN_ATTEMPT = "bookstore.login.attempt";
    /** 令牌吊销（标签 role=user|admin） */
    public static final String METRIC_TOKEN_REVOKED = "bookstore.token.revoked";
    /** 订单创建成功 */
    public static final String METRIC_ORDER_CREATED = "bookstore.order.created";
    /** 订单支付入账成功（含模拟支付与支付回调） */
    public static final String METRIC_ORDER_PAID = "bookstore.order.paid";

    /** 登录成功 */
    public static final String RESULT_SUCCESS = "success";
    /** 登录失败（账号或口令错误） */
    public static final String RESULT_FAILURE = "failure";
    /** 因账号处于锁定状态而直接拒绝 */
    public static final String RESULT_LOCKED = "locked";

    private static final String TAG_SCOPE = "scope";
    private static final String TAG_RESULT = "result";
    private static final String TAG_ROLE = "role";

    private final MeterRegistry meterRegistry;
    private final AppProperties appProperties;

    /**
     * 计数器缓存，键为「名称 + 标签键值对拼串」。
     *
     * <p>用 ConcurrentMap 而非普通 Map：埋点会在多个请求线程中并发触发。
     * 注意这里缓存的是 Counter 实例，Micrometer 内部对同名同标签的计数器本身就是唯一的，
     * 缓存只是省去查找开销，不会产生重复计数。
     */
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry meterRegistry, AppProperties appProperties) {
        this.meterRegistry = meterRegistry;
        this.appProperties = appProperties;
    }

    /**
     * 记录一次登录尝试。
     *
     * @param scope  账号域：{@code user} / {@code admin}
     * @param result {@link #RESULT_SUCCESS} / {@link #RESULT_FAILURE} / {@link #RESULT_LOCKED}
     */
    public void loginAttempt(String scope, String result) {
        increment(METRIC_LOGIN_ATTEMPT, TAG_SCOPE, scope, TAG_RESULT, result);
    }

    /**
     * 记录一次令牌吊销（退出登录在服务端生效）。
     *
     * @param role 令牌所属角色：{@code user} / {@code admin}
     */
    public void tokenRevoked(String role) {
        increment(METRIC_TOKEN_REVOKED, TAG_ROLE, role);
    }

    /** 记录一次订单创建成功 */
    public void orderCreated() {
        increment(METRIC_ORDER_CREATED);
    }

    /** 记录一次订单支付入账成功 */
    public void orderPaid() {
        increment(METRIC_ORDER_PAID);
    }

    // ========================== 内部实现 ==========================

    /**
     * 计数器 +1。
     *
     * <p>标签值出现 null 时直接跳过（Micrometer 会对 null 标签值抛异常），
     * 避免「一个没传全参数的埋点把业务请求打挂」。整段逻辑包在 try-catch 中，
     * 埋点属于旁路能力，任何异常都不应向上传播。
     */
    private void increment(String name, String... tags) {
        if (!appProperties.getObservability().isMetricsEnabled()) {
            return;
        }
        for (String tag : tags) {
            if (tag == null) {
                return;
            }
        }
        try {
            Counter counter = counters.computeIfAbsent(cacheKey(name, tags),
                    key -> Counter.builder(name).tags(tags).register(meterRegistry));
            counter.increment();
        } catch (Exception e) {
            // 只在 debug 级别记录：埋点异常重复发生时，用 warn 会把日志刷爆
            log.debug("业务埋点记录失败 name={} 原因={}", name, e.getMessage());
        }
    }

    /** 生成缓存键；标签按传入顺序拼接，调用方需保证同名指标的标签顺序一致 */
    private String cacheKey(String name, String[] tags) {
        StringBuilder sb = new StringBuilder(name);
        for (String tag : tags) {
            sb.append('|').append(tag);
        }
        return sb.toString();
    }
}
