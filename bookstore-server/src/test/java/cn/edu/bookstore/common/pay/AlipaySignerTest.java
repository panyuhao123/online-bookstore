package cn.edu.bookstore.common.pay;

import cn.edu.bookstore.common.BusinessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付宝 RSA2 签名 / 验签单元测试。
 *
 * <p>测试策略：<b>不依赖任何支付宝账号或沙箱</b>。
 * 运行时现场生成一对 RSA 密钥，用私钥签名、用公钥验签，
 * 从而把「签名规则是否正确」这件事变成可自动执行的断言 ——
 * 否则这段代码在拿到沙箱凭证之前完全没有办法验证，只能靠人眼读。
 *
 * <p>覆盖的关键规则：
 * <ol>
 *     <li>待签名字符串的拼接口径（ASCII 升序 / 剔除 sign / 剔除空值）；</li>
 *     <li>签名与验签能对上（往返测试）；</li>
 *     <li>内容被篡改后验签必须失败（这是防「伪造已支付通知」的根本保障）；</li>
 *     <li>换一对密钥后验签必须失败；</li>
 *     <li>带 PEM 头尾与换行的密钥也能正常使用（用户粘贴格式问题）。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@DisplayName("支付宝 RSA2 签名 / 验签")
class AlipaySignerTest {

    /** 被测场景：本应用私钥 + 支付宝公钥（这里用自生成密钥对模拟） */
    private static String appPrivateKey;
    private static String appPublicKey;

    /** 「攻击者」自己的密钥对，用于验证换密钥后验签必然失败 */
    private static String otherPrivateKey;
    private static String otherPublicKey;

    @BeforeAll
    static void generateKeyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair app = generator.generateKeyPair();
        appPrivateKey = Base64.getEncoder().encodeToString(app.getPrivate().getEncoded());
        appPublicKey = Base64.getEncoder().encodeToString(app.getPublic().getEncoded());

