package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.FreightRuleVO;
import cn.edu.bookstore.model.vo.OrderSettleVO;

import java.math.BigDecimal;

/**
 * 运费业务接口。
 *
 * <p>把运费独立成一个服务而不是塞进 OrderService，是因为它有两个调用方、
 * 且规则来源是「可被管理端随时修改的配置」：
 * <ul>
 *     <li>结算预览（用户下单前看到的金额）；</li>
 *     <li>创建订单（真正落库的金额）。</li>
 * </ul>
 * 两条路径必须用同一份规则计算，否则就会出现「预览 107、下单 115」的金额不一致。
 * 收敛到一个实现类之后，这种不一致在结构上就不可能发生。
 *
 * @author 毕设项目组
 */
public interface FreightService {

    /** 当前生效的运费规则（前台展示说明、管理端配置页共用） */
    FreightRuleVO currentRule();

    /**
     * 依据商品小计算运费。
     *
     * @param goodsAmount 商品小计，null 或负数按 0 处理
     * @return 运费金额；全场包邮或已达门槛时返回 0.00
     */
    BigDecimal freightOf(BigDecimal goodsAmount);

    /**
     * 组装结算金额视图：商品小计 / 运费 / 应付 / 是否包邮 / 距包邮差额。
     *
     * @param goodsAmount   商品小计
     * @param totalQuantity 商品总件数（仅用于回显，不参与金额计算）
     */
    OrderSettleVO settle(BigDecimal goodsAmount, int totalQuantity);
}
