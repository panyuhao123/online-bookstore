package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播图实体，对应表 banner。
 *
 * <p>{@code imageUrl} 为空时前台回退渲染 {@code bgColor}，这个兜底是有意设计的：
 * 演示库不必先准备并上传三张图才能让首页看起来正常；
 * 运营补图之后前台自动优先用图片，前后端都不需要为此改代码。
 *
 * @author 毕设项目组
 */
@Data
@TableName("banner")
public class Banner {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主标题 */
    private String title;

    /** 副标题 / 描述 */
    private String subTitle;

    /** 背景图地址；为空时前台用 bgColor */
    private String imageUrl;

    /** 无图时的背景色或渐变，如 linear-gradient(135deg,#667eea,#764ba2) */
    private String bgColor;

    /** 点击跳转路径（站内 hash 路径或完整外链） */
    private String linkUrl;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 状态：1-上架 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