        KeyPair other = generator.generateKeyPair();
        otherPrivateKey = Base64.getEncoder().encodeToString(other.getPrivate().getEncoded());
        otherPublicKey = Base64.getEncoder().encodeToString(other.getPublic().getEncoded());
    }

    @Test
    @DisplayName("待签名字符串：按 ASCII 升序拼接，剔除 sign 与空值")
    void buildSignContent_shouldSortAndSkipBlank() {
        Map<String, String> params = new LinkedHashMap<>();
        // 刻意打乱顺序，并且加入各种「应当被剔除」的项
        params.put("timestamp", "2026-10-16 16:00:00");
        params.put("app_id", "2021000000000000");
        params.put("sign", "SHOULD_BE_REMOVED");
        params.put("empty", "");
        params.put("blank", "   ");
        params.put("nullValue", null);
        params.put("charset", "utf-8");

        String content = AlipaySigner.buildSignContent(params);

        assertEquals("app_id=2021000000000000&charset=utf-8&timestamp=2026-10-16 16:00:00", content);
        assertFalse(content.contains("sign="), "sign 自身不能参与签名");
        assertFalse(content.contains("empty"), "空字符串参数必须被剔除");
        assertFalse(content.contains("blank"), "空白字符串参数必须被剔除");
        assertFalse(content.contains("nullValue"), "null 参数必须被剔除");
    }

    @Test
    @DisplayName("待签名字符串：排序依据是 ASCII，大写字母在小写字母之前")
    void buildSignContent_shouldUseAsciiOrder() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("zone", "3");
        params.put("AppId", "1");
        params.put("appId", "2");

        // 'A'(65) < 'a'(97)，所以 AppId 排在 appId 与 zone 之前
        assertEquals("AppId=1&appId=2&zone=3", AlipaySigner.buildSignContent(params));
    }

    @Test
    @DisplayName("回调参数预处理：sign 与 sign_type 都要剔除")
    void withoutSignFields_shouldRemoveSignAndSignType() {
        Map<String, String> notify = new TreeMap<>();
        notify.put("out_trade_no", "BK202610161600001234");
        notify.put("trade_status", "TRADE_SUCCESS");
        notify.put("sign", "SIGN_VALUE");
        notify.put("sign_type", "RSA2");

        Map<String, String> cleaned = AlipaySigner.withoutSignFields(notify);

        assertEquals(2, cleaned.size());
        assertFalse(cleaned.containsKey("sign"));
        assertFalse(cleaned.containsKey("sign_type"));
        assertTrue(cleaned.containsKey("out_trade_no"));
        // 原 Map 不应被修改，否则调用方后续读取 sign 会拿不到值
        assertEquals(4, notify.size(), "withoutSignFields 不应修改入参");
    }

    @Test
    @DisplayName("往返测试：私钥签名, 公钥验签通过")
    void signAndVerify_shouldMatch() {
        String content = "app_id=2021000000000000&charset=utf-8&method=alipay.trade.page.pay";

        String sign = AlipaySigner.sign(content, appPrivateKey);

        assertTrue(sign.length() > 100, "2048 位 RSA 的 Base64 签名长度应在 300+ 字符量级");
        assertTrue(AlipaySigner.verify(content, sign, appPublicKey), "同一份内容必须验签通过");
    }

    @Test
    @DisplayName("内容被篡改后验签必须失败（防伪造支付通知）")
    void verify_shouldFailWhenContentTampered() {
        String content = "out_trade_no=BK123&total_amount=28.00&trade_status=TRADE_SUCCESS";
        String sign = AlipaySigner.sign(content, appPrivateKey);

        // 攻击者把金额改成 0.01 元
        String tampered = content.replace("28.00", "0.01");

        assertFalse(AlipaySigner.verify(tampered, sign, appPublicKey),
                "金额被篡改后验签必须失败，否则可以 0.01 元买走 28 元的书");
    }

    @Test
    @DisplayName("签名值本身被篡改后验签必须失败")
    void verify_shouldFailWhenSignTampered() {
        String content = "out_trade_no=BK123&total_amount=28.00";
        String sign = AlipaySigner.sign(content, appPrivateKey);

        // 改动签名中的几个字符（保持 Base64 合法性）
        char[] chars = sign.toCharArray();
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        String broken = new String(chars);

        assertFalse(AlipaySigner.verify(content, broken, appPublicKey));
    }

    @Test
    @DisplayName("换成别人的密钥对后验签必须失败")
    void verify_shouldFailWithWrongKeyPair() {
        String content = "out_trade_no=BK123&total_amount=28.00";
        String signByAttacker = AlipaySigner.sign(content, otherPrivateKey);

        assertFalse(AlipaySigner.verify(content, signByAttacker, appPublicKey),
                "只有用支付宝私钥签出的通知才应被接受");
        assertTrue(AlipaySigner.verify(content, signByAttacker, otherPublicKey),
                "换成攻击者自己的公钥则能验证 —— 说明失败原因确实是密钥不匹配");
    }

    @Test
    @DisplayName("密钥清洗：带 PEM 头尾与换行的密钥也能正常签名验签")
    void cleanKey_shouldStripPemHeaderAndWhitespace() {
        String privateKeyWithPem = "-----BEGIN PRIVATE KEY-----\n"
                + wrapEvery64(appPrivateKey)
                + "\n-----END PRIVATE KEY-----";
        String publicKeyWithPem = "-----BEGIN PUBLIC KEY-----\n"
                + wrapEvery64(appPublicKey)
                + "\n-----END PUBLIC KEY-----";

        String content = "app_id=test&charset=utf-8";

        // 清洗后应与原始密钥完全一致
        assertEquals(appPrivateKey, AlipaySigner.cleanKey(privateKeyWithPem));
        assertEquals(appPublicKey, AlipaySigner.cleanKey(publicKeyWithPem));

        // 直接使用带格式的密钥也能跑通，不必让使用者手工整理
        String sign = AlipaySigner.sign(content, privateKeyWithPem);
        assertTrue(AlipaySigner.verify(content, sign, publicKeyWithPem));
    }

    @Test
    @DisplayName("私钥格式非法时抛业务异常，而不是 500 空响应")
    void sign_shouldThrowBusinessExceptionOnBadKey() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> AlipaySigner.sign("content", "这不是一个合法的私钥"));
        assertEquals(cn.edu.bookstore.common.ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("公钥格式非法时验签返回 false，不抛异常")
    void verify_shouldReturnFalseOnBadPublicKey() {
        String sign = AlipaySigner.sign("content", appPrivateKey);

        // 公钥配错属于「配置问题」，但验签失败是预期内结果：
        // 抛异常会让回调接口 500，从而触发支付网关无限重推
        assertFalse(AlipaySigner.verify("content", sign, "不是合法公钥"));
        assertFalse(AlipaySigner.verify("content", "不是合法签名", appPublicKey));
    }

    /** 把长 Base64 串按 64 字符换行，模拟从支付宝控制台复制出来的密钥格式 */
    private static String wrapEvery64(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        return sb.toString();
    }
}
