package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 运费规则视图对象。
 *
 * <p>前台需要它来渲染「再买 ¥X 包邮」这类引导文案，管理端需要它来做规则配置页。
 * 两端共用同一个结构，避免「前台按 A 口径算、后台按 B 口径改」的经典不一致。
 *
 * @author 毕设项目组
 */
@Data
public class FreightRuleVO {

    /** 是否收取运费：true-收取 false-全场包邮 */
    private Boolean enabled;

    /** 包邮门槛（元）；达到或超过该金额免运费 */
    private BigDecimal freeThreshold;

    /** 未达门槛时的运费（元） */
    private BigDecimal baseFee;

    /** 面向用户的一句话说明，如「满 99 元包邮」；全场包邮时为「全场包邮」 */
    private String tip;
}
