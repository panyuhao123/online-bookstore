package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.CartAddRequest;
import cn.edu.bookstore.model.dto.CartMergeRequest;
import cn.edu.bookstore.model.dto.CartUpdateRequest;
import cn.edu.bookstore.model.vo.CartListVO;
import cn.edu.bookstore.model.vo.CartMergeResultVO;
import cn.edu.bookstore.service.CartService;
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
 * 购物车接口（全部需要登录）。
 *
 * <p>userId 从 Spring Security 认证后的登录上下文中取，不从请求参数接收，
 * 从根本上防止越权操作用户数据。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 合并未登录期间的本地购物车。
     *
     * <p>由 APP 在登录/注册成功后自动调用：把用户未登录时加进本地购物车的商品
     * 与服务端购物车合并，做到「先逛后登录，购物车不丢」。
     */
    @PostMapping("/merge")
    public Result<CartMergeResultVO> merge(@Valid @RequestBody CartMergeRequest request) {
        return Result.success(cartService.merge(LoginUserHolder.getUserId(), request));
    }

    /** 加入购物车 */
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody CartAddRequest request) {
        cartService.add(LoginUserHolder.getUserId(), request);
        return Result.success();
    }

    /** 购物车列表（含已勾选合计金额） */
    @GetMapping("/list")
    public Result<CartListVO> list() {
        return Result.success(cartService.list(LoginUserHolder.getUserId()));
    }

    /** 修改数量或勾选状态 */
    @PutMapping("/{cartId}")
    public Result<Void> update(@PathVariable Long cartId, @Valid @RequestBody CartUpdateRequest request) {
        cartService.update(LoginUserHolder.getUserId(), cartId, request);
        return Result.success();
    }

    /** 删除单个条目 */
    @DeleteMapping("/{cartId}")
    public Result<Void> remove(@PathVariable Long cartId) {
        cartService.remove(LoginUserHolder.getUserId(), cartId);
        return Result.success();
    }

    /** 批量删除 */
    @PostMapping("/removeBatch")
    public Result<Void> removeBatch(@RequestBody List<Long> cartIds) {
        cartService.removeBatch(LoginUserHolder.getUserId(), cartIds);
        return Result.success();
    }
}
