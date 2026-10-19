package cn.edu.bookstore.common.util;

import cn.edu.bookstore.config.AppProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JWT 签发与校验测试。
 *
 * @author 毕设项目组
 */
@DisplayName("JWT 令牌")
class JwtUtilTest {

    /** 测试密钥：长度需 >= 32（与生产校验规则一致） */
    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdefghij";

    private AppProperties appProperties;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setSecret(SECRET);
        appProperties.getJwt().setExpireHours(24);
        jwtUtil = new JwtUtil(appProperties);
    }

    @Test
    @DisplayName("签发的令牌携带 jti，且是 32 位十六进制随机串")
    void tokenCarriesJti() {
        String token = jwtUtil.generateToken(1001L, "user");
        DecodedJWT jwt = jwtUtil.verify(token);

        assertNotNull(jwt, "刚签发的令牌应当校验通过");
        String jti = jwtUtil.getJti(jwt);
        assertNotNull(jti, "令牌必须携带 jti，否则无法被单独吊销");
        assertEquals(32, jti.length(), "jti 应为去掉横线的 32 位十六进制串");
        assertTrue(jti.matches("[0-9a-f]{32}"), "jti 应为十六进制字符，实际：" + jti);
    }

    @Test
    @DisplayName("同一用户连续两次登录拿到不同 jti（各自可独立吊销）")
    void jtiIsUniquePerToken() {
        DecodedJWT first = jwtUtil.verify(jwtUtil.generateToken(1001L, "user"));
        DecodedJWT second = jwtUtil.verify(jwtUtil.generateToken(1001L, "user"));

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.getId(), second.getId(),
                "多设备登录会各自持有一枚令牌，jti 相同会导致「一个设备登出，其他设备一起掉线」");
    }

    @Test
    @DisplayName("载荷可解出 userId 与 role")
    void claimsAreReadable() {
        DecodedJWT jwt = jwtUtil.verify(jwtUtil.generateToken(7L, "admin"));

        assertNotNull(jwt);
        assertEquals(7L, jwtUtil.getUserId(jwt));
        assertEquals("admin", jwtUtil.getRole(jwt));
    }

    @Test
    @DisplayName("令牌带过期时间，且约等于配置的有效期")
    void tokenHasExpiry() {
        DecodedJWT jwt = jwtUtil.verify(jwtUtil.generateToken(1L, "user"));

        assertNotNull(jwt);
        assertNotNull(jwt.getExpiresAt(), "令牌必须带 exp，否则永远不会过期");
        // 用范围断言而不是整除：签发到断言之间流逝的毫秒会让整除结果少 1 小时
        long remainMillis = jwt.getExpiresAt().getTime() - System.currentTimeMillis();
        long expected = 24L * 3_600_000L;
        assertTrue(remainMillis > expected - 60_000L && remainMillis <= expected,
                "有效期应约等于 bookstore.jwt.expire-hours（24 小时），实际剩余 " + remainMillis / 1000 + " 秒");
    }

    @Test
    @DisplayName("换密钥签发的令牌校验不通过（防伪造）")
    void tokenSignedByOtherSecretIsRejected() {
        AppProperties other = new AppProperties();
        other.getJwt().setSecret("another-secret-0123456789abcdefghijklmn");
        String forged = new JwtUtil(other).generateToken(1001L, "admin");

        assertNull(jwtUtil.verify(forged), "用别的密钥签发的令牌必须被拒绝");
    }

    @Test
    @DisplayName("缺少 exp 的令牌被拒绝（防止出现永久有效的钥匙）")
    void tokenWithoutExpiryIsRejected() {
        // 模拟「将来签发代码被改坏、漏掉 withExpiresAt」的情形
        String forever = JWT.create()
                .withJWTId("0123456789abcdef0123456789abcdef")
                .withClaim(JwtUtil.CLAIM_USER_ID, 1001L)
                .withClaim(JwtUtil.CLAIM_ROLE, "user")
                .sign(Algorithm.HMAC256(SECRET));

        assertNull(jwtUtil.verify(forever), "签名合法但没有 exp 的令牌不应被接受");
    }

    @Test
    @DisplayName("空令牌与垃圾串一律校验失败")
    void blankOrGarbageTokenIsRejected() {
        assertNull(jwtUtil.verify(null));
        assertNull(jwtUtil.verify(""));
        assertNull(jwtUtil.verify("   "));
        assertNull(jwtUtil.verify("not-a-jwt"));
        assertNull(jwtUtil.verify("a.b.c"));
    }

    @Test
    @DisplayName("能从请求头解析令牌，兼容有无 Bearer 前缀")
    void resolveTokenFromHeader() {
        assertEquals("abc", JwtUtil.resolveFromHeader(requestWith("Bearer abc")));
        assertEquals("abc", JwtUtil.resolveFromHeader(requestWith("abc")));
        assertNull(JwtUtil.resolveFromHeader(requestWith(null)));
        assertNull(JwtUtil.resolveFromHeader(requestWith("  ")));
        assertNull(JwtUtil.resolveFromHeader(null));
    }

    private HttpServletRequest requestWith(String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(authorization);
        return request;
    }
}
