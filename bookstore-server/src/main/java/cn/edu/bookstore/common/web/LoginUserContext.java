package cn.edu.bookstore.common.web;

import lombok.Data;

/**
 * 当前登录用户上下文。
 *
 * <p>作为 Spring Security 的 Authentication principal 存放，
 * Service 层通过 {@link LoginUserHolder} 读取，使业务代码不依赖 HttpServletRequest，便于单元测试。
 *
 * @author 毕设项目组
 */
@Data
public class LoginUserContext {

    /** 角色标识：管理员 */
    private static final String ROLE_ADMIN = "admin";

    /** 用户 ID（APP 用户为 user.id，管理员为 admin.id） */
    private Long userId;

    /** 角色：user / admin */
    private String role;

    /** 展示名：昵称或管理员账号 */
    private String username;

    public static LoginUserContext of(Long userId, String role, String username) {
        LoginUserContext ctx = new LoginUserContext();
        ctx.setUserId(userId);
        ctx.setRole(role);
        ctx.setUsername(username);
        return ctx;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /**
     * Spring Security 权限标识。
     *
     * <p>统一映射为 ROLE_ 前缀（框架约定：hasRole('ADMIN') 比对的就是 ROLE_ADMIN），
     * 避免各处硬编码字符串导致授权规则失效。
     */
    public String authority() {
        return isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
    }
}
