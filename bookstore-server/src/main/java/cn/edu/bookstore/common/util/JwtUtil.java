package cn.edu.bookstore.common.util;

import cn.edu.bookstore.config.AppProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT 工具类：负责令牌签发与校验。
 *
 * <p>载荷中只放 userId 与 role（user / admin），不放敏感信息。
 * 令牌过期或签名不合法时返回 null，由上层转换为 401 提示，不暴露具体原因细节。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /** 载荷 key：用户/管理员 ID */
    private static final String CLAIM_USER_ID = "userId";

    /** 载荷 key：角色，取值 user / admin */
    private static final String CLAIM_ROLE = "role";

    private final AppProperties appProperties;

    /** 签发访问令牌 */
    public String generateToken(Long userId, String role) {
        Algorithm algorithm = Algorithm.HMAC256(appProperties.getJwt().getSecret());
        Date expireAt = new Date(System.currentTimeMillis()
                + TimeUnit.HOURS.toMillis(appProperties.getJwt().getExpireHours()));
        return JWT.create()
                .withClaim(CLAIM_USER_ID, userId)
                .withClaim(CLAIM_ROLE, role)
                .withIssuedAt(new Date())
                .withExpiresAt(expireAt)
                .sign(algorithm);
    }

    /**
     * 校验令牌，返回解码结果；令牌非法或过期返回 null。
     */
    public DecodedJWT verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Algorithm algorithm = Algorithm.HMAC256(appProperties.getJwt().getSecret());
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(token);
        } catch (TokenExpiredException e) {
            log.debug("令牌已过期");
            return null;
        } catch (JWTVerificationException e) {
            log.debug("令牌校验失败：{}", e.getMessage());
            return null;
        }
    }

    /** 从令牌中解析用户 ID，失败返回 null */
    public Long getUserId(String token) {
        DecodedJWT jwt = verify(token);
        return jwt == null ? null : jwt.getClaim(CLAIM_USER_ID).asLong();
    }

    /** 从令牌中解析角色，失败返回 null */
    public String getRole(String token) {
        DecodedJWT jwt = verify(token);
        return jwt == null ? null : jwt.getClaim(CLAIM_ROLE).asString();
    }
}
