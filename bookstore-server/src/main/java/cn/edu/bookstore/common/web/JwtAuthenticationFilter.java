package cn.edu.bookstore.common.web;

import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.security.TokenRevocationService;
import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.entity.Admin;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.AdminMapper;
import cn.edu.bookstore.mapper.UserMapper;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * JWT 认证过滤器（Spring Security 过滤器链中的一个环节）。
 *
 * <p>职责：把 Authorization 头里的令牌「翻译」成 Spring Security 的 Authentication，
 * 后续的授权判断（hasRole('ADMIN')、authenticated()）与 @PreAuthorize 全部交给框架。
 *
 * <p>四种情况：
 * <ol>
 *     <li>没有令牌 → 不设置认证，作为匿名请求继续向下走，由授权规则决定是否需要登录；</li>
 *     <li>令牌已登出（jti 命中吊销黑名单）→ 直接返回 401，
 *         这是「退出登录真正生效」的落点：客户端删掉令牌只是卫生，服务端不认才是安全。
 *         唯一的例外是登出接口本身（见 {@link #LOGOUT_PATHS}），它必须能被失效令牌调用；</li>
 *     <li>令牌有效且账号正常 → 写入 SecurityContext（principal 为 LoginUserContext）；</li>
 *     <li>令牌有效但账号被禁用/已删除 → 直接返回 401，比统一文案更精确，避免用户困惑。</li>
 * </ol>
 *
 * <p>与旧的拦截器实现相比，这里不再手工判断路径白名单与角色 ——
 * 那是授权规则的职责，集中在 SecurityConfig，避免两处逻辑各写一遍导致不一致。
 *
 * <p>性能说明：每个携带令牌的请求会做一次 Redis 查询（查吊销黑名单）。
 * 这是有意的代价 —— 只保留「无状态令牌」就没有服务端吊销能力，
 * 而一次本地 Redis 查询通常不到 1ms；若 Redis 不可用，
 * {@link TokenRevocationService} 会降级放行而不是把请求全部拒绝。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 管理员角色标识（JWT 载荷与 admin 表共用） */
    private static final String ROLE_ADMIN = "admin";

    /**
     * 登出路径：这些接口不参与吊销校验，交给接口自己处理。
     *
     * <p>为什么需要这份例外清单：登出接口的职责是「让这枚令牌失效」，
     * 因此它必须允许被<b>已经失效的令牌</b>调用 —— 否则用户重复点两次退出登录，
     * 第二次就会被过滤器拦成 401，与「登出幂等、总是返回成功」的语义矛盾。
     *
     * <p>注意这里不是授权规则（授权规则仍集中在 SecurityConfig 的 PUBLIC_PATHS），
     * 而是「吊销校验的适用范围」：失效令牌不许访问业务接口，但允许把登出这件事做完。
     */
    private static final Set<String> LOGOUT_PATHS = Set.of("/api/user/logout", "/api/admin/logout");

    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;
    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final RestAuthExceptionHandler restAuthExceptionHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = JwtUtil.resolveFromHeader(request);
        boolean logoutRequest = isLogoutRequest(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 只验签一次：userId / role / jti 都从同一个解码结果里取，
            // 避免原先「getUserId 一次、getRole 再一次」的重复验签
            DecodedJWT jwt = jwtUtil.verify(token);

            if (jwt != null && !logoutRequest && tokenRevocationService.isRevoked(jwt.getId())) {
                log.info("拒绝已登出的令牌 jti={} path={}", jwt.getId(), request.getServletPath());
                restAuthExceptionHandler.write(response, ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
                return;
            }

            if (jwt != null) {
                Long userId = jwtUtil.getUserId(jwt);
                String role = jwtUtil.getRole(jwt);

                if (userId != null && role != null) {
                    LoginUserContext context = loadContext(userId, role);
                    if (context == null) {
                        // 令牌本身合法但账号已不可用：提示「账号状态异常」而不是「未登录」，
                        // 否则用户会反复重新登录却始终失败
                        restAuthExceptionHandler.write(response, ErrorCode.UNAUTHORIZED, "账号状态异常，请重新登录");
                        return;
                    }
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            context, null, List.of(new SimpleGrantedAuthority(context.authority())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 当前请求是否为登出接口 */
    private boolean isLogoutRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            // 某些容器/转发场景下 servletPath 为空，退回完整 URI（本项目 context-path 为空，两者一致）
            path = request.getRequestURI();
        }
        return path != null && LOGOUT_PATHS.contains(path);
    }

    /**
     * 按角色查对应的账号表并校验状态。
     *
     * <p>管理员与 APP 用户分表存放，必须按角色分别查询：
     * 若拿 admin.id 去查 user 表，管理员会被误判为「账号不存在」而恒返回 401。
     *
     * @return 账号可用时返回上下文，账号不存在或被禁用返回 null
     */
    private LoginUserContext loadContext(Long userId, String role) {
        if (ROLE_ADMIN.equals(role)) {
            Admin admin = adminMapper.selectById(userId);
            if (admin == null || Integer.valueOf(0).equals(admin.getStatus())) {
                return null;
            }
            return LoginUserContext.of(admin.getId(), role, admin.getRealName());
        }
        User user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            return null;
        }
        return LoginUserContext.of(user.getId(), role, user.getNickname());
    }
}
