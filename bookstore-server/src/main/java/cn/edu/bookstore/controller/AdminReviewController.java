package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.dto.AdminReviewQuery;
import cn.edu.bookstore.model.vo.AdminReviewSummaryVO;
import cn.edu.bookstore.model.vo.AdminReviewVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端评价管理接口。
 *
 * <p>全部位于 /api/admin/**，由 Spring Security 强制校验管理员身份，
 * 并额外用 @PreAuthorize 做方法级双保险。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    /** 评价分页：GET /api/admin/review/page?bookId=&rating=&status=&keyword=&pageNum=&pageSize= */
    @GetMapping("/page")
    public Result<PageVO<AdminReviewVO>> page(@ModelAttribute AdminReviewQuery query) {
        return Result.success(adminReviewService.page(query));
    }

    /** 评价汇总指标：GET /api/admin/review/summary */
    @GetMapping("/summary")
    public Result<AdminReviewSummaryVO> summary() {
        return Result.success(adminReviewService.summary());
    }

    /**
     * 显示 / 隐藏评价：PUT /api/admin/review/5/status?status=0
     *
     * <p>优先用「隐藏」而不是「删除」处理违规评价：隐藏后立即从平均分与前台列表消失，
     * 但数据仍在，事后可追溯、可申诉、可恢复。
     */
    @PutMapping("/{id}/status")
    @OpLog(module = "评价管理", action = "变更评价状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminReviewService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 彻底删除评价：DELETE /api/admin/review/5
     *
     * <p>仅用于垃圾数据清理 —— 日常违规处理请走「隐藏」。
     */
    @DeleteMapping("/{id}")
    @OpLog(module = "评价管理", action = "删除评价")
    public Result<Void> delete(@PathVariable Long id) {
        adminReviewService.delete(id);
        return Result.success();
    }
}
