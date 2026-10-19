package cn.edu.bookstore.common.security;

import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.config.AppProperties;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 令牌吊销（退出登录在服务端真正生效）。
 *
 * <p>解决的问题：JWT 是<b>无状态</b>令牌，服务端不保存会话。因此「退出登录」原本只是
 * 客户端把令牌删掉，只要那枚令牌还在有效期内，谁拿着它都能继续调接口 ——
 * 换手机、共享设备、抓包、日志泄露，任何一种途径流出的令牌都会一直有效到自然过期。
 *
 * <p>做法：签发令牌时写入唯一编号 jti（见 {@code JwtUtil}），退出登录时把该 jti
 * 记入 Redis 黑名单；之后每个请求在认证过滤器里查一次黑名单，命中即拒绝。
 *
 * <h3>键设计（Redis）</h3>
 * <pre>
 *   bookstore:token:revoked:{jti}   值 = 吊销主体（如 user:1001），存在即已吊销
 * </pre>
 * 键的 TTL = 令牌剩余有效期。这一点很关键：黑名单只需覆盖「令牌仍然可能被使用」的那段时间，
 * 令牌自然过期后再留着黑名单毫无意义，只会让 Redis 无限膨胀。
 *
 * <h3>为什么不用「服务端保存会话」</h3>
 * <p>把每个登录会话写进 Redis 并逐请求校验，确实能实现即时吊销，但代价是：
 * 每次请求多一次存储往返、所有实例共享会话状态、Redis 故障时全站无法登录。
 * 本项目采用「黑名单 + 短期令牌」的折中 —— 存储里只留「已失效」的少量条目（正常情况几乎为空），
 * 常态下 Redis 挂掉也只是失去吊销能力，不影响正常访问。
 *
 * <h3>Redis 不可用时为什么是「放行」而不是「拒绝」</h3>
 * <p>黑名单属于<b>访问收紧</b>能力：Redis 故障时若一律拒绝，等于把所有在线用户直接踢下线，
 * 故障影响被放大成全站不可用。而放行的风险面只是「故障窗口内已登出的令牌仍可用」，
 * 且令牌本身仍有签名校验与有效期兜底。两害相权取其轻，因此选择降级放行 + 本地内存兜底 + 告警。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
public class TokenRevocationService {

    /** 键统一前缀，与 CacheConfig 的 key-prefix 保持一致 */
    private static final String REDIS_PREFIX = "bookstore:token:";
    /** 吊销标记键段 */
    private static final String REVOKED = "revoked:";

    /**
     * 降级后重新探测 Redis 的间隔（毫秒）。
     *
     * <p>与 {@code LoginAttemptGuard} 一次性停用不同，这里选择「定期重试」：
     * 登录失败计数降级到本地内存后功能仍完整；而黑名单降级期间，
     * 别的实例吊销的令牌在本实例仍可用 —— 这个不一致窗口越小越好，
     * 所以 Redis 恢复后最多 60 秒就要重新接上。
     */
    private static final long RETRY_INTERVAL_MS = 60_000L;

    /** 本地降级表最多保留的条目数，防止 Redis 长期不可用时内存无限增长 */
    private static final int LOCAL_MAX_ENTRIES = 10_000;

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final JwtUtil jwtUtil;

    /** Redis 可用性标记；置 false 后按 {@link #RETRY_INTERVAL_MS} 周期重试 */
    private volatile boolean redisAvailable = true;
    private volatile long lastFailureAt = 0L;

    /** 只告警一次，避免 Redis 故障期间每个请求都刷一条同样的日志 */
    private final AtomicBoolean degradedWarned = new AtomicBoolean(false);

    /** 降级用的本地吊销表（键 → 过期时刻 epoch millis） */
    private final Map<String, Long> localRevoked = new ConcurrentHashMap<>();

