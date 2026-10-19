package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价展示对象（APP 图书详情页的「用户评价」列表）。
 *
 * <p>昵称由 SQL 直接 JOIN user 表带出，并在数据库侧完成匿名替换 ——
 * 这样「匿名」这件事只在服务端发生一次，APP 端拿到的就是要展示的文本，
 * 不会因为前端忘记处理而把匿名用户的昵称漏出去。
 *
 * @author 毕设项目组
 */
@Data
public class ReviewVO {

    private Long id;

    private Long bookId;

    /** 评价人显示名（匿名评价已在此处替换为「匿名用户」） */
    private String nickname;

    /** 星级 1~5 */
    private Integer rating;

    private String content;

    /** 是否匿名：1-是 0-否 */
    private Integer anonymous;

    private LocalDateTime createTime;

    /**
     * 图书标题 / 封面。
     *
     * <p>仅「我的评价」列表使用（图书详情页的评价列表里这些字段冗余，为 null）；
     * 复用同一个 VO 避免再多一个几乎相同的类。
     */
    private String bookTitle;

    private String bookCoverUrl;
}
