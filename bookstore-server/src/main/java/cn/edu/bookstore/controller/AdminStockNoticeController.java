package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeSummaryVO;
import cn.edu.bookstore.service.AdminStockNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端到货提醒看板接口。
 *
 * <p>展示口径是「按图书聚合的等待人数」而不是原始订阅流水：
 * 运营打开这个页面想回答的是「先补哪本书」，而这个问题只有聚合后才能回答。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/stock-notice")
@RequiredArgsConstructor
public class AdminStockNoticeController {

    private final AdminStockNoticeService adminStockNoticeService;

    /**
     * 缺货图书等待人数分页（按待通知人数倒序）。
     *
     * <p>示例：GET /api/admin/stock-notice/summary?keyword=算法&onlyWaiting=true&pageNum=1&pageSize=10
     *
     * @param onlyWaiting 为空时按 true 处理（默认只看还有用户等待的图书，这才是补货清单）
     */
    @GetMapping("/summary")
    public Result<PageVO<StockNoticeSummaryVO>> summary(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Boolean onlyWaiting,
                                                        @RequestParam(required = false) Long pageNum,
                                                        @RequestParam(required = false) Long pageSize) {
        return Result.success(adminStockNoticeService.summary(keyword, onlyWaiting, pageNum, pageSize));
    }

    /**
     * 把某本书的待通知订阅全部标记为「已通知」：PUT /api/admin/stock-notice/12/notify
     *
     * <p>返回 {@code notified} 条数，让后台能立刻回显「本次通知了 N 位读者」。
     * 幂等：重复点击第二次返回 0，不会产生异常（补货通常伴随多次操作，
     * 运营手滑点两下不应该看到报错）。
     */
    @OpLog(module = "到货提醒", action = "标记已通知")
    @PutMapping("/{bookId}/notify")
    public Result<Map<String, Integer>> markNotified(@PathVariable Long bookId) {
        return Result.success(Map.of("notified", adminStockNoticeService.markNotified(bookId)));
    }
}
