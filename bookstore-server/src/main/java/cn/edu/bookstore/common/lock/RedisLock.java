package cn.edu.bookstore.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的轻量分布式锁，用于定时任务在多实例部署时「只让一个实例执行」。
 *
 * <p>为什么需要：Spring 的 {@code @Scheduled} 是**每个 JVM 各跑各的**。
 * 如果后端部署了两个实例，同一个定时任务会同时被两个实例触发。
 * 本项目虽然答辩时只跑单实例，但把这个问题显式处理掉，
 * 既避免将来扩容踩坑，也说明「知道定时任务的边界在哪」。
 *
 * <p>两个关键设计：
 * <ul>
 *     <li><b>加锁失败就直接跳过本轮</b>，不做重试排队 —— 定时任务下一轮还会来，
 *         抢不到锁说明别的实例正在干这件事，重复等待没有意义。</li>
 *     <li><b>Redis 不可用时「放行」而不是「阻塞」（fail-open）</b>：
 *         锁的作用是防止重复执行，而被保护的业务（取消超时订单）本身是幂等的
 *         （条件更新 + 状态校验），最坏结果是白跑一轮，不会造成数据错误。
 *         反过来，若 Redis 故障就让定时任务彻底不执行，超时订单会永远堆着 ——
 *         那是更严重的业务问题。这里的选择是「宁可白跑，不可不跑」。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 当前实例的唯一标识。
     *
     * <p>释放锁前要先比对值，确保删掉的是自己加的锁 ——
     * 否则可能出现「A 的锁已超时自动释放、B 拿到锁、此时 A 收尾把 B 的锁删了」。
     */
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * 尝试加锁。
     *
     * @param key 锁的键
     * @return true 表示拿到锁（可以执行）；Redis 不可用时同样返回 true（fail-open）
     */
    public boolean tryLock(String key) {
        return tryLock(key, DEFAULT_TTL);
    }

    /**
     * 尝试加锁并指定过期时间。
     *
     * <p>过期时间是**必须**的：如果持有者进程被 kill 而没有走到释放逻辑，
     * 没有 TTL 的锁会永久残留，定时任务就再也不执行了。
     */
    public boolean tryLock(String key, Duration ttl) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, instanceId, ttl);
            boolean acquired = Boolean.TRUE.equals(ok);
            if (!acquired) {
                log.debug("未取得分布式锁，跳过本轮 key={}", key);
            }
            return acquired;
        } catch (Exception e) {
            // fail-open：Redis 故障时放行，业务幂等可兜底，不能因锁不可用而让业务停摆
            log.warn("分布式锁不可用（Redis 异常），本轮放行继续执行 key={}：{}", key, e.getMessage());
            return true;
        }
    }

    /** 释放锁：只删自己加的锁 */
    public void unlock(String key) {
        try {
            String owner = stringRedisTemplate.opsForValue().get(key);
            if (instanceId.equals(owner)) {
                stringRedisTemplate.delete(key);
            }
        } catch (Exception e) {
            // 释放失败无需处理：锁有 TTL，到期会自动释放
            log.warn("释放分布式锁失败（将由 TTL 自动过期）key={}：{}", key, e.getMessage());
        }
    }
}
