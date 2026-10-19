package cn.edu.bookstore.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存熔断器。
 *
 * <p>为什么需要它：实测发现 Redis 进程挂掉后，每个请求都要等「连接超时 + 读超时」
 * 叠加的约 4 秒才降级到数据库 —— 功能虽然没坏，但接口被拖成了秒级响应，
 * 比直接报错更危险（会把上游线程池占满，形成雪崩）。
 *
 * <p>策略：连续失败达到阈值即熔断一段时间，窗口期内所有缓存读写直接跳过（相当于全部未命中），
 * 请求立刻走数据库；窗口结束后放行一次探测请求，成功则自动恢复缓存。
 * 这是一个轻量版熔断器，语义与 Resilience4j 的 CircuitBreaker 一致，
 * 此处自行实现是为了不引入额外依赖。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
public class CacheCircuitBreaker {

    /** 连续失败多少次后熔断 */
    private static final int FAILURE_THRESHOLD = 3;

    /** 熔断持续时长（毫秒）：窗口内不再尝试访问 Redis */
    private static final long OPEN_MILLIS = 5000L;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** 熔断关闭时间点；0 表示未熔断 */
    private volatile long openUntil = 0L;

    /**
     * 当前是否允许访问缓存。
     *
     * <p>窗口刚结束时放行一次（半开状态）：若这次成功，计数清零、恢复正常；
     * 若失败，重新进入熔断窗口。
     */
    public boolean isAvailable() {
        long until = openUntil;
        if (until == 0L) {
            return true;
        }
        if (System.currentTimeMillis() >= until) {
            openUntil = 0L;
            return true;
        }
        return false;
    }

    /** 一次成功访问：清零失败计数并关闭熔断 */
    public void recordSuccess() {
        if (consecutiveFailures.get() != 0 || openUntil != 0L) {
            log.info("缓存已恢复可用，恢复正常读写");
        }
        consecutiveFailures.set(0);
        openUntil = 0L;
    }

    /** 一次失败访问：达到阈值即熔断 */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD && openUntil == 0L) {
            openUntil = System.currentTimeMillis() + OPEN_MILLIS;
            log.warn("缓存连续失败 {} 次，熔断 {} ms —— 期间所有缓存读写直接跳过，请求走数据库",
                    failures, OPEN_MILLIS);
        }
    }

    /** 当前是否处于熔断状态（供状态查询/测试使用） */
    public boolean isOpen() {
        return openUntil != 0L && System.currentTimeMillis() < openUntil;
    }
}
