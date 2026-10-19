package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.model.vo.FreightRuleVO;
import cn.edu.bookstore.model.vo.OrderSettleVO;
import cn.edu.bookstore.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link FreightServiceImpl} 单元测试。
 *
 * <p>运费是「金额」这条链路上最容易出现争议的一段：门槛写在配置里，随时可能被运营改。
 * 因此这里把三条边界显式钉住：
 * <ul>
 *     <li>门槛判断是<b>「达到或超过」</b>——恰好等于门槛必须包邮，与用户直觉一致；</li>
 *     <li>门槛为 0 或负数视为<b>无条件包邮</b>，而不是「任何金额都要运费」；</li>
 *     <li>任何情况下都不返回 null，统一是两位小数的金额 —— 调用方要拿它做加法，
 *         返回 null 会在「应付金额」上变成空指针。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FreightServiceImpl —— 运费规则与结算金额")
class FreightServiceImplTest {

    private static final String KEY_ENABLED = "freight.enabled";
    private static final String KEY_THRESHOLD = "freight.free-threshold";
    private static final String KEY_BASE_FEE = "freight.base-fee";

    @Mock private SysConfigService sysConfigService;

    /**
     * 不用 @InjectMocks：实现类的依赖里有一个是真实对象（AppProperties）而非 mock，
     * 手工 new 出来更直白，也避免「Mockito 注入」与「测试自己装配」两套机制互相覆盖。
     */
    private FreightServiceImpl freightService;

    /** 实体里的兜底配置：yml 层的默认值。单测直接 new，不依赖 Spring 绑定。 */
    private final AppProperties appProperties = new AppProperties();

    @BeforeEach
    void wireDependencies() {
        freightService = new FreightServiceImpl(sysConfigService, appProperties);
        rule(true, "99", "8.00");
    }

    /** 配置三项运费规则 */
    private void rule(boolean enabled, String threshold, String baseFee) {
        when(sysConfigService.getBool(eq(KEY_ENABLED), anyBoolean())).thenReturn(enabled);
        when(sysConfigService.getDecimal(eq(KEY_THRESHOLD), any(BigDecimal.class)))
                .thenReturn(new BigDecimal(threshold));
        when(sysConfigService.getDecimal(eq(KEY_BASE_FEE), any(BigDecimal.class)))
                .thenReturn(new BigDecimal(baseFee));
    }

    // ============================================================== 规则

    @Nested
    @DisplayName("规则读取 currentRule()")
    class Rule {

        @Test
        @DisplayName("金额统一按两位小数返回，并生成面向用户的文案")
        void exposesRuleWithTip() {
            FreightRuleVO vo = freightService.currentRule();

            assertTrue(vo.getEnabled());
            assertEquals(0, new BigDecimal("99.00").compareTo(vo.getFreeThreshold()));
            assertEquals(0, new BigDecimal("8.00").compareTo(vo.getBaseFee()));
            assertEquals("满 99 元包邮", vo.getTip());
        }

        @Test
        @DisplayName("门槛带小数时不丢精度：满 59.5 元包邮")
        void keepsDecimalThresholdInTip() {
            rule(true, "59.50", "6.00");

            assertEquals("满 59.5 元包邮", freightService.currentRule().getTip());
        }

        @Test
        @DisplayName("关闭运费开关 → 文案为「全场包邮」")
        void tipWhenDisabled() {
            rule(false, "99", "8.00");

            assertEquals("全场包邮", freightService.currentRule().getTip());
        }

        @Test
        @DisplayName("门槛为 0 → 文案同样是「全场包邮」（0 门槛的语义是「没有门槛」）")
        void tipWhenThresholdZero() {
            rule(true, "0", "8.00");

            assertEquals("全场包邮", freightService.currentRule().getTip());
        }
    }

    // ============================================================== 计算

    @Nested
    @DisplayName("运费计算 freightOf()")
    class Compute {

