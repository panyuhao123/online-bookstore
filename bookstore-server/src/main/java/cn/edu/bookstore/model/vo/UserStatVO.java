package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个用户的订单聚合结果（累计订单数 / 消费金额）。
 *
 * <p>由 OrdersMapper 的分组查询直接映射，避免逐用户统计造成 N+1。
 *
 * @author 毕设项目组
 */
@Data
public class UserStatVO {

    private Long userId;

    private Long orderCount;

    private BigDecimal totalAmount;
}
