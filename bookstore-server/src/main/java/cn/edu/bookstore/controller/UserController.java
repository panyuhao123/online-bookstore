package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.LoginRequest;
import cn.edu.bookstore.model.dto.RegisterRequest;
import cn.edu.bookstore.model.vo.LoginVO;
import cn.edu.bookstore.model.vo.UserVO;
import cn.edu.bookstore.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
     * <p>本接口位于安全白名单（无需认证即可访问），语义是「让这枚令牌立即失效」，
     * 因此设计成<b>幂等</b>：令牌缺失、已过期或已经登出过，都返回成功 ——
     * 目标状态已经达成，不该反过来给用户报错。
     *
     * <p>因为不走认证上下文，这里从请求头自行解析令牌（与认证过滤器共用同一套解析规则），
     * 交给服务层写入吊销黑名单。客户端同时删除本地令牌，双重保障。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        userService.logout(JwtUtil.resolveFromHeader(request));
        return Result.success();
    }

    /** 获取当前登录用户信息（需登录） */
    @GetMapping("/info")
    public Result<UserVO> info() {
        Long userId = LoginUserHolder.getUserId();
        return Result.success(userService.getCurrentUser(userId));
    }
}