    public TokenRevocationService(StringRedisTemplate redisTemplate, AppProperties appProperties, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 便捷入口：解析令牌原文并吊销它（幂等）。
     *
     * <p>面向「退出登录」这一直接场景：调用方手里只有 Authorization 头里的令牌原文，
     * 不必自己验签、取 jti 与 exp。
     *
     * <p>令牌缺失、非法、已过期时静默返回 —— 登出的目标是让令牌不可用，
     * 本来就不可用时目标已达成，不该因此给前端报错（前端随后会清掉本地登录态）。
     *
     * @param rawToken 令牌原文
     * @param role     角色标识（user / admin），仅用于在黑名单里记录吊销主体
     */
    public void revokeToken(String rawToken, String role) {
        DecodedJWT jwt = jwtUtil.verify(rawToken);
        if (jwt == null) {
            log.debug("退出登录：令牌缺失或已失效，无需吊销 role={}", role);
            return;
        }
        revoke(jwt.getId(), jwt.getExpiresAt(), role + ":" + jwtUtil.getUserId(jwt));
    }

    /**
     * 吊销一枚令牌（幂等）。
     *
     * @param jti       令牌唯一编号（{@code JwtUtil#getJti} 取得）
     * @param expiresAt 令牌自身的过期时间；已过期的令牌无需入黑名单
     * @param actor     吊销主体，形如 {@code user:1001} / {@code admin:1}，仅用于排查
     */
    public void revoke(String jti, Date expiresAt, String actor) {
        if (!appProperties.getJwt().getRevocation().isEnabled()) {
            log.warn("令牌吊销功能已关闭（bookstore.jwt.revocation.enabled=false），"
                    + "本次退出登录仅在客户端删除令牌，服务端仍认可该令牌直到过期");
            return;
        }
        if (jti == null || jti.isBlank()) {
            // 历史版本签发的、不含 jti 的令牌：无法精确吊销，只能等它自然过期
            log.warn("令牌缺少 jti，无法吊销，请等待其自然过期。actor={}", actor);
            return;
        }
        long ttlSeconds = remainingSeconds(expiresAt);
        if (ttlSeconds <= 0) {
            log.debug("令牌已过期，无需吊销 jti={}", jti);
            return;
        }

        String key = key(jti);
        String value = (actor == null || actor.isBlank()) ? "1" : actor;

        if (shouldTryRedis()) {
            try {
                redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
                log.info("令牌已吊销 jti={} actor={} 剩余有效期={}秒", jti, value, ttlSeconds);
                return;
            } catch (Exception e) {
                markRedisFailure(e);
            }
        }

        // ---- 本地降级：至少保证本实例立刻失效 ----
        evictExpiredLocal();
        localRevoked.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
        log.warn("Redis 不可用，令牌吊销已降级为「仅本实例生效」，其他实例上的该令牌仍有效。"
                + "actor={} jti={}", value, jti);
    }

    /**
     * 判断令牌是否已被吊销。
     *
     * <p>由认证过滤器在每次请求时调用。功能关闭、jti 缺失、Redis 故障时一律返回 false
     * （即放行），理由见类注释。
     */
    public boolean isRevoked(String jti) {
        if (!appProperties.getJwt().getRevocation().isEnabled()) {
            return false;
        }
        if (jti == null || jti.isBlank()) {
            return false;
        }
        String key = key(jti);

        if (shouldTryRedis()) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(key));
            } catch (Exception e) {
                markRedisFailure(e);
            }
        }

        Long expireAt = localRevoked.get(key);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            // 惰性清理：键的过期语义与 Redis TTL 保持一致
            localRevoked.remove(key);
            return false;
        }
        return true;
    }

    /** 当前进程内降级表的大小，供健康检查/测试观察 */
    public int localRevokedSize() {
        return localRevoked.size();
    }

    // ========================== 内部实现 ==========================

    /** 令牌剩余有效秒数；已过期或无法解析返回 0 */
    private long remainingSeconds(Date expiresAt) {
        if (expiresAt == null) {
            return 0L;
        }
        long remainMillis = expiresAt.getTime() - System.currentTimeMillis();
        return remainMillis <= 0 ? 0L : remainMillis / 1000L;
    }

    /**
     * 标记 Redis 故障。
     *
     * <p>首次故障打 warn（含原因），之后静默 —— 故障期间每个请求都会撞上这里，
     * 不加限制会把日志刷满，反过来掩盖真正的问题。
     */
    private void markRedisFailure(Exception e) {
        redisAvailable = false;
        lastFailureAt = System.currentTimeMillis();
        if (degradedWarned.compareAndSet(false, true)) {
            log.warn("Redis 不可用，令牌吊销暂时降级（已登出的令牌可能仍被接受）。"
                    + "将在 {} 秒后自动重试连接。原因：{}", RETRY_INTERVAL_MS / 1000, e.getMessage());
        } else {
            log.debug("Redis 仍不可用：{}", e.getMessage());
        }
    }

    /**
     * 是否应当尝试访问 Redis：正常时返回 true；故障后每隔 {@link #RETRY_INTERVAL_MS}
     * 放行一次以探测恢复情况。
     */
    private boolean shouldTryRedis() {
        if (redisAvailable) {
            return true;
        }
        if (System.currentTimeMillis() - lastFailureAt > RETRY_INTERVAL_MS) {
            redisAvailable = true;
            return true;
        }
        return false;
    }

    /** 拼装 Redis 键：{@code bookstore:token:revoked:<jti>} */
    private String key(String jti) {
        return REDIS_PREFIX + REVOKED + jti;
    }

    /** 清理本地降级表中的过期条目；条目过多时整体清空，避免内存无限增长 */
    private void evictExpiredLocal() {
        if (localRevoked.size() <= LOCAL_MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        localRevoked.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (localRevoked.size() > LOCAL_MAX_ENTRIES) {
            log.warn("本地吊销表超过 {} 条且无过期项可清，已整体清空（Redis 故障期间请尽快恢复）",
                    LOCAL_MAX_ENTRIES);
            localRevoked.clear();
        }
    }
}
