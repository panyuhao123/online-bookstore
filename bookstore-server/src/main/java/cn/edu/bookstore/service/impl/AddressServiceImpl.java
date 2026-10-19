package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Address;
import cn.edu.bookstore.mapper.AddressMapper;
import cn.edu.bookstore.model.dto.AddressRequest;
import cn.edu.bookstore.model.vo.AddressVO;
import cn.edu.bookstore.service.AddressService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 收货地址业务实现。
 *
 * <p>关键规则：同一用户最多只有一个默认地址；
 * 所有写操作都校验地址归属，防止越权修改他人地址。
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    public List<AddressVO> list(Long userId) {
        List<Address> list = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreateTime));
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(AddressVO::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO add(Long userId, AddressRequest request) {
        // 用户的第一个地址自动成为默认地址
        long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId));
        int isDefault = (count == 0 || Integer.valueOf(1).equals(request.getIsDefault())) ? 1 : 0;
        if (isDefault == 1) {
            clearDefault(userId);
        }

        Address address = new Address();
        address.setUserId(userId);
        applyRequest(address, request);
        address.setIsDefault(isDefault);
        addressMapper.insert(address);
        return AddressVO.from(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(Long userId, Long addressId, AddressRequest request) {
        Address address = requireOwned(userId, addressId);
        if (Integer.valueOf(1).equals(request.getIsDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        applyRequest(address, request);
        addressMapper.updateById(address);
        return AddressVO.from(address);
    }

    @Override
    public void remove(Long userId, Long addressId) {
        requireOwned(userId, addressId);
        addressMapper.deleteById(addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        requireOwned(userId, addressId);
        clearDefault(userId);
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .set(Address::getIsDefault, 1)
                .eq(Address::getId, addressId)
                .eq(Address::getUserId, userId));
    }

    /** 把请求字段写入实体 */
    private void applyRequest(Address address, AddressRequest request) {
        address.setReceiver(request.getReceiver());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setProvinceAdcode(request.getProvinceAdcode());
        address.setCityAdcode(request.getCityAdcode());
        address.setDistrictAdcode(request.getDistrictAdcode());
        address.setDetail(request.getDetail());
    }

    /** 清除当前用户已有的默认地址标记 */
    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .set(Address::getIsDefault, 0)
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1));
    }

    /** 校验地址存在且属于当前用户 */
    private Address requireOwned(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收货地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人的收货地址");
        }
        return address;
    }
}
