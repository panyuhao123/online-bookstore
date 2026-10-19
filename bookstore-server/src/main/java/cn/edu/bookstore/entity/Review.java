package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书评价实体，对应表 review。
 *
 * <p>与订单强绑定（必须带 order_id）：评价资格来自真实购买，
 * 这是防刷评最直接也最有效的方式。
 *
 * @author 毕设项目组
 */
@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long userId;

    /** 来源订单 ID：业务上要求只有「已完成」订单里的图书才能评价 */
    private Long orderId;

    /** 星级评分 1~5 */
    private Integer rating;

    private String content;

    /** 是否匿名：1-匿名（列表里显示「匿名用户」） 0-显示昵称 */
    private Integer anonymous;

    /** 状态：1-正常 0-已隐藏（后台下架违规评价，不做物理删除以保留痕迹） */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