        @Test
        @DisplayName("未达门槛 → 收基础运费")
        void chargesBaseFeeBelowThreshold() {
            assertEquals(0, new BigDecimal("8.00")
                    .compareTo(freightService.freightOf(new BigDecimal("98.99"))));
        }

        @Test
        @DisplayName("恰好等于门槛 → 包邮（判断口径是「达到或超过」，不是「超过」）")
        void freeAtExactThreshold() {
            assertTrue(freightService.freightOf(new BigDecimal("99.00")).signum() == 0);
            assertTrue(freightService.freightOf(new BigDecimal("99")).signum() == 0);
        }

        @Test
        @DisplayName("超过门槛 → 包邮，且返回 0.00 而不是 null")
        void freeAboveThreshold() {
            BigDecimal freight = freightService.freightOf(new BigDecimal("150.00"));

            assertEquals(0, BigDecimal.ZERO.compareTo(freight));
            // 必须是两位小数的零：调用方会拿它做加法，null 会在应付金额上变成空指针
            assertEquals(2, freight.scale());
        }

        @Test
        @DisplayName("关闭运费开关 → 一律包邮，与金额无关")
        void freeWhenDisabled() {
            rule(false, "99", "8.00");

            assertTrue(freightService.freightOf(new BigDecimal("1.00")).signum() == 0);
        }

        @Test
        @DisplayName("门槛为 0 或负数 → 无条件包邮（而不是「任何金额都收运费」）")
        void freeWhenThresholdNotPositive() {
            rule(true, "0", "8.00");
            assertTrue(freightService.freightOf(new BigDecimal("1.00")).signum() == 0);

            rule(true, "-5", "8.00");
            assertTrue(freightService.freightOf(new BigDecimal("1.00")).signum() == 0);
        }

        @Test
        @DisplayName("null 与负数商品小计按 0 处理，不抛异常")
        void toleratesNullAndNegative() {
            assertEquals(0, new BigDecimal("8.00").compareTo(freightService.freightOf(null)));
            assertEquals(0, new BigDecimal("8.00").compareTo(freightService.freightOf(new BigDecimal("-10"))));
        }
    }

    // ============================================================== 结算

    @Nested
    @DisplayName("结算金额 settle()")
    class Settle {

        @Test
        @DisplayName("未达门槛：应付 = 商品小计 + 运费，并给出距包邮差额")
        void settleBelowThreshold() {
            OrderSettleVO vo = freightService.settle(new BigDecimal("80.00"), 3);

            assertEquals(0, new BigDecimal("80.00").compareTo(vo.getGoodsAmount()));
            assertEquals(0, new BigDecimal("8.00").compareTo(vo.getFreightAmount()));
            assertEquals(0, new BigDecimal("88.00").compareTo(vo.getPayableAmount()));
            assertFalse(vo.getFreeShipping());
            assertEquals(0, new BigDecimal("19.00").compareTo(vo.getAmountToFreeShipping()));
            assertEquals(3, vo.getTotalQuantity());
        }

        @Test
        @DisplayName("已包邮：差额为 0.00（前端据此隐藏「再买 X 元包邮」）")
        void settleAboveThreshold() {
            OrderSettleVO vo = freightService.settle(new BigDecimal("120.00"), 2);

            assertEquals(0, new BigDecimal("120.00").compareTo(vo.getPayableAmount()));
            assertTrue(vo.getFreeShipping());
            assertEquals(0, BigDecimal.ZERO.compareTo(vo.getAmountToFreeShipping()));
        }

        @Test
        @DisplayName("null 小计按 0 算，且应付等于运费（不会出现 null 参与运算）")
        void settleWithNullAmount() {
            OrderSettleVO vo = freightService.settle(null, 0);

            assertEquals(0, BigDecimal.ZERO.compareTo(vo.getGoodsAmount()));
            assertEquals(0, new BigDecimal("8.00").compareTo(vo.getFreightAmount()));
            assertEquals(0, new BigDecimal("8.00").compareTo(vo.getPayableAmount()));
        }
    }
}
