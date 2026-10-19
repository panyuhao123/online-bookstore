package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.StockNotice;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 到货提醒订阅视图对象（用户侧「我的订阅」）。
 *
 * @author 毕设项目组
 */
@Data
public class StockNoticeVO {

    private Long id;

    private Long bookId;

    private String bookTitle;

    private String bookCoverUrl;

    private BigDecimal price;

    /** 订阅时的库存；若已补货则 &gt; 0，用户可以直接下单 */
    private Integer stock;

    /** 图书状态：1-上架 0-下架 */
    private Integer bookStatus;

    /** 订阅状态：0-待通知 1-已通知 2-已取消 */
    private Integer status;

    /** 状态文案，直接用中文展示 */
    private String statusText;

    /** 订阅时间 */
    private LocalDateTime createTime;

    /** 通知时间 */
    private LocalDateTime notifyTime;

    public static StockNoticeVO of(StockNotice notice, Book book) {
        StockNoticeVO vo = new StockNoticeVO();
        vo.setId(notice.getId());
        vo.setBookId(notice.getBookId());
        vo.setStatus(notice.getStatus());
        vo.setStatusText(textOf(notice.getStatus()));
        vo.setCreateTime(notice.getCreateTime());
        vo.setNotifyTime(notice.getNotifyTime());
        if (book != null) {
            vo.setBookTitle(book.getTitle());
            vo.setBookCoverUrl(book.getCoverUrl());
            vo.setPrice(book.getPrice());
            vo.setStock(book.getStock());
            vo.setBookStatus(book.getStatus());
        }
        return vo;
    }

    /** 状态码转中文文案 */
    public static String textOf(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case StockNotice.STATUS_PENDING -> "待到货通知";
            case StockNotice.STATUS_NOTIFIED -> "已通知";
            case StockNotice.STATUS_CANCELLED -> "已取消";
            default -> "未知";
        };
    }
}
