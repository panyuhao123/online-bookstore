package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统配置修改请求参数。
 *
 * <p>只能改值，不能改键：配置键是代码里按名字读取的，允许改名等于允许通过接口
 * 让某段业务逻辑取不到配置。同理也不提供新增接口 —— 键的集合由 V6 迁移与代码共同定义。
 *
 * @author 毕设项目组
 */
@Data
public class SysConfigUpdateRequest {

    @NotBlank(message = "配置值不能为空")
    @Size(max = 200, message = "配置值过长")
    private String configValue;
}
