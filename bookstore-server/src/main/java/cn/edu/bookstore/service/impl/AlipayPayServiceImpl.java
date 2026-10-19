package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.pay.AlipaySigner;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.model.vo.PrepayVO;
import cn.edu.bookstore.service.OrderService;
import cn.edu.bookstore.service.PayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 支付宝沙箱支付实现（电脑网站支付 alipay.trade.page.pay）。
 *
 * <p><b>当前状态（务必如实告知使用者）</b>：
 * 下单参数组装、RSA2 签名与异步通知验签的逻辑均已实现，
 * 且签名/验签逻辑已通过「自生成密钥对的往返测试」验证（见单元测试）。
 * <b>但尚未与支付宝沙箱真实交互过</b> —— 因为沙箱凭证（appId、应用私钥、支付宝公钥）
 * 需要由使用者本人从支付宝开放平台申请，并且异步通知要求公网可达的地址。
 * 凭证配齐后即可切换开关使用，无需改动任何业务代码。
 *
 * <p>为什么不用官方 SDK 而用 JDK 原生实现签名：
 * 本项目只在「组装参数 → 签名 → 拼接跳转地址」与「验签」两个点上需要支付宝能力，
 * 用 {@code java.security} 十几行就能完成，避免引入一个体积不小的依赖；
 * 同时签名规则完全透明，便于讲解与排查。
 *
 * <p>接入真实沙箱的步骤（写在这里，避免遗忘）：
 * <ol>
 *     <li>登录支付宝开放平台 → 沙箱环境，获取 APPID、应用私钥、支付宝公钥；</li>
 *     <li>配置环境变量：PAY_TYPE=alipay、ALIPAY_APP_ID、ALIPAY_PRIVATE_KEY、ALIPAY_PUBLIC_KEY；</li>
 *     <li>配置公网可达的 ALIPAY_NOTIFY_URL（本地可用内网穿透）；</li>
 *     <li>启动后调 POST /api/order/{id}/pay，拿到 payUrl 在浏览器打开即可进入沙箱收银台。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookstore.pay.type", havingValue = "alipay")
public class AlipayPayServiceImpl implements PayService {

    /** 支付宝要求的请求时间格式 */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 异步通知中表示「交易成功」的状态值 */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";

    /** 支付宝要求收到通知后返回的应答内容 */
    private static final String NOTIFY_ACK = "success";

