package cn.edu.bookstore.common.util;

import cn.edu.bookstore.config.AppProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 工具类：负责令牌签发与校验。
 *
 * <p>载荷中只放 userId、role 与 jti（令牌唯一编号），不放任何敏感信息。
 * 令牌过期或签名不合法时返回 null，由上层转换为 401 提示，不暴露具体原因细节。
 *
 * <h3>为什么要有 jti</h3>
 * <p>JWT 是无状态令牌：服务端不保存会话，因此「退出登录」在客户端删掉令牌后，
 * 那枚令牌在它过期之前依然是合法凭证 —— 一旦被复制走（抓包、日志、共享设备），
 * 谁拿到都能继续用。加入 jti 后，服务端可以把「已登出的令牌编号」记入黑名单
 * （见 {@code TokenRevocationService}），让退出登录**真正在服务端生效**。
 *
 * <p>jti 使用随机 UUID（去掉横线共 32 位十六进制）：不是顺序号，
 * 无法从令牌编号反推用户数量或注册顺序，也不会因并发签发而冲突。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /** 载荷 key：用户/管理员 ID */
    public static final String CLAIM_USER_ID = "userId";

    /** 载荷 key：角色，取值 user / admin */
    public static final String CLAIM_ROLE = "role";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppProperties appProperties;

    /**
     * 签发访问令牌。
     *
     * <p>写入 jti 是为了让令牌可被单独吊销；有效期取
     * {@code bookstore.jwt.expire-hours}，生产环境通过配置收敛得更短。
     */
    public String generateToken(Long userId, String role) {
        Algorithm algorithm = Algorithm.HMAC256(appProperties.getJwt().getSecret());
        Date now = new Date();
        Date expireAt = new Date(now.getTime()
                + TimeUnit.HOURS.toMillis(appProperties.getJwt().getExpireHours()));
        return JWT.create()
                // withJWTId 写入标准 jti 声明，读取用 decode.getId()
                .withJWTId(newJti())
                .withClaim(CLAIM_USER_ID, userId)
                .withClaim(CLAIM_ROLE, role)
                .withIssuedAt(now)
                .withExpiresAt(expireAt)
                .sign(algorithm);
    }

    /** 生成令牌唯一编号：32 位十六进制随机串 */
    private String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验令牌，返回解码结果；令牌非法或过期返回 null。
     *
     * <p>除签名与有效期外，这里额外要求令牌**必须携带 exp 声明**：
     * 签名合法但没有 exp 的令牌永远不会过期，等于一把永久钥匙。
     * 正常情况下本项目签发的令牌都带 exp，这条判断是防止将来签发代码被改坏
     * 时留下一个「永久令牌」的口子（防御性校验，几乎没有额外开销）。
     */
    public DecodedJWT verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Algorithm algorithm = Algorithm.HMAC256(appProperties.getJwt().getSecret());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            if (jwt.getExpiresAt() == null) {
                log.warn("拒绝缺少 exp 声明的令牌：这类令牌不会过期，不应被接受");
                return null;
            }
            return jwt;
        } catch (TokenExpiredException e) {
            log.debug("令牌已过期");
            return null;
        } catch (JWTVerificationException e) {
            log.debug("令牌校验失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Authorization 头中提取令牌，兼容不带 Bearer 前缀的写法。
     *
     * <p>抽成静态方法是为了让「认证过滤器」与「退出登录接口」共用同一套解析规则：
     * 退出接口在安全白名单中（不经过认证上下文），需要自己取令牌，
     * 若两处各写一份解析逻辑，很容易出现「过滤器认、退出接口不认」的不一致。
     *
     * @return 令牌原文；请求头缺失或为空时返回 null
     */
    public static String resolveFromHeader(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
    }

    /** 从令牌中解析用户 ID，失败返回 null */
    public Long getUserId(String token) {
        DecodedJWT jwt = verify(token);
        return jwt == null ? null : getUserId(jwt);
    }

    /** 从已解码的令牌中取用户 ID（避免重复验签） */
    public Long getUserId(DecodedJWT jwt) {
        return jwt == null ? null : jwt.getClaim(CLAIM_USER_ID).asLong();
    }

    /** 从令牌中解析角色，失败返回 null */
    public String getRole(String token) {
        DecodedJWT jwt = verify(token);
        return jwt == null ? null : getRole(jwt);
    }

    /** 从已解码的令牌中取角色（避免重复验签） */
    public String getRole(DecodedJWT jwt) {
        return jwt == null ? null : jwt.getClaim(CLAIM_ROLE).asString();
    }

    /** 从已解码的令牌中取唯一编号 jti；无 jti 的历史令牌返回 null */
    public String getJti(DecodedJWT jwt) {
        return jwt == null ? null : jwt.getId();
    }
}
