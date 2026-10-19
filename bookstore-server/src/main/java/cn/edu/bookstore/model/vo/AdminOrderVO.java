package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.entity.User;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端订单视图对象。
 *
 * <p>在 APP 端 {@link OrderVO} 基础上补充下单用户信息 —— 后台订单列表需要显示
 * 「谁下的单」，而 APP 端只看自己的订单，无需这些字段。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderVO extends OrderVO {

    private Long userId;

    /** 下单用户手机号（后台主要用它识别用户） */
    private String userPhone;

    private String userNickname;

    public static AdminOrderVO from(Orders orders, User user) {
        if (orders == null) {
            return null;
        }
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(orders.getId());
        vo.setOrderNo(orders.getOrderNo());
        vo.setTotalAmount(orders.getTotalAmount());
        vo.setStatus(orders.getStatus());
        vo.setStatusText(OrderStatus.textOf(orders.getStatus()));
        vo.setAddressSnapshot(orders.getAddressSnapshot());
        vo.setPayTime(orders.getPayTime());
        vo.setCreateTime(orders.getCreateTime());
        if (user != null) {
            vo.setUserId(user.getId());
            vo.setUserPhone(user.getPhone());
            vo.setUserNickname(user.getNickname());
        }
        return vo;
    }
}
