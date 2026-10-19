package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收货地址新增/编辑请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class AddressRequest {

    @NotBlank(message = "收件人不能为空")
    @Size(max = 20, message = "收件人姓名过长")
    private String receiver;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 20, message = "省份名称过长")
    private String province;

    @Size(max = 20, message = "城市名称过长")
    private String city;

    @Size(max = 20, message = "区县名称过长")
    private String district;

    /** 省份 adcode 编码（可选，由自动解析回填） */
    @Size(max = 12, message = "省份编码过长")
    private String provinceAdcode;

    /** 城市 adcode 编码（可选） */
    @Size(max = 12, message = "城市编码过长")
    private String cityAdcode;

    /** 区县 adcode 编码（可选） */
    @Size(max = 12, message = "区县编码过长")
    private String districtAdcode;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 120, message = "详细地址过长")
    private String detail;

    /** 是否设为默认地址：1-是 0-否 */
    private Integer isDefault = 0;
}
