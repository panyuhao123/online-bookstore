package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.dto.AdminUserQuery;
import cn.edu.bookstore.model.vo.AdminUserSummaryVO;
import cn.edu.bookstore.model.vo.AdminUserVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户接口（Web 后台：用户分页 / 启用禁用）。
 *
 * <p>后台不提供新增或修改用户资料：用户来自 APP 注册，后台只做账户状态管控。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页：GET /api/admin/user/page?keyword=&status=&pageNum=&pageSize= */
    @GetMapping("/page")
    public Result<PageVO<AdminUserVO>> page(@ModelAttribute AdminUserQuery query) {
        return Result.success(adminUserService.page(query));
    }

    /** 启用 / 禁用：PUT /api/admin/user/{id}/status?status=1 */
    @OpLog(module = "用户管理", action = "启用禁用用户")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.updateStatus(id, status);
        return Result.success();
    }

    /** 用户汇总指标：GET /api/admin/user/summary */
    @GetMapping("/summary")
    public Result<AdminUserSummaryVO> summary() {
        return Result.success(adminUserService.summary());
    }
}
