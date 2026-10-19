package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单金额结算预览视图对象。
 *
 * <p>为什么需要一个专门的预览接口，而不是让前端自己算：
 * 运费规则（是否包邮、门槛多少）是<b>服务端配置</b>，管理端随时可改。
 * 如果前端拿商品小计自己判断包邮，一旦运营改了门槛，
 * 前端显示的应付金额就会与服务端下单时算出的金额不一致 ——
 * 用户看到「应付 107 元」，提交后订单却写着 115 元，这是最难解释的一类投诉。
 * 因此金额一律由服务端算，前端只负责展示。
 *
 * @author 毕设项目组
 */
@Data
public class OrderSettleVO {

    /** 商品小计（各明细 subtotal 之和） */
    private BigDecimal goodsAmount;

    /** 运费金额；包邮时为 0.00 */
    private BigDecimal freightAmount;

    /** 应付合计 = 商品小计 + 运费 */
    private BigDecimal payableAmount;

    /** 当前是否满足包邮 */
    private Boolean freeShipping;

    /** 包邮门槛（元），供前端渲染进度提示 */
    private BigDecimal freeThreshold;

    /**
     * 距包邮还差多少元；已包邮时为 0.00。
     *
     * <p>由服务端算而不是前端减，同样是为了避免门槛改了两边口径不一致。
     */
    private BigDecimal amountToFreeShipping;

    /** 商品总件数 */
    private Integer totalQuantity;
}
