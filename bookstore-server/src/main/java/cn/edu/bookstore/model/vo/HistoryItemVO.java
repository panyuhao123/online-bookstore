package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Book;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 浏览历史条目视图对象。
 *
 * <p>这里带的是图书的「当前快照」而不是浏览当时的快照：浏览历史是导航入口，
 * 点进去要看到这本书现在的价格与库存，用旧快照反而会点进详情后对不上。
 * （订单明细相反 —— 那里必须冻结下单瞬间的价格，因为金额对账不能被后续改价影响。）
 *
 * @author 毕设项目组
 */
@Data
public class HistoryItemVO {

    private Long bookId;

    private String title;

    private String author;

    private String coverUrl;

    /** 当前售价 */
    private BigDecimal price;

    /** 当前库存，0 表示缺货（前端可据此引导「到货提醒」） */
    private Integer stock;

    /** 图书状态：1-上架 0-下架 */
    private Integer status;

    /** 最近一次浏览时间 */
    private LocalDateTime viewTime;

    public static HistoryItemVO of(Book book, LocalDateTime viewTime) {
        HistoryItemVO vo = new HistoryItemVO();
        if (book != null) {
            vo.setBookId(book.getId());
            vo.setTitle(book.getTitle());
            vo.setAuthor(book.getAuthor());
            vo.setCoverUrl(book.getCoverUrl());
            vo.setPrice(book.getPrice());
            vo.setStock(book.getStock());
            vo.setStatus(book.getStatus());
        }
        vo.setViewTime(viewTime);
        return vo;
    }
}
