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
 * 订单主表实体，对应表 orders。
 *
 * <p>address_snapshot 保存下单瞬间的地址快照，用户后续修改地址不会影响历史订单。
 *
 * @author 毕设项目组
 */
@Data
@TableName("orders")
public class Orders {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号，对外展示用 */
    private String orderNo;

    private Long userId;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消 */
    private Integer status;

    /** 收货地址快照 */
    private String addressSnapshot;

    /** 支付时间（模拟支付时写入） */
    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
