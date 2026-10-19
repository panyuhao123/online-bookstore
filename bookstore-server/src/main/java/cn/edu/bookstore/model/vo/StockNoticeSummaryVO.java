package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺货图书的「等待人数」汇总（管理端到货提醒看板）。
 *
 * <p>这是到货提醒功能真正的落点：把「谁在等」聚合成「哪本书有多少人在等」，
 * 运营按 waitingCount 倒序补货即可。单看「库存为 0」并不能说明什么 ——
 * 滞销图书的库存也常年是 0。
 *
 * @author 毕设项目组
 */
@Data
public class StockNoticeSummaryVO {

    private Long bookId;

    private String bookTitle;

    private String bookCoverUrl;

    /** 当前库存（补货后应大于 0） */
    private Integer stock;

    /** 图书状态：1-上架 0-下架 */
    private Integer bookStatus;

    /** 待通知人数（status=0 的订阅数） */
    private Integer waitingCount;

    /** 已通知人数（status=1） */
    private Integer notifiedCount;

    /** 最近一次订阅时间，用于判断「需求是否还在持续」 */
    private LocalDateTime lastSubscribeTime;

    /** 若该书已补货且有库存，管理端可提示「可直接通知」 */
    private BigDecimal price;
}
