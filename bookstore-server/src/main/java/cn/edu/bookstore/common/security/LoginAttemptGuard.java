package cn.edu.bookstore.common.security;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败锁定（防在线暴力破解）。
 *
 * <p>解决的问题：APP 用户登录已有图形验证码，但验证码只能挡住「无脑脚本」。
 * 攻击者完全可以脚本化地「先取验证码 → OCR 识别 → 猜一个密码」，或者人工慢速尝试。
 * 从验证码视角看这些请求全部「合规」，因此必须再加一层**按账号维度的失败计数**：
 * 连续失败达到阈值就把该账号临时锁住，把「无限次尝试」压成「每 15 分钟 5 次」，
 * 使口令穷举在时间维度上不可行。管理端登录没有验证码，这层是它唯一的在线防护。
 *
 * <h3>键设计（Redis）</h3>
 * <pre>
 *   bookstore:login:fail:{scope}:{identity}   失败计数（固定窗口，窗口内累加）
 *   bookstore:login:lock:{scope}:{identity}   锁定标记（存在即锁定中，TTL 即剩余锁定时间）
 * </pre>
 * {@code scope} 取 {@code user} / {@code admin}，避免 APP 用户与管理员的同名账号互相影响。
 *
 * <h3>三个刻意的设计取舍</h3>
 * <ol>
 *     <li><b>只按账号维度计数，不按 IP。</b> 公司/校园网 / 运营商 NAT 后面常有一大批用户共用出口 IP，
 *         按 IP 限流会把「同事练手输错密码」变成「整栋楼都登不上」。账号维度误伤面最小。
 *         将来若要做 IP 维度，应作为**辅助**维度并放宽阈值（见 README 的扩展说明）。</li>
 *     <li><b>不要求账号真实存在。</b> 不存在的账号同样计数，否则「不存在的账号永远不锁」
 *         就成了账号枚举的信号。</li>
 *     <li><b>Redis 不可用时降级为本地内存计数，而不是直接放行。</b>
 *         放行等于攻击者只要把 Redis 打死就能无限试密码；本地内存虽在重启后清零、
 *         多实例部署时各算各的，但「有防护」始终强于「没防护」。</li>
 * </ol>
 *
 * <p>凭据以明文形式作为键的一部分（手机号 / 管理员账号名）——这是有意为之：
 * 排查线上问题时能直接看出是哪个账号被锁，比哈希后「完全不可读」更有价值；
 * 键只存在于内网 Redis 中，不属于对外暴露面。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /** 键统一前缀，与 CacheConfig 的 key-prefix 保持一致 */
    private static final String REDIS_PREFIX = "bookstore:login:";
    /** 失败计数键段 */
    private static final String FAIL = "fail:";
    /** 锁定标记键段 */
    private static final String LOCK = "lock:";

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    /**
     * Redis 可用性开关：一旦失败过就不再重试，避免每次请求都付出连接超时代价。
     *
     * <p>刻意用实例字段而非 static：单例 Bean 下效果相同，但单元测试里可以每个测试
     * 用独立实例，互不污染。
     */
    private volatile boolean redisAvailable = true;

    /** 降级用的本地计数（键 → 失败次数） */
    private final Map<String, Integer> localFail = new ConcurrentHashMap<>();
    /** 降级用的本地计数过期时刻（键 → epoch millis） */
    private final Map<String, Long> localFailExpire = new ConcurrentHashMap<>();
    /** 降级用的本地锁定过期时刻（键 → epoch millis） */
    private final Map<String, Long> localLockExpire = new ConcurrentHashMap<>();

    public LoginAttemptGuard(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    /**
     * 校验该账号当前是否处于锁定状态；锁定中则直接抛业务异常（422）。
     *
     * <p>调用时机应尽量靠前（APP 端放在验证码校验之后、查库之前），
     * 这样被锁期间连数据库都不会被碰。
     *
     * <p><b>为什么用 422 而不是 401：</b>两端前端都把 401 当作「令牌失效」处理 ——
     * APP 会清空本地令牌并跳登录页，管理端会清 token + 整页重定向到登录页。
     * 用 401 会导致「攻击者试错 → 把自己的登录页刷出来」，而且正常用户被锁后再登录
     * 也看不出原因。422（业务规则不满足）不在前端那套「令牌失效」分支里，只弹提示，
     * 既保留了登录态，也能把「已被临时锁定，请 X 分钟后重试」原样展示给用户。
     *
     * @param scope    账号域：user-APP 用户 / admin-管理员
     * @param identity 账号标识：手机号或管理员账号名
     */
    public void checkNotLocked(String scope, String identity) {
        AppProperties.LoginGuard cfg = appProperties.getLoginGuard();
        if (!cfg.isEnabled() || identity == null || identity.isBlank()) {
            return;
        }
        long remainingSeconds = remainingLockSeconds(scope, identity);
        if (remainingSeconds > 0) {
            // 向上取整：剩余 30 秒也告诉用户「1 分钟」，避免显示「0 分钟」让人以为没锁
            long minutes = Math.max(1, (remainingSeconds + 59) / 60);
            log.warn("账号处于登录锁定中，拒绝本次尝试 scope={} identity={} 剩余秒数={}",
                    scope, identity, remainingSeconds);
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "登录失败次数过多，账号已被临时锁定，请 " + minutes + " 分钟后重试");
        }
    }

    /**
     * 记录一次登录失败；达到阈值时写入锁定标记。
     *
     * <p>计数采用**固定窗口**：键首次创建时设置窗口 TTL，窗口内不再续期。
     * 若每次失败都续期（滑动窗口），攻击者只要把节奏控制在窗口内就永远触发不了锁定。
     *
     * @return true 表示本次失败刚好触发了锁定（调用方可据此打更醒目的日志）
     */
    public boolean recordFailure(String scope, String identity) {
        AppProperties.LoginGuard cfg = appProperties.getLoginGuard();
        if (!cfg.isEnabled() || identity == null || identity.isBlank()) {
            return false;
        }
        int maxFailures = cfg.getMaxFailures();
        long count = increaseFailCount(scope, identity, cfg.getWindowMinutes());

        if (count >= maxFailures) {
            lock(scope, identity, cfg.getLockMinutes());
            // 锁定后清掉计数：解锁后从 0 重新开始，而不是「刚解锁就被下一次失败重新锁上」
            clearFailCount(scope, identity);
            log.warn("账号连续登录失败 {} 次，已锁定 {} 分钟 scope={} identity={}",
                    count, cfg.getLockMinutes(), scope, identity);
            return true;
        }

        log.info("登录失败计数 scope={} identity={} 次数={}/{}", scope, identity, count, maxFailures);
        return false;
    }

    /**
     * 重置该账号的失败计数与锁定标记。
     *
     * <p>两个使用场景：登录成功时清零（避免历史失败次数「赊账」到下次登录）；
     * 管理员确认是自己的账号被误锁时手动解锁。
     */
    public void reset(String scope, String identity) {
        if (identity == null || identity.isBlank()) {
            return;
        }
        String failKey = key(scope, identity, FAIL);
        String lockKey = key(scope, identity, LOCK);
        localFail.remove(failKey);
        localFailExpire.remove(failKey);
        localLockExpire.remove(lockKey);
        if (redisAvailable) {
            try {
                redisTemplate.delete(failKey);
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，登录失败计数改由本地内存维护。原因：{}", e.getMessage());
            }
        }
    }

    // ========================== 内部实现 ==========================

    /**
     * 拼装 Redis 键。
     *
     * <p>顺序是 {@code 用途:账号域:标识}（如 {@code bookstore:login:fail:user:13800138000}），
     * 而不是 {@code 账号域:用途:标识} —— 把「用途」放在靠前的位置，
     * 可以用 {@code bookstore:login:fail:*} 一条通配就清掉所有失败计数，
     * 排查线上问题时也更符合「先看是什么、再看是谁」的习惯。
     */
    private String key(String scope, String identity, String suffix) {
        return REDIS_PREFIX + suffix + scope + ":" + identity;
    }

    /** 剩余锁定秒数；未锁定返回 0 */
    private long remainingLockSeconds(String scope, String identity) {
        String lockKey = key(scope, identity, LOCK);
        if (redisAvailable) {
            try {
                Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
                if (ttl == null || ttl <= 0) {
                    // -2 键不存在 / -1 键存在但无 TTL。后者理论上不会出现（写入时必带 TTL），
                    // 真出现说明有人手工写过这个键，按未锁定处理并把键清掉，避免被永久锁死。
                    if (ttl != null && ttl == -1L) {
                        redisTemplate.delete(lockKey);
                    }
                    return 0;
                }
                return ttl;
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，登录失败计数改由本地内存维护。原因：{}", e.getMessage());
            }
        }
        Long expireAt = localLockExpire.get(lockKey);
        if (expireAt == null) {
            return 0;
        }
        long remainMillis = expireAt - System.currentTimeMillis();
        if (remainMillis <= 0) {
            localLockExpire.remove(lockKey);
            return 0;
        }
        return remainMillis / 1000;
    }

    /** 失败计数 +1，返回累加后的值 */
    private long increaseFailCount(String scope, String identity, int windowMinutes) {
        String failKey = key(scope, identity, FAIL);
        if (redisAvailable) {
            try {
                Long count = redisTemplate.opsForValue().increment(failKey);
                if (count != null && count == 1L) {
                    // 只在键刚创建时设置窗口，之后不再续期（固定窗口语义）
                    redisTemplate.expire(failKey, Duration.ofMinutes(windowMinutes));
                }
                return count == null ? 1L : count;
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，登录失败计数改由本地内存维护。原因：{}", e.getMessage());
            }
        }
        // ---- 本地降级实现 ----
        long now = System.currentTimeMillis();
        Long expireAt = localFailExpire.get(failKey);
        if (expireAt == null || now > expireAt) {
            localFail.put(failKey, 1);
            localFailExpire.put(failKey, now + windowMinutes * 60_000L);
            return 1L;
        }
        return localFail.merge(failKey, 1, Integer::sum);
    }

    private void clearFailCount(String scope, String identity) {
        String failKey = key(scope, identity, FAIL);
        localFail.remove(failKey);
        localFailExpire.remove(failKey);
        if (redisAvailable) {
            try {
                redisTemplate.delete(failKey);
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，登录失败计数改由本地内存维护。原因：{}", e.getMessage());
            }
        }
    }

    private void lock(String scope, String identity, int lockMinutes) {
        String lockKey = key(scope, identity, LOCK);
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(lockMinutes));
                return;
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，登录失败计数改由本地内存维护。原因：{}", e.getMessage());
            }
        }
        localLockExpire.put(lockKey, System.currentTimeMillis() + lockMinutes * 60_000L);
    }
}
