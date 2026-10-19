package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * <p>参数在 Controller 边界处用 @Valid 校验，不信任客户端数据。
 *
 * @author 毕设项目组
 */
@Data
public class LoginRequest {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码明文 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6~20 位")
    private String password;

    /** 验证码唯一标识（前端先调 /api/captcha 拿到） */
    @NotBlank(message = "请先获取验证码")
    private String captchaKey;

    /** 用户填写的 4 位图形验证码 */
    @NotBlank(message = "请输入验证码")
    @Size(min = 4, max = 4, message = "验证码应为 4 位")
    private String captchaCode;
}
