package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 「可评价的商品」条目：订单里的一本书 + 是否已评价。
 *
 * <p>APP 端订单详情页据此渲染「评价」按钮：
 * 未评价的显示「去评价」，已评价的显示「查看评价」。
 *
 * @author 毕设项目组
 */
@Data
public class ReviewableItemVO {

    private Long orderId;

    private Long bookId;

    private String bookTitle;

    private String bookCoverUrl;

    /** 下单时的单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 是否已评价 */
    private Boolean reviewed;

    /** 已评价时对应的评价 ID，便于前端直接跳转查看 */
    private Long reviewId;

    /** 已评价时回显星级，便于前端展示「已评 5 星」 */
    private Integer rating;
}
