package cn.edu.bookstore.model.vo;

import lombok.Data;

/**
 * 登录成功返回结果：令牌 + 用户基本信息。
 *
 * @author 毕设项目组
 */
@Data
public class LoginVO {

    /** JWT 访问令牌，APP 后续请求放在 Authorization 头中 */
    private String token;

    /** 令牌类型，便于 APP 直接拼接 Authorization */
    private String tokenType = "Bearer";

    /** 令牌有效期（小时） */
    private int expireHours;

    /** 用户信息 */
    private UserVO user;
}
