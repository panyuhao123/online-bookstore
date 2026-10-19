package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AddressRequest;
import cn.edu.bookstore.model.vo.AddressVO;

import java.util.List;

/**
 * 收货地址业务接口（全部需要登录）。
 *
 * @author 毕设项目组
 */
public interface AddressService {

    /** 地址列表，默认地址排在最前 */
    List<AddressVO> list(Long userId);

    /** 新增地址，返回新地址 */
    AddressVO add(Long userId, AddressRequest request);

    /** 编辑地址（校验归属） */
    AddressVO update(Long userId, Long addressId, AddressRequest request);

    /** 删除地址（校验归属） */
    void remove(Long userId, Long addressId);

    /** 设为默认地址（同一用户只保留一个默认地址） */
    void setDefault(Long userId, Long addressId);
}
