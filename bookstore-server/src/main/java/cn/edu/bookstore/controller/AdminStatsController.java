package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.SalesTrendVO;
import cn.edu.bookstore.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端数据统计接口（后台看板图表）。
 *
 * <p>全部位于 /api/admin/**，由 Spring Security 强制校验管理员身份。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    /** 销售趋势：GET /api/admin/stats/sales-trend?days=7 */
    @GetMapping("/sales-trend")
    public Result<List<SalesTrendVO>> salesTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(adminStatsService.salesTrend(days));
    }

    /** 图书销量排行：GET /api/admin/stats/top-books?limit=10 */
    @GetMapping("/top-books")
    public Result<List<BookVO>> topBooks(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(adminStatsService.topSellingBooks(limit));
    }
}
