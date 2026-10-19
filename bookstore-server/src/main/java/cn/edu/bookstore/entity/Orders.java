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

    /**
     * 商品小计金额（各明细 subtotal 之和），<b>不含运费</b>。
     *
     * <p>注意语义：这一列从建库起就是「商品小计」，接入运费后刻意没有改成「含运费总价」——
     * 历史订单的该字段含义是商品金额，若就地改写，老订单的金额构成就对不上账了。
     * 用户实际应付 = {@code totalAmount + freightAmount}，由 {@code OrderVO#payableAmount} 给出。
     */
    private BigDecimal totalAmount;

    /**
     * 运费金额。
     *
     * <p>存量订单（接入运费之前创建的）一律为 0.00，语义上视为「当时包邮」，
     * 与新的包邮订单表现一致，因此订单列表不需要为老数据做特例判断。
     */
    private BigDecimal freightAmount;

    /** 状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消 */
    private Integer status;

    /** 收货地址快照 */
    private String addressSnapshot;

    /** 支付时间（模拟支付时写入） */
    private LocalDateTime payTime;

    /**
     * 发货时间（管理端发货时写入）。
     *
     * <p>订单详情的时间线需要真实节点时刻。此前只能用 update_time 近似，
     * 但 update_time 会被任意一次无关更新刷新，时间线上会出现
     * 「发货时间晚于收货时间」这类看起来像 bug 的展示。
     */
    private LocalDateTime shipTime;

    /** 完成时间（用户确认收货时写入） */
    private LocalDateTime finishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
