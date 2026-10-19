package cn.edu.bookstore.model.vo;

import lombok.Data;

/**
 * 管理员登录返回结果：令牌 + 管理员基本信息。
 *
 * <p>与 APP 端 {@link LoginVO} 结构一致（token / tokenType / expireHours），
 * 便于前后端统一处理令牌过期逻辑。
 *
 * @author 毕设项目组
 */
@Data
public class AdminLoginVO {

    /** JWT 访问令牌，后台后续请求放在 Authorization 头中 */
    private String token;

    private String tokenType = "Bearer";

    /** 令牌有效期（小时） */
    private int expireHours;

    /** 管理员信息 */
    private AdminVO admin;
}
