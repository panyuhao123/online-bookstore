package cn.edu.bookstore.common.security;

import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.config.AppProperties;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 令牌吊销（jti 黑名单）测试。
 *
 * <p>测试重点不在「有没有调 Redis」，而在几条容易被忽略的边界语义：
 * <ul>
 *     <li>黑名单必须带 TTL，否则 Redis 只增不减；</li>
 *     <li>已过期 / 无 jti 的令牌不该写入黑名单（写了也没意义，只是垃圾）；</li>
 *     <li>Redis 故障时降级为「本实例生效」而不是直接抛错把用户的登出操作弄失败；</li>
 *     <li>开关关闭时整个机制静默失效（应急出口必须真的管用）。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("令牌吊销")
class TokenRevocationServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdefghij";
    private static final String JTI = "0123456789abcdef0123456789abcdef";

    /** 一枚「1 小时后过期」的令牌过期时间，用于 TTL 断言 */
    private static final long ONE_HOUR_MS = 3_600_000L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AppProperties appProperties;
    private JwtUtil jwtUtil;
    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setSecret(SECRET);
        appProperties.getJwt().setExpireHours(24);
        jwtUtil = new JwtUtil(appProperties);
        service = new TokenRevocationService(redisTemplate, appProperties, jwtUtil);
    }

    /** 让 Redis 走「正常可用」分支 */
    private void redisWorks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("吊销后该 jti 立即被判定为已失效，键的 TTL 等于令牌剩余有效期")
    void revokedJtiIsDetectedWithMatchingTtl() {
        redisWorks();
        Date expiresAt = new Date(System.currentTimeMillis() + ONE_HOUR_MS);

        service.revoke(JTI, expiresAt, "user:1001");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("bookstore:token:revoked:" + JTI), eq("user:1001"), ttl.capture());
        long seconds = ttl.getValue().getSeconds();
        assertTrue(seconds > ONE_HOUR_MS / 1000 - 5 && seconds <= ONE_HOUR_MS / 1000,
                "黑名单 TTL 必须收敛到令牌剩余有效期，避免 Redis 只增不减；实际 " + seconds + " 秒");

        when(redisTemplate.hasKey("bookstore:token:revoked:" + JTI)).thenReturn(true);
        assertTrue(service.isRevoked(JTI));
    }

    @Test
    @DisplayName("未吊销的 jti 返回 false（并确实查了一次 Redis）")
    void unknownJtiIsNotRevoked() {
        assertFalse(service.isRevoked(JTI));
        // 未 stub hasKey 时 Mockito 返回 null，走 Boolean.TRUE.equals 的假值分支
        verify(redisTemplate).hasKey("bookstore:token:revoked:" + JTI);
    }

    @Test
    @DisplayName("已过期的令牌不写入黑名单（留着也永远不会被查到）")
    void expiredTokenIsNotBlacklisted() {
        Date expired = new Date(System.currentTimeMillis() - 1_000L);

        service.revoke(JTI, expired, "user:1001");

        verifyNoInteractions(redisTemplate);
        assertEquals(0, service.localRevokedSize());
    }

    @Test
    @DisplayName("缺少 jti 的历史令牌不写黑名单也不报错")
    void tokenWithoutJtiIsIgnored() {
        service.revoke(null, new Date(System.currentTimeMillis() + ONE_HOUR_MS), "user:1001");
        service.revoke("  ", new Date(System.currentTimeMillis() + ONE_HOUR_MS), "user:1001");

        verifyNoInteractions(redisTemplate);
        assertEquals(0, service.localRevokedSize());
    }

    @Test
    @DisplayName("Redis 故障时降级为本地内存而不是抛错，本实例上令牌仍然失效")
    void redisFailureDegradesToLocalMemory() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        Date expiresAt = new Date(System.currentTimeMillis() + ONE_HOUR_MS);

        // 登出接口不该因为运维故障而报错：用户点「退出登录」应当照常成功
        assertDoesNotThrow(() -> service.revoke(JTI, expiresAt, "user:1001"));

        assertTrue(service.isRevoked(JTI), "Redis 挂掉后至少要保住本实例的吊销效果");
        assertEquals(1, service.localRevokedSize());
        assertFalse(service.isRevoked("ffffffffffffffffffffffffffffffff"),
                "降级表只能影响被吊销的那一枚令牌，不能连坐其他令牌");
    }

    @Test
    @DisplayName("降级写入的本地条目到期后自动不再视为已吊销")
    void localEntryExpires() throws InterruptedException {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        // 令牌只剩约 1 秒有效期，本地条目的过期时刻也应与之对应
        Date expiresAt = new Date(System.currentTimeMillis() + 1_900L);

        service.revoke(JTI, expiresAt, "user:1001");
        assertTrue(service.isRevoked(JTI));

        Thread.sleep(1_200L);
        assertFalse(service.isRevoked(JTI), "本地条目必须跟随令牌有效期过期，否则内存会越攒越多");
    }

    @Test
    @DisplayName("开关关闭时既不写黑名单也不做校验（应急出口）")
    void disabledRevocationIsNoop() {
        appProperties.getJwt().getRevocation().setEnabled(false);
        redisWorks();

        service.revoke(JTI, new Date(System.currentTimeMillis() + ONE_HOUR_MS), "user:1001");

        verifyNoInteractions(valueOperations);
        assertFalse(service.isRevoked(JTI), "开关关闭后 isRevoked 必须恒为 false，不能用查库失败把用户挡住");
    }

    @Test
    @DisplayName("revokeToken：解析真实令牌后按 jti 吊销，并把吊销主体记进黑名单")
    void revokeTokenResolvesRealToken() {
        redisWorks();
        String token = jwtUtil.generateToken(1001L, "user");
        DecodedJWT jwt = jwtUtil.verify(token);
        assertNotNull(jwt);

        service.revokeToken(token, "user");

        verify(valueOperations).set(eq("bookstore:token:revoked:" + jwt.getId()),
                eq("user:1001"), any(Duration.class));
    }

    @Test
    @DisplayName("revokeToken：令牌缺失或非法时静默返回（登出幂等）")
    void revokeTokenToleratesBadInput() {
        assertDoesNotThrow(() -> service.revokeToken(null, "user"));
        assertDoesNotThrow(() -> service.revokeToken("", "user"));
        assertDoesNotThrow(() -> service.revokeToken("not-a-jwt", "admin"));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("吊销是幂等的：重复登出不抛错，键值保持一致")
    void revokeIsIdempotent() {
        redisWorks();
        Date expiresAt = new Date(System.currentTimeMillis() + ONE_HOUR_MS);

        assertDoesNotThrow(() -> {
            service.revoke(JTI, expiresAt, "user:1001");
            service.revoke(JTI, expiresAt, "user:1001");
        });

        verify(valueOperations, times(2))
                .set(eq("bookstore:token:revoked:" + JTI), eq("user:1001"), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("吊销主体缺失时用占位值，键本身依然有效")
    void nullActorStillRevokes() {
        redisWorks();

        service.revoke(JTI, new Date(System.currentTimeMillis() + ONE_HOUR_MS), null);

        verify(valueOperations).set(eq("bookstore:token:revoked:" + JTI), eq("1"), any(Duration.class));
    }
}
