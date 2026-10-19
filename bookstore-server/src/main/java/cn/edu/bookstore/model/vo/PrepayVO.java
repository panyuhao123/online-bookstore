package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发起支付后的返回信息（收银台数据）。
 *
 * <p>不同支付渠道的差异全部收敛在这里，APP 端只需按 {@code paid} 与 {@code payUrl} 分支处理：
 * <ul>
 *     <li>{@code paid = true}：支付已完成（模拟支付渠道），APP 直接跳订单成功页；</li>
 *     <li>{@code paid = false} 且 {@code payUrl} 非空：需要跳转/唤起支付，完成后轮询订单状态。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Data
public class PrepayVO {

    /** 支付渠道：mock / alipay */
    private String payType;

    /** 商户订单号（支付宝侧称 out_trade_no），本项目直接复用订单号 */
    private String orderNo;

    /** 应付金额 */
    private BigDecimal amount;

    /** 订单标题（支付渠道侧展示，如「订单 BK2026...」） */
    private String subject;

    /**
     * 支付跳转地址。
     *
     * <p>模拟支付渠道为 null；支付宝渠道为拼接好签名参数的网关地址
     * （电脑网站支付，用户在新页面完成付款）。
     */
    private String payUrl;

    /** 是否已完成支付。模拟支付渠道恒为 true */
    private Boolean paid;

    /** 支付超时秒数，前端可据此提示「请在 x 分钟内完成支付」 */
    private Long expireSeconds;
}
