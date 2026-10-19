package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 图书评分聚合结果（按 book_id 分组）。
 *
 * <p>用于列表场景：一次 GROUP BY 拿到整页图书的评分与评价数，避免逐本查询造成 N+1。
 *
 * @author 毕设项目组
 */
@Data
public class BookRatingStatVO {

    private Long bookId;

    /** 平均分，保留一位小数 */
    private BigDecimal avgRating;

    /** 评价条数 */
    private Integer reviewCount;
}
