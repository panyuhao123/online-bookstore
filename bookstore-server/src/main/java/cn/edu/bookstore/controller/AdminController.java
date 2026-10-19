package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.AdminLoginRequest;
import cn.edu.bookstore.model.vo.AdminLoginVO;
import cn.edu.bookstore.model.vo.AdminVO;
import cn.edu.bookstore.model.vo.OverviewVO;
import cn.edu.bookstore.service.AdminDashboardService;
import cn.edu.bookstore.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员接口（Web 管理后台）。
 *
 * <p>登录接口在 Spring Security 白名单中；其余 /api/admin/** 接口
 * 由拦截器强制要求 role = admin，普通用户令牌访问返回 403。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminDashboardService adminDashboardService;

    /** 管理员登录 */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminService.login(request));
    }

    @PreAuthorize("hasRole('ADMIN')") // 方法级授权：即使路径规则被误改，后台接口也不会被越权访问
    /** 当前管理员信息：后台刷新后用于恢复登录态、校验令牌是否仍有效 */
    @GetMapping("/info")
    public Result<AdminVO> info() {
        return Result.success(adminService.getInfo(LoginUserHolder.getUserId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    /** 首页数据总览：卡片用的图书 / 用户 / 订单 / 销售额统计 */
    @GetMapping("/dashboard")
    public Result<OverviewVO> dashboard() {
        return Result.success(adminDashboardService.overview());
    }
}
