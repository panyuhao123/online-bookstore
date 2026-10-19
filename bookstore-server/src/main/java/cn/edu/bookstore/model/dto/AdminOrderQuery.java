package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端订单查询条件：支持订单号 / 状态筛选。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderQuery extends BasePageQuery {

    /** 订单号，支持右模糊匹配 */
    private String orderNo;

    /** 订单状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消；为空表示不筛选 */
    private Integer status;

    /** 下单用户 ID，为空表示不筛选 */
    private Long userId;
}
