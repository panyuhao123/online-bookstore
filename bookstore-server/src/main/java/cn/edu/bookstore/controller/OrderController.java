package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.OrderCreateRequest;
import cn.edu.bookstore.model.dto.OrderSettleRequest;
import cn.edu.bookstore.model.vo.FreightRuleVO;
import cn.edu.bookstore.model.vo.OrderSettleVO;
import cn.edu.bookstore.model.vo.OrderVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.PrepayVO;
import cn.edu.bookstore.service.FreightService;
import cn.edu.bookstore.service.OrderService;
import cn.edu.bookstore.service.PayService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * 订单接口（全部需要登录）。
 *
 * @author 毕设项目组
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PayService payService;
    private final FreightService freightService;

    /**
     * 运费规则（匿名可访问）。
     *
     * <p>放在订单前缀下是因为它服务于结算流程，但它的读权限与图书详情同级：
     * 商品详情页要在「加入购物车」按钮旁渲染「再买 ¥X 包邮」，
     * 那一刻用户还没有登录，接口若要求令牌，这句引导文案就只能写死在前端，
     * 而写死的门槛一旦和后台配置不一致，就会出现「页面说满 99 包邮、结算时却收运费」。
     */
    @GetMapping("/freight-rule")
    public Result<FreightRuleVO> freightRule() {
        return Result.success(freightService.currentRule());
    }

    /**
     * 结算金额预览（需要登录）。
     *
     * <p>与 {@code /create} 的区别是<b>只算不落库</b>：不校验地址、不扣库存、不写订单。
     * 结算页在用户选地址之前就要显示金额，因此不能用 create 的结果来预览 ——
     * 那需要先造一个地址，且每次改数量都会产生一笔真实订单。
     *
     * <p>金额一律由服务端算：运费规则是可配置的，前后端各算一遍迟早不一致。
     */
    @PostMapping("/settle-preview")
    public Result<OrderSettleVO> settlePreview(@RequestBody(required = false) OrderSettleRequest request) {
        return Result.success(orderService.settlePreview(LoginUserHolder.getUserId(), request));
    }

    /** 提交订单（模拟下单） */
    @PostMapping("/create")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orderService.create(LoginUserHolder.getUserId(), request));
    }

    /**
     * 发起支付。
     *
     * <p>请求走支付抽象层，返回收银台信息而不是空结果，以便支持真实支付渠道：
     * <ul>
     *     <li>模拟支付渠道（默认）：当场完成支付，返回 {@code paid = true}；</li>
     *     <li>支付宝渠道：返回 {@code payUrl} 与 {@code paid = false}，用户跳转收银台付款，
     *         支付结果由支付宝异步通知回写。</li>
     * </ul>
     */
    @PostMapping("/{orderId}/pay")
    public Result<PrepayVO> pay(@PathVariable Long orderId) {
        return Result.success(payService.prepay(LoginUserHolder.getUserId(), orderId));
    }

    /**
     * 支付渠道异步通知入口（支付宝回调此地址）。
     *
     * <p>这个接口和项目里其他接口都不一样，有三点刻意的设计：
     * <ul>
     *     <li><b>匿名可访问</b>：调用方是支付平台服务器，不可能带用户令牌，
     *         因此加入 Spring Security 白名单；安全性由<b>验签</b>保证，而不是由登录态保证；</li>
     *     <li><b>返回纯文本而不是统一 JSON</b>：支付宝约定收到 "success" 才算确认，
     *         返回 JSON 会被判定为处理失败并不断重推，因此这是项目里唯一
     *         不遵循「统一返回结构」约定的接口 —— 它面向的是机器协议，不是前端；</li>
     *     <li><b>直接写 HttpServletResponse，不走 content negotiation</b>：
     *         最初这里写的是 {@code @PostMapping(produces = "text/plain")} + 返回 String，
     *         实测有两个坑：① 调用方若带 {@code Accept: application/json}，
     *         请求在进入方法前就会因媒体类型不匹配抛 406，被兜底成 500；
     *         ② 抛异常时异常处理器要把 {@code Result} 序列化成 text/plain，
     *         没有任何转换器能胜任，结果是 500 + 空响应体。
     *         而支付网关一旦收到 5xx 就会持续重推通知，所以这里改成手工写响应体 ——
     *         任何异常都转换为约定文本 {@code failure}，让网关按正常重试策略处理。
     * </ul>
     */
    @PostMapping("/notify")
    public void notify(@RequestParam Map<String, String> params, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/plain;charset=UTF-8");
        String body;
        try {
            body = payService.handleNotify(params);
        } catch (BusinessException e) {
            log.warn("支付回调处理失败，已回复 failure：{}", e.getMessage());
            body = "failure";
        } catch (Exception e) {
            log.error("支付回调处理异常，已回复 failure", e);
            body = "failure";
        }
        response.getWriter().write(body);
    }

    /** 取消订单：仅「待支付」可取消，取消后库存与销量自动回补 */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(LoginUserHolder.getUserId(), orderId);
        return Result.success();
    }

    /** 确认收货：订单由「已发货」流转为「已完成」 */
    @PostMapping("/{orderId}/confirm")
    public Result<Void> confirm(@PathVariable Long orderId) {
        orderService.confirm(LoginUserHolder.getUserId(), orderId);
        return Result.success();
    }

    /**
     * 我的订单分页列表。
     *
     * <p>示例：GET /api/order/page?status=1&pageNum=1&pageSize=10
     */
    @GetMapping("/page")
    public Result<PageVO<OrderVO>> page(@RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) Long pageNum,
                                        @RequestParam(required = false) Long pageSize) {
        return Result.success(orderService.page(LoginUserHolder.getUserId(), status, pageNum, pageSize));
    }

    /** 订单详情（含明细） */
    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.success(orderService.detail(LoginUserHolder.getUserId(), orderId));
    }
}
