package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员登录请求参数（Web 管理后台）。
 *
 * @author 毕设项目组
 */
@Data
public class AdminLoginRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
