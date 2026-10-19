package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图书实体，对应表 book。
 *
 * <p>价格使用 BigDecimal，禁止用 double 参与金额计算。
 *
 * @author 毕设项目组
 */
@Data
@TableName("book")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属分类 ID（业务外键） */
    private Long categoryId;

    private String title;

    private String author;

    private String publisher;

    /** 单价 */
    private BigDecimal price;

    /** 库存 */
    private Integer stock;

    /** 封面图地址 */
    private String coverUrl;

    /** 图书简介 */
    private String description;

    /** 销量 */
    private Integer sales;

    /** 状态：1-上架 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
