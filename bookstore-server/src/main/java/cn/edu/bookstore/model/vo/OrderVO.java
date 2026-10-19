package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.Orders;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象（列表与详情共用）。
 *
 * <p>列表场景 items 可为空，详情场景返回完整明细。
 *
 * @author 毕设项目组
 */
@Data
public class OrderVO {

    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 商品小计（各明细金额之和，<b>不含运费</b>） */
    private BigDecimal totalAmount;

    /** 运费金额；包邮时为 0.00 */
    private BigDecimal freightAmount;

    /**
     * 应付合计 = 商品小计 + 运费。
     *
     * <p>由服务端算好一并返回，而不是让前端做加法：
     * 金额展示是这个系统里最不能出错的地方，多一个前端计算点就多一处
     * 「浮点误差 / 漏加运费」的机会。存量订单运费为 0，此值等于商品小计。
     */
    private BigDecimal payableAmount;

    /** 状态码：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消 */
    private Integer status;

    /** 状态文案，直接用中文展示 */
    private String statusText;

    /** 收货地址快照 */
    private String addressSnapshot;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 支付时间；未支付为 null */
    private LocalDateTime payTime;

    /** 发货时间；未发货为 null */
    private LocalDateTime shipTime;

    /** 完成时间（用户确认收货）；未完成为 null */
    private LocalDateTime finishTime;

    /** 订单明细 */
    private List<OrderItemVO> items;

    /** 商品总件数（列表页展示「共 N 件」） */
    private Integer totalQuantity;

    public static OrderVO from(Orders orders) {
        if (orders == null) {
            return null;
        }
        return fillBase(new OrderVO(), orders);
    }

    /**
     * 把订单主表的公共字段填进视图对象，并返回同一个实例。
     *
     * <p>抽出来是给子类 {@link AdminOrderVO} 复用：管理端订单视图在 APP 端字段之外
     * 只多了「下单用户」三项，如果让它自己再抄一遍字段赋值，就必然出现
     * 「OrderVO 加了字段、AdminOrderVO 忘了加」—— 后台订单列表里运费一栏永远空白，
     * 而 APP 端却是对的。这种只在某一端暴露的缺陷最难在自测中发现，所以从结构上禁掉它。
     *
     * @param vo     待填充的视图对象（可以是子类实例）
     * @param orders 订单主表记录
     */
    protected static <T extends OrderVO> T fillBase(T vo, Orders orders) {
        vo.setId(orders.getId());
        vo.setOrderNo(orders.getOrderNo());
        vo.setTotalAmount(orders.getTotalAmount());
        // 运费列可为 null（理论上只有手工插数据才会出现），兜底成 0，
        // 避免 payableAmount 变成 null 让前端到处判空。
        BigDecimal freight = orders.getFreightAmount() == null ? BigDecimal.ZERO : orders.getFreightAmount();
        vo.setFreightAmount(freight);
        vo.setPayableAmount(
                (orders.getTotalAmount() == null ? BigDecimal.ZERO : orders.getTotalAmount()).add(freight));
        vo.setStatus(orders.getStatus());
        vo.setStatusText(OrderStatus.textOf(orders.getStatus()));
        vo.setAddressSnapshot(orders.getAddressSnapshot());
        vo.setCreateTime(orders.getCreateTime());
        vo.setPayTime(orders.getPayTime());
        vo.setShipTime(orders.getShipTime());
        vo.setFinishTime(orders.getFinishTime());
        return vo;
    }
}
