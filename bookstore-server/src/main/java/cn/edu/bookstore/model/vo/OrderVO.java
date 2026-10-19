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

    private BigDecimal totalAmount;

    /** 状态码：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消 */
    private Integer status;

    /** 状态文案，直接用中文展示 */
    private String statusText;

    /** 收货地址快照 */
    private String addressSnapshot;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    /** 订单明细 */
    private List<OrderItemVO> items;

    /** 商品总件数（列表页展示「共 N 件」） */
    private Integer totalQuantity;

    public static OrderVO from(Orders orders) {
        if (orders == null) {
            return null;
        }
        OrderVO vo = new OrderVO();
        vo.setId(orders.getId());
        vo.setOrderNo(orders.getOrderNo());
        vo.setTotalAmount(orders.getTotalAmount());
        vo.setStatus(orders.getStatus());
        vo.setStatusText(OrderStatus.textOf(orders.getStatus()));
        vo.setAddressSnapshot(orders.getAddressSnapshot());
        vo.setPayTime(orders.getPayTime());
        vo.setCreateTime(orders.getCreateTime());
        return vo;
    }
}
