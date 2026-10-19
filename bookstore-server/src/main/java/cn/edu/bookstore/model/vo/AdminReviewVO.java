package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端评价展示对象。
 *
 * <p>比 APP 端多带图书标题与评价人 ID：
 * 后台要能快速定位「哪本书的哪条评价」，也需要知道评价归属哪个用户以便处理违规。
 * 匿名评价在后台**不做匿名处理** —— 后台是管理场景，必须能看到真实评价人。
 *
 * @author 毕设项目组
 */
@Data
public class AdminReviewVO {

    private Long id;

    private Long bookId;

    private String bookTitle;

    private Long userId;

    private String userNickname;

    private Long orderId;

    private String orderNo;

    private Integer rating;

    private String content;

    private Integer anonymous;

    /** 状态：1-正常 0-已隐藏 */
    private Integer status;

    private LocalDateTime createTime;
}
