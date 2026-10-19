package cn.edu.bookstore.common.web;

import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.entity.Admin;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.AdminMapper;
import cn.edu.bookstore.mapper.UserMapper;
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

/**
 * JWT 认证过滤器（Spring Security 过滤器链中的一个环节）。
 *
 * <p>职责：把 Authorization 头里的令牌「翻译」成 Spring Security 的 Authentication，
 * 后续的授权判断（hasRole('ADMIN')、authenticated()）与 @PreAuthorize 全部交给框架。
 *
 * <p>三种情况：
 * <ol>
 *     <li>没有令牌 → 不设置认证，作为匿名请求继续向下走，由授权规则决定是否需要登录；</li>
 *     <li>令牌有效且账号正常 → 写入 SecurityContext（principal 为 LoginUserContext）；</li>
 *     <li>令牌有效但账号被禁用/已删除 → 直接返回 401，比统一文案更精确，避免用户困惑。</li>
 * </ol>
 *
 * <p>与旧的拦截器实现相比，这里不再手工判断路径白名单与角色 ——
 * 那是授权规则的职责，集中在 SecurityConfig，避免两处逻辑各写一遍导致不一致。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 管理员角色标识（JWT 载荷与 admin 表共用） */
    private static final String ROLE_ADMIN = "admin";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final RestAuthExceptionHandler restAuthExceptionHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtUtil.getUserId(token);
            String role = jwtUtil.getRole(token);

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

        filterChain.doFilter(request, response);
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

    /** 从 Authorization 头中提取令牌，兼容不带 Bearer 前缀的写法 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
    }
}
