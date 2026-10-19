package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览历史实体，对应表 browse_history。
 *
 * <p>一个用户对一本书只保留一行：(user_id, book_id) 上有唯一键，
 * 重复浏览用「更新浏览时间」而不是「再插一行」来处理。
 * 这样表的行数天然有上限（用户数 × 图书数），不会随浏览次数无限增长。
 *
 * <p>100 条上限没有做成表约束，而是由 Service 在写入后裁剪：
 * 上限属于可调的业务策略，写在代码里调整时只需重启，若写成触发器或视图反而更难维护。
 *
 * @author 毕设项目组
 */
@Data
@TableName("browse_history")
public class BrowseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（业务外键） */
    private Long userId;

    /** 图书 ID（业务外键） */
    private Long bookId;

    /** 最近一次浏览时间（同一本书再次浏览时被刷新） */
    private LocalDateTime viewTime;
}
