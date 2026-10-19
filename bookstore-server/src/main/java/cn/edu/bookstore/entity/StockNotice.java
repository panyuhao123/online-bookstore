package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缺货到货提醒订阅实体，对应表 stock_notice。
 *
 * <p>这个功能的业务价值主要在管理端：把它按图书聚合之后，
 * 「哪本书有多少人在等」就是一份现成的补货优先级清单 ——
 * 比单纯看「库存为 0」有用得多（滞销书的库存也经常是 0）。
 *
 * <p>取消订阅不删行，而是把 status 改成 2（已取消）。原因是
 * (user_id, book_id) 上有唯一键，留行才能让「取消后重新订阅」复用同一行；
 * 若改成物理删除，用户反复订阅/取消就会不断产生新行，唯一键也就失去意义。
 *
 * @author 毕设项目组
 */
@Data
@TableName("stock_notice")
public class StockNotice {

    /** 状态：待通知 */
    public static final int STATUS_PENDING = 0;

    /** 状态：已通知 */
    public static final int STATUS_NOTIFIED = 1;

    /** 状态：已取消 */
    public static final int STATUS_CANCELLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订阅用户 ID（业务外键） */
    private Long userId;

    /** 图书 ID（业务外键） */
    private Long bookId;

    /** 状态：0-待通知 1-已通知 2-已取消 */
    private Integer status;

    /** 通知时间（管理端标记已通知时写入） */
    private LocalDateTime notifyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
