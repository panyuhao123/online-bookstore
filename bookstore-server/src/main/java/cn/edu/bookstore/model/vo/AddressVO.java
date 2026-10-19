package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Address;
import lombok.Data;

/**
 * 收货地址视图对象。
 *
 * @author 毕设项目组
 */
@Data
public class AddressVO {

    private Long id;

    private String receiver;

    private String phone;

    private String province;
    private String city;
    private String district;

    /** 省份 adcode 编码 */
    private String provinceAdcode;
    /** 城市 adcode 编码 */
    private String cityAdcode;
    /** 区县 adcode 编码 */
    private String districtAdcode;

    private String detail;

    /** 是否默认地址：1-是 0-否 */
    private Integer isDefault;

    /** 省市区拼好的完整地址，APP 端可直接展示 */
    private String fullAddress;

    public static AddressVO from(Address address) {
        if (address == null) {
            return null;
        }
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiver(address.getReceiver());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setProvinceAdcode(address.getProvinceAdcode());
        vo.setCityAdcode(address.getCityAdcode());
        vo.setDistrictAdcode(address.getDistrictAdcode());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        vo.setFullAddress(buildFull(address));
        return vo;
    }

    private static String buildFull(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.getProvince() != null) {
            sb.append(address.getProvince());
        }
        if (address.getCity() != null) {
            sb.append(address.getCity());
        }
        if (address.getDistrict() != null) {
            sb.append(address.getDistrict());
        }
        sb.append(address.getDetail());
        return sb.toString();
    }
}
