package cn.edu.bookstore.model.vo;

import lombok.Data;

/**
 * 订单状态分布的一行（工作台状态计数条 / 订单页状态胶囊）。
 *
 * <p>用「状态码 + 单量」而不是 Map，是为了让返回顺序与 {@code OrderStatus} 枚举一致，
 * 前端不必再排一次序；同一份结构既能画计数条，也能直接当筛选入口。
 *
 * @author 毕设项目组
 */
@Data
public class StatusCountVO {

    /** 订单状态码：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消 */
    private Integer status;

    /** 该状态下的订单数；无订单时为 0 而不是缺失 */
    private Long orderCount;

    public static StatusCountVO of(Integer status, Long orderCount) {
        StatusCountVO vo = new StatusCountVO();
        vo.setStatus(status);
        vo.setOrderCount(orderCount == null ? 0L : orderCount);
        return vo;
    }
}
