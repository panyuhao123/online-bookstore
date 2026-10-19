package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户书单实体，对应表 book_list。
 *
 * <p>为什么要把书单从浏览器本地存储搬到数据库：书单是最典型的「用户创造内容」，
 * 留在 localStorage 里意味着换台电脑、换个浏览器就全没了，
 * 而它又恰好是「我整理的书单可以分享给别人看」这类功能的前提。
 *
 * <p>{@code bookCount} 是冗余计数列：书单列表页每张卡片都要显示「共 N 本」，
 * 若不做冗余就得对每个书单各发一次 count 查询（典型 N+1）。
 * 代价是每次加书/移书都要同步维护它，这一点统一收敛在 Service 里，不允许别处直接写该字段。
 *
 * @author 毕设项目组
 */
@Data
@TableName("book_list")
public class BookList {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建者用户 ID（业务外键） */
    private Long userId;

    /** 书单名称 */
    private String name;

    /** 书单简介 */
    private String description;

    /** 封面图地址；为空时前端用书单内第一本书的封面兜底 */
    private String coverUrl;

    /** 是否公开：1-公开（任何人可浏览） 0-仅自己可见 */
    private Integer isPublic;

    /** 书单内图书数量（冗余计数，只由 Service 维护） */
    private Integer bookCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
