package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.model.dto.AdminOrderQuery;
import cn.edu.bookstore.model.vo.AdminOrderVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminOrderService;
import cn.edu.bookstore.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单接口（Web 后台：订单分页 / 详情 / 发货）。
 *
 * <p>全部位于 /api/admin/**，由 Spring Security 强制校验管理员身份，
 * APP 端令牌访问一律 403。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final OrderService orderService;
    private final AppProperties appProperties;

    /** 订单分页：GET /api/admin/order/page?orderNo=&status=&userId=&pageNum=&pageSize= */
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
