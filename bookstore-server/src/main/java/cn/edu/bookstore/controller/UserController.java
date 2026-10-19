package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.LoginRequest;
import cn.edu.bookstore.model.dto.RegisterRequest;
import cn.edu.bookstore.model.vo.LoginVO;
import cn.edu.bookstore.model.vo.UserVO;
import cn.edu.bookstore.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：注册、登录、退出、获取当前用户。
 *
 * <p>Controller 只做参数接收与结果封装，业务规则全部在 UserService 中。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 用户注册（无需登录） */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /** 用户登录（无需登录） */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 退出登录。
     *
     * <p>JWT 无状态，服务端不保存令牌，因此退出由客户端删除本地令牌完成；
     * 本接口只做显式记录，保证 APP 端流程完整、便于论文中描述。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    /** 获取当前登录用户信息（需登录） */
    @GetMapping("/info")
    public Result<UserVO> info() {
        Long userId = LoginUserHolder.getUserId();
        return Result.success(userService.getCurrentUser(userId));
    }
}
