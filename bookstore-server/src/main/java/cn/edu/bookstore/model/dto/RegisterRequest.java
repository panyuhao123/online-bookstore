package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class RegisterRequest {

    /** 手机号，注册后作为登录账号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6~20 位")
    private String password;

    /** 昵称，可为空，默认取「用户 + 手机号后 4 位」 */
    @Size(max = 20, message = "昵称不能超过 20 个字符")
    private String nickname;
}
