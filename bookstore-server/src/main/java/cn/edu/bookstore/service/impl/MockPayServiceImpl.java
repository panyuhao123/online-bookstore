package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.model.vo.PrepayVO;
import cn.edu.bookstore.service.OrderService;
import cn.edu.bookstore.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 模拟支付实现（默认渠道）。
 *
 * <p>定位：让「订单支付」这条业务链路在没有真实支付渠道的情况下也能完整跑通。
 * 答辩演示时最怕的就是「演示到支付这一步需要联网 + 登录第三方账号」，
 * 因此默认渠道做成模拟支付，点一下即完成，零外部依赖。
 *
 * <p>它和真实渠道的差别只在「谁来确认支付成功」：
 * 模拟渠道由本方法直接确认，真实渠道由异步通知确认；
 * 两者最终都调用 {@link OrderService#markPaidByOrderNo(String)} 落状态，
 * 因此订单状态机、幂等性、后续发货流程完全共用一套代码。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookstore.pay.type", havingValue = "mock", matchIfMissing = true)
public class MockPayServiceImpl implements PayService {

    private final OrdersMapper ordersMapper;
    private final OrderService orderService;
    private final AppProperties appProperties;

    @Override
    public String type() {
        return "mock";
    }

    @Override
    public PrepayVO prepay(Long userId, Long orderId) {
        Orders orders = ordersMapper.selectById(orderId);
        if (orders == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!orders.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权支付他人的订单");
        }

        // 可选的模拟延迟：让前端能看到「支付中」的 loading，演示更真实
        long delay = appProperties.getPay().getMock().getDelayMillis();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 复用订单服务的既有逻辑（含状态校验与友好错误提示），
        // 再通过「按订单号置为已支付」这条与真实渠道相同的路径落状态
        orderService.pay(userId, orderId);
        log.info("模拟支付完成 userId={} orderNo={} 金额={}", userId, orders.getOrderNo(), orders.getTotalAmount());

        PrepayVO vo = new PrepayVO();
        vo.setPayType(type());
        vo.setOrderNo(orders.getOrderNo());
        vo.setAmount(orders.getTotalAmount());
        vo.setSubject(buildSubject(orders));
        vo.setPayUrl(null);
        // 模拟渠道当场即完成支付，无需跳转收银台
        vo.setPaid(true);
        vo.setExpireSeconds(0L);
        return vo;
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        // 模拟支付没有外部渠道，不存在异步通知。
        // 这里显式拒绝，避免误配回调地址时产生「看起来成功」的假象。
        throw new BusinessException(ErrorCode.BAD_REQUEST,
                "当前为模拟支付渠道，不支持异步通知；如需回调请将 bookstore.pay.type 配置为 alipay");
    }

    private String buildSubject(Orders orders) {
        return "订单 " + orders.getOrderNo();
    }
}
