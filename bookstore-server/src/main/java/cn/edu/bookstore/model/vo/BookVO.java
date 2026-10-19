package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Book;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 图书视图对象（列表与详情共用）。
 *
 * @author 毕设项目组
 */
@Data
public class BookVO {

    private Long id;

    private Long categoryId;

    /** 分类名称，列表展示用（由 Service 补齐，避免 APP 端二次请求） */
    private String categoryName;

    private String title;

    private String author;

    private String publisher;

    private BigDecimal price;

    private Integer stock;

    private String coverUrl;

    private String description;

    private Integer sales;

    /** 状态：1-上架 0-下架 */
    private Integer status;

    /**
     * 平均评分，保留一位小数。
     *
     * <p>由评价模块在 Service 层批量回填（一次 GROUP BY 覆盖整页图书，避免 N+1）；
     * {@code book} 表本身不存评分 —— 冗余字段一旦写漏就会长期展示错误分数，
     * 而评价表的行数在毕设规模下聚合成本可以忽略。
     *
     * <p>无评价时为 null，APP 端显示「暂无评分」而不是误导性的 0 分。
     */
    private BigDecimal avgRating;

    /** 评价条数；无评价时为 0，前端可直接显示「(0)」 */
    private Integer reviewCount;

    public static BookVO from(Book book) {
        if (book == null) {
            return null;
        }
        BookVO vo = new BookVO();
        vo.setId(book.getId());
        vo.setCategoryId(book.getCategoryId());
        vo.setTitle(book.getTitle());
        vo.setAuthor(book.getAuthor());
        vo.setPublisher(book.getPublisher());
        vo.setPrice(book.getPrice());
        vo.setStock(book.getStock());
        vo.setCoverUrl(book.getCoverUrl());
        vo.setDescription(book.getDescription());
        vo.setSales(book.getSales());
        vo.setStatus(book.getStatus());
        return vo;
    }
}
