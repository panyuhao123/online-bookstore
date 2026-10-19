package cn.edu.bookstore.common.web;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 登录用户持有者。
 *
 * <p>实现说明：数据源是 Spring Security 的 {@link SecurityContextHolder}，而不是自己维护的 ThreadLocal。
 * 好处是登录态只有一份来源（框架负责请求结束后清理 ThreadLocal，不会出现漏清理导致的身份串号），
 * 同时保留静态方法入口，让 Service 层与 Controller 的既有代码零改动即可迁移。
 *
 * @author 毕设项目组
 */
public final class LoginUserHolder {

    private LoginUserHolder() {
    }

    /** 写入当前登录用户（JWT 过滤器与单元测试使用） */
    public static void set(LoginUserContext context) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                context, null, List.of(new SimpleGrantedAuthority(context.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 获取当前登录上下文，未登录返回 null */
    public static LoginUserContext get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUserContext context) {
            return context;
        }
        return null;
    }

    /** 获取当前登录用户 ID，未登录返回 null */
    public static Long getUserId() {
        LoginUserContext context = get();
        return context == null ? null : context.getUserId();
    }

    /** 判断是否已登录 */
    public static boolean isLogin() {
        return get() != null;
    }

    /** 判断当前登录者是否管理员 */
    public static boolean isAdmin() {
        LoginUserContext context = get();
        return context != null && context.isAdmin();
    }

    /** 清理当前线程数据，请求结束时调用 */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
