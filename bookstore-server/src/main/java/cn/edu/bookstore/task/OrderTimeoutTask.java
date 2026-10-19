package cn.edu.bookstore.task;

import cn.edu.bookstore.common.lock.RedisLock;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：自动取消超时未支付的订单。
 *
 * <p>解决的真实问题：用户下单后库存就被占住了（下单即扣库存）。
 * 如果用户一直不付款又不主动取消，这些库存会被无限期锁死，
 * 别的用户看到「没货」但实际货一直在。定时任务把这类订单清理掉并回补库存。
 *
 * <p>三个工程上的考量：
 * <ol>
 *     <li><b>复用而非重写</b>：取消动作直接复用用户取消订单的服务方法，
 *         库存回补、销量回退、缓存失效全部一致，不会出现两条路径行为不同的隐患；</li>
 *     <li><b>幂等</b>：取消用条件更新 {@code WHERE status = 待支付} 保证并发安全 ——
 *         定时任务与用户手动取消撞在一起时只有一方成功，库存不会被回补两次；</li>
 *     <li><b>多实例防重</b>：{@code @Scheduled} 是每个 JVM 各跑各的，
 *         用 Redis 锁保证同一时刻只有一个实例在执行（Redis 故障时放行，
 *         因为业务本身幂等，宁可白跑一轮也不能不跑）。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookstore.order.auto-cancel-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutTask {

    /** 分布式锁键：固定键名，所有实例抢同一把锁 */
    private static final String LOCK_KEY = "bookstore:lock:order-timeout-cancel";

    private final OrderService orderService;
    private final AppProperties appProperties;
    private final RedisLock redisLock;

    /**
     * 按配置的周期扫描并取消超时订单。
     *
     * <p>周期通过 {@code bookstore.order.auto-cancel-cron} 配置，默认每 5 分钟一次。
     * 答辩演示时若不希望等，可以改成每分钟，或直接调管理端的手动触发接口。
     */
    @Scheduled(cron = "${bookstore.order.auto-cancel-cron:0 */5 * * * ?}")
    public void cancelTimeoutOrders() {
        if (!redisLock.tryLock(LOCK_KEY)) {
            // 没抢到锁说明已有实例在执行，本轮直接跳过，等下一个周期
            return;
        }
        try {
            int timeoutMinutes = appProperties.getOrder().getTimeoutMinutes();
            int batchSize = appProperties.getOrder().getAutoCancelBatchSize();

            long start = System.currentTimeMillis();
            int cancelled = orderService.autoCancelTimeout(timeoutMinutes, batchSize);
            long cost = System.currentTimeMillis() - start;

            if (cancelled > 0) {
                log.info("超时订单自动取消完成：取消 {} 单（超时阈值 {} 分钟），耗时 {} ms",
                        cancelled, timeoutMinutes, cost);
            } else {
                log.debug("超时订单自动取消完成：无超时订单，耗时 {} ms", cost);
            }
        } catch (Exception e) {
            // 必须自己兜住异常：虽然 @Scheduled 不会因异常终止后续调度，
            // 但显式捕获才能给出「将在下个周期重试」这样可判断的日志，
            // 否则只剩一串堆栈，运维看不出任务是否还活着。
            log.error("超时订单自动取消执行失败，将在下一个周期重试", e);
        } finally {
            redisLock.unlock(LOCK_KEY);
        }
    }
}
