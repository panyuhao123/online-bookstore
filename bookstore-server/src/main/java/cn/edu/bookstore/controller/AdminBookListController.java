package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.dto.AdminBookListQuery;
import cn.edu.bookstore.model.vo.AdminBookListDetailVO;
import cn.edu.bookstore.model.vo.AdminBookListVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminBookListService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端书单治理接口。
 *
 * <p>为什么这里只有「查 / 改状态 / 删」而没有「增 / 改内容」：
 * 书单是<b>用户的东西</b>，管理员不是代运营。平台需要的只有两件事——
 * 看到全部书单（含私密与已隐藏）、以及把违规的藏起来或清掉。
 * 给管理员开放任意编辑用户书单的能力，等于给了一个「以用户名义说话」的后门，
 * 一旦出现纠纷无法自证，所以故意不给。
 *
 * <p>写操作均标注 {@code @OpLog}：书单处置会直接影响用户，事后要能追到是谁、
 * 什么时候、以什么理由做的。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/booklist")
@RequiredArgsConstructor
public class AdminBookListController {

    private final AdminBookListService adminBookListService;

    /**
     * 书单分页（含私密与已隐藏）。
     *
     * <p>四个筛选条件都可选：keyword（名称/简介）、status（1 正常 / 0 已隐藏）、
     * userId（某个用户的全部书单）、isPublic（1 公开 / 0 仅自己可见）。
     * 不传 status 时正常与已隐藏都会返回——后台默认要看全量，否则「已隐藏的书单」
     * 永远找不到，恢复功能就形同虚设。
     */
    @GetMapping("/page")
    public Result<PageVO<AdminBookListVO>> page(AdminBookListQuery query) {
        return Result.success(adminBookListService.page(query));
    }

    /** 书单详情（含条目，已下架图书照常返回，便于判断内容是否违规） */
    @GetMapping("/{id}")
    public Result<AdminBookListDetailVO> detail(@PathVariable Long id) {
        return Result.success(adminBookListService.detail(id));
    }

    /**
     * 隐藏 / 恢复：PUT /api/admin/booklist/3/status?status=0
     *
     * <p>单独一个接口而不是让前端传整条记录：处置动作只有「藏」和「放」两种，
     * 整条回传容易顺带把用户的书单名、描述改坏。
     */
    @OpLog(module = "书单管理", action = "变更书单状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminBookListService.updateStatus(id, status);
        return Result.success();
    }

    /** 彻底删除书单（连同条目）；常规处置请优先用「隐藏」 */
    @OpLog(module = "书单管理", action = "删除书单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminBookListService.delete(id);
        return Result.success();
    }
}
