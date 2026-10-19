package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书单条目实体，对应表 book_list_item。
 *
 * <p>表上有 (list_id, book_id) 唯一键，因此「同一本书重复加入同一书单」在数据库层
 * 就写不进去。业务层在插入前仍会先查一次，但那只是为了给用户「该书已在书单中」的
 * 友好提示，而不是依赖应用层判断来保证数据正确性 —— 并发下先查后插本身是有窗口的。
 *
 * @author 毕设项目组
 */
@Data
@TableName("book_list_item")
public class BookListItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 书单 ID（业务外键） */
    private Long listId;

    /** 图书 ID（业务外键） */
    private Long bookId;

    /** 书单内排序值，越小越靠前 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
