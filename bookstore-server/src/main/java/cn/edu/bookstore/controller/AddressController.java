package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.AddressRequest;
import cn.edu.bookstore.model.dto.GeocodeRequest;
import cn.edu.bookstore.model.vo.AddressVO;
import cn.edu.bookstore.model.vo.GeocodeResult;
import cn.edu.bookstore.service.AddressService;
import cn.edu.bookstore.service.GeocodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（全部需要登录）。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final GeocodeService geocodeService;

    /** 地址自动解析（高德地理编码）：输入地址文本，返回候选省市区/adcode */
    @PostMapping("/geocode")
    public Result<GeocodeResult> geocode(@Valid @RequestBody GeocodeRequest request) {
        return Result.success(geocodeService.parse(LoginUserHolder.getUserId(), request.getAddress()));
    }

    /** 地址列表（默认地址在最前） */
    @GetMapping("/list")
    public Result<List<AddressVO>> list() {
        return Result.success(addressService.list(LoginUserHolder.getUserId()));
    }

    /** 新增地址 */
    @PostMapping("/add")
    public Result<AddressVO> add(@Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.add(LoginUserHolder.getUserId(), request));
    }

    /** 编辑地址 */
    @PutMapping("/{addressId}")
    public Result<AddressVO> update(@PathVariable Long addressId,
                                    @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.update(LoginUserHolder.getUserId(), addressId, request));
    }

    /** 删除地址 */
    @DeleteMapping("/{addressId}")
    public Result<Void> remove(@PathVariable Long addressId) {
        addressService.remove(LoginUserHolder.getUserId(), addressId);
        return Result.success();
    }

    /** 设为默认地址 */
    @PutMapping("/{addressId}/default")
    public Result<Void> setDefault(@PathVariable Long addressId) {
        addressService.setDefault(LoginUserHolder.getUserId(), addressId);
        return Result.success();
    }
}