    private final OrdersMapper ordersMapper;
    private final OrderService orderService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "alipay";
    }

    @Override
    public PrepayVO prepay(Long userId, Long orderId) {
        AppProperties.Pay.Alipay conf = appProperties.getPay().getAlipay();

        Orders orders = ordersMapper.selectById(orderId);
        if (orders == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!orders.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权支付他人的订单");
        }
        if (orders.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "该订单状态不可支付，请勿重复操作");
        }

        // ---- 1. 业务参数 biz_content ----
        Map<String, Object> bizContent = new TreeMap<>();
        bizContent.put("out_trade_no", orders.getOrderNo());
        // 金额必须是「元 + 两位小数」的字符串，且不能带千分位
        bizContent.put("total_amount", orders.getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        bizContent.put("subject", "线上书店订单 " + orders.getOrderNo());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        // 支付超时时间：与订单超时保持一致，超时后支付宝侧也拒绝支付
        bizContent.put("timeout_express", conf.getTimeoutMinutes() + "m");

        String bizJson;
        try {
            bizJson = objectMapper.writeValueAsString(bizContent);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付参数组装失败");
        }

        // ---- 2. 公共参数 ----
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", conf.getAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", conf.getSignType());
        params.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        params.put("version", "1.0");
        params.put("biz_content", bizJson);
        if (!conf.getNotifyUrl().isBlank()) {
            params.put("notify_url", conf.getNotifyUrl());
        }
        if (!conf.getReturnUrl().isBlank()) {
            params.put("return_url", conf.getReturnUrl());
        }

        // ---- 3. 签名 ----
        String signContent = buildSignContent(params);
        params.put("sign", sign(signContent, conf.getPrivateKey()));

        log.info("支付宝下单参数已生成 orderNo={} amount={}（尚未与沙箱真实交互）",
                orders.getOrderNo(), bizContent.get("total_amount"));

        // ---- 4. 拼接跳转地址（这一步才需要 URL 编码）----
        String payUrl = conf.getGatewayUrl() + "?" + params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        PrepayVO vo = new PrepayVO();
        vo.setPayType(type());
        vo.setOrderNo(orders.getOrderNo());
        vo.setAmount(orders.getTotalAmount());
        vo.setSubject(String.valueOf(bizContent.get("subject")));
        vo.setPayUrl(payUrl);
        vo.setPaid(false);
        vo.setExpireSeconds(conf.getTimeoutMinutes() * 60L);
        return vo;
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        AppProperties.Pay.Alipay conf = appProperties.getPay().getAlipay();

        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");
        String sign = params.get("sign");

        // 1. 验签：确认通知确实来自支付宝。没有这一步，任何人都能伪造「已支付」请求白拿商品。
        if (sign == null || !verify(buildSignContent(withoutSignFields(params)), sign, conf.getAlipayPublicKey())) {
            log.warn("支付宝异步通知验签失败，已忽略 outTradeNo={}", outTradeNo);
            return "failure";
        }

        // 2. 只处理交易成功，其余状态（如等待付款、已关闭）直接应答成功但不改订单
        if (!TRADE_SUCCESS.equals(tradeStatus)) {
            log.info("支付宝通知状态非成功，不处理 outTradeNo={} tradeStatus={}", outTradeNo, tradeStatus);
            return NOTIFY_ACK;
        }

        // 3. 金额核对：防止「用 0.01 元支付 1000 元订单」这类篡改
        Orders orders = ordersMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Orders>()
                        .eq(Orders::getOrderNo, outTradeNo))
                .stream().findFirst().orElse(null);
        if (orders == null) {
            log.warn("支付宝通知对应的订单不存在 outTradeNo={}", outTradeNo);
            return NOTIFY_ACK;
        }
        String notifiedAmount = params.get("total_amount");
        if (notifiedAmount != null
                && orders.getTotalAmount().compareTo(new BigDecimal(notifiedAmount)) != 0) {
            log.error("支付宝通知金额与订单金额不一致，拒绝入账 outTradeNo={} 通知={} 订单={}",
                    outTradeNo, notifiedAmount, orders.getTotalAmount());
            return "failure";
        }

        // 4. 落状态：markPaidByOrderNo 内部用条件更新保证幂等，
        //    支付宝会重复推送通知，这里重复调用是安全的
        orderService.markPaidByOrderNo(outTradeNo);

        // 5. 必须返回纯文本 success，否则支付宝会按策略重复通知
        return NOTIFY_ACK;
    }

    /**
     * 构造待签名字符串（规则见 {@link AlipaySigner#buildSignContent}）。
     *
     * <p>密码学细节已抽到 {@link AlipaySigner}，这里只做委托 ——
     * 业务类不关心 RSA 还是国密，只关心「要签名」这件事。
     */
    private String buildSignContent(Map<String, String> params) {
        return AlipaySigner.buildSignContent(params);
    }

    /** 回调验签时，sign 与 sign_type 不参与签名计算 */
    private Map<String, String> withoutSignFields(Map<String, String> params) {
        return AlipaySigner.withoutSignFields(params);
    }

    /** RSA2（SHA256withRSA）签名，PKCS8 私钥 */
    private String sign(String content, String privateKeyBase64) {
        return AlipaySigner.sign(content, privateKeyBase64);
    }

    /** RSA2 验签，X509 公钥 */
    private boolean verify(String content, String signBase64, String publicKeyBase64) {
        return AlipaySigner.verify(content, signBase64, publicKeyBase64);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
