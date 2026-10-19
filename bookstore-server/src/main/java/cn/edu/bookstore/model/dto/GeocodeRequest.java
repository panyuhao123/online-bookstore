package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 地址自动解析请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class GeocodeRequest {

    /** 用户输入的完整地址文本，如「北京市朝阳区望京街道SOHO T1」 */
    @NotBlank(message = "地址文本不能为空")
    @Size(min = 3, max = 120, message = "地址长度需在 3~120 之间")
    private String address;
}
