package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.common.util.CsvUtil;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.model.dto.AdminOrderQuery;
import cn.edu.bookstore.model.dto.BatchShipRequest;
import cn.edu.bookstore.model.vo.AdminOrderVO;
import cn.edu.bookstore.model.vo.BatchShipResultVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminOrderService;
import cn.edu.bookstore.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理端订单接口（Web 后台：订单分页 / 详情 / 发货 / 批量发货 / 导出）。
 *
 * <p>全部位于 /api/admin/**，由 Spring Security 强制校验管理员身份，
 * APP 端令牌访问一律 403。
 *
 * @author 毕设项目组
 */
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final OrderService orderService;
    private final AppProperties appProperties;

    /** 订单分页：GET /api/admin/order/page?keyword=&status=&userId=&createTimeStart=&createTimeEnd=&pageNum=&pageSize= */
    @GetMapping("/page")
    public Result<PageVO<AdminOrderVO>> page(@ModelAttribute AdminOrderQuery query) {
        return Result.success(adminOrderService.page(query));
    }

    /** 订单详情（含明细） */
    @GetMapping("/{id}")
    public Result<AdminOrderVO> detail(@PathVariable Long id) {
        return Result.success(adminOrderService.detail(id));
    }

    /** 发货：POST /api/admin/order/{id}/ship */
    @OpLog(module = "订单管理", action = "订单发货")
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        adminOrderService.ship(id);
        return Result.success();
    }

    /**
     * 批量发货：POST /api/admin/order/batch-ship
     *
     * <p>部分成功不算失败：返回「发了几笔 + 哪几笔被跳过、为什么」，
     * 由运营自己决定是否处理剩余的单。整体回滚会把已经能发的单也一起退回，
     * 操作员只能逐单重试。
     */
    @OpLog(module = "订单管理", action = "批量发货")
    @PostMapping("/batch-ship")
    public Result<BatchShipResultVO> batchShip(@Valid @RequestBody BatchShipRequest request) {
        return Result.success(adminOrderService.batchShip(request.getIds()));
    }

    /**
     * 按当前筛选条件导出 CSV：GET /api/admin/order/export?keyword=&status=&createTimeStart=&createTimeEnd=
     *
     * <p><b>这是本项目唯一一个不返回统一返回体的接口</b>：导出要的就是一个文件流，
     * 把它包进 JSON 再让前端解码既没有意义，也没法被浏览器直接下载。
     * 因此这里直接写 HttpServletResponse，异常时回退成纯文本提示（与支付回调同一套思路）。
     *
     * <p>不走 @OpLog：切面在方法返回后记录操作日志，而本方法把响应体写成了文件流，
     * 记录参数虽无副作用，但日志详情里会出现一长串筛选条件，实际没有排查价值。
     */
    @GetMapping("/export")
    public void export(@ModelAttribute AdminOrderQuery query, HttpServletResponse response) {
        try {
            List<AdminOrderVO> rows = adminOrderService.exportList(query);
            String fileName = "orders_" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            // 同时给 filename 与 filename*：中文文件名在部分浏览器下只认后者
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

            StringBuilder csv = new StringBuilder(CsvUtil.BOM);
            csv.append(CsvUtil.line("订单号", "下单时间", "下单用户", "手机号", "订单金额",
                    "商品件数", "订单状态", "支付时间", "发货时间", "收货信息"));
            for (AdminOrderVO row : rows) {
                csv.append(CsvUtil.line(
                        row.getOrderNo(),
                        formatTime(row.getCreateTime()),
                        row.getUserNickname(),
                        row.getUserPhone(),
                        row.getPayableAmount(),
                        row.getTotalQuantity(),
                        row.getStatusText(),
                        formatTime(row.getPayTime()),
                        formatTime(row.getShipTime()),
                        row.getAddressSnapshot()));
            }
            response.getWriter().write(csv.toString());
            response.flushBuffer();
        } catch (Exception e) {
            log.warn("订单导出失败：{}", e.getMessage());
            try {
                response.reset();
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("导出失败：" + e.getMessage());
            } catch (Exception ignore) {
                // 响应已提交时无法再改写，只能放弃（避免二次异常覆盖原始错误）
            }
        }
    }

    /** 导出里的时间统一成「去 T 的可读格式」，空值留空而不是写 null */
    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 手动触发「超时订单自动取消」：POST /api/admin/order/cancel-timeout
     *
     * <p>与定时任务是同一个服务方法，只是把触发权交给管理员。
     * 这样答辩演示时不必真等 30 分钟，点一下就能看到超时订单被清理、库存被回补。
     *
     * @param timeoutMinutes 可选，覆盖配置里的超时阈值（分钟）。
     *                       传 0 表示「把所有未支付订单都视为超时」，用于一键清理；
     *                       不传则使用配置值（默认 30 分钟）。
     * @return 本次实际取消的订单数
     */
    @OpLog(module = "订单管理", action = "手动取消超时订单")
    @PostMapping("/cancel-timeout")
    public Result<Integer> cancelTimeout(
            @RequestParam(required = false) Integer timeoutMinutes) {
        int minutes = timeoutMinutes == null
                ? appProperties.getOrder().getTimeoutMinutes()
                : Math.clamp(timeoutMinutes, 0, 24 * 60);
        int cancelled = orderService.autoCancelTimeout(
                minutes, appProperties.getOrder().getAutoCancelBatchSize());
        return Result.success(cancelled);
    }
}
