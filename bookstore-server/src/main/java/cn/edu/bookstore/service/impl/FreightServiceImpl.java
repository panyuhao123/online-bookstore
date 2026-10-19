package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.model.vo.FreightRuleVO;
import cn.edu.bookstore.model.vo.OrderSettleVO;
import cn.edu.bookstore.service.FreightService;
import cn.edu.bookstore.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 运费业务实现。
 *
 * <p>配置来源分两层，优先级从高到低：
 * <ol>
 *     <li>{@code sys_config} 表中的三项（管理端可改）；</li>
 *     <li>{@code bookstore.freight.*}（application.yml 中的兜底默认值）。</li>
 * </ol>
 * 之所以保留 yml 这一层：配置表可能被人误清空（比如执行了删除数据脚本），
 * 此时运费规则仍能回落到一个合理值，下单流程不会因此中断。
 *
 * <p>金额口径（三条边界都显式处理，不留隐式行为）：
 * <ul>
 *     <li>门槛判断用 {@code compareTo}，且是「达到或超过」——
 *         商品小计恰好等于门槛时包邮，这与用户直觉一致；</li>
 *     <li>门槛为 0 或负数时视为「无条件包邮」，而不是「任何金额都要运费」；</li>
 *     <li>结果为 0 时统一返回 {@code 0.00} 而不是 null，避免调用方在扣减金额时踩空指针。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreightServiceImpl implements FreightService {

    /** 金额统一保留两位小数，用 HALF_UP 与财务口径一致（四舍五入） */
    private static final int SCALE = 2;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private static final String KEY_ENABLED = "freight.enabled";

    private static final String KEY_FREE_THRESHOLD = "freight.free-threshold";

    private static final String KEY_BASE_FEE = "freight.base-fee";

    private final SysConfigService sysConfigService;

    private final AppProperties appProperties;

    @Override
    public FreightRuleVO currentRule() {
        AppProperties.Freight fallback = appProperties.getFreight();
        FreightRuleVO vo = new FreightRuleVO();
        boolean enabled = sysConfigService.getBool(KEY_ENABLED, fallback.isEnabled());
        BigDecimal threshold = sysConfigService.getDecimal(
                KEY_FREE_THRESHOLD, fallback.getFreeThreshold());
        BigDecimal baseFee = sysConfigService.getDecimal(KEY_BASE_FEE, fallback.getBaseFee());

        vo.setEnabled(enabled);
        vo.setFreeThreshold(threshold.setScale(SCALE, RoundingMode.HALF_UP));
        vo.setBaseFee(baseFee.setScale(SCALE, RoundingMode.HALF_UP));
        vo.setTip(buildTip(enabled, threshold));
        return vo;
    }

    @Override
    public BigDecimal freightOf(BigDecimal goodsAmount) {
        FreightRuleVO rule = currentRule();
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            return ZERO;
        }
        // 门槛 <= 0 表示无条件包邮。若不特判，0 门槛会让「任何金额 >= 0」恒成立，
        // 结果碰巧也是包邮，但语义上「门槛为 0」更接近「关闭门槛」而不是「0 元起包邮」，
        // 显式写出来可以让读代码的人不必自己推导。
        if (rule.getFreeThreshold().signum() <= 0) {
            return ZERO;
        }
        BigDecimal amount = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        if (amount.compareTo(rule.getFreeThreshold()) >= 0) {
            return ZERO;
        }
        return rule.getBaseFee().setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public OrderSettleVO settle(BigDecimal goodsAmount, int totalQuantity) {
        FreightRuleVO rule = currentRule();
        BigDecimal goods = (goodsAmount == null ? BigDecimal.ZERO : goodsAmount)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal freight = freightOf(goods);

        OrderSettleVO vo = new OrderSettleVO();
        vo.setGoodsAmount(goods);
        vo.setFreightAmount(freight);
        vo.setPayableAmount(goods.add(freight));
        vo.setFreeShipping(freight.signum() == 0);
        vo.setFreeThreshold(rule.getFreeThreshold());
        vo.setTotalQuantity(totalQuantity);

        // 差额只在「收运费」时有意义：已包邮时给 0.00，前端据此隐藏「再买 X 元包邮」的提示。
        if (freight.signum() == 0) {
            vo.setAmountToFreeShipping(ZERO);
        } else {
            vo.setAmountToFreeShipping(rule.getFreeThreshold().subtract(goods)
                    .setScale(SCALE, RoundingMode.HALF_UP));
        }
        return vo;
    }

    /** 生成面向用户的一句话说明 */
    private String buildTip(boolean enabled, BigDecimal threshold) {
        if (!enabled) {
            return "全场包邮";
        }
        if (threshold.signum() <= 0) {
            return "全场包邮";
        }
        // 整数门槛去掉小数尾巴（99.00 → 99），避免页面上出现「满 99.00 元包邮」这种啰嗦文案
        return "满 " + threshold.stripTrailingZeros().toPlainString() + " 元包邮";
    }
}
