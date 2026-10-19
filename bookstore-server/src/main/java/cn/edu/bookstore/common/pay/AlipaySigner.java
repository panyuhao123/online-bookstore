package cn.edu.bookstore.common.pay;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 支付宝 RSA2 签名/验签工具。
 *
 * <p>为什么单独抽成一个类、并且全部是静态方法：
 * <ul>
 *     <li>这部分是<b>纯计算</b>，没有数据库、缓存、Spring 上下文依赖，
 *         单独放一处才能被单元测试直接覆盖 —— 支付相关的逻辑最怕「只能靠肉眼检查」；</li>
 *     <li>{@link cn.edu.bookstore.service.impl.AlipayPayServiceImpl} 只负责
 *         「组装参数 / 解析通知」这些业务流程，密码学细节不外泄到业务代码里。</li>
 * </ul>
 *
 * <p>实现要点（都是接入支付宝时最容易踩的坑）：
 * <ol>
 *     <li><b>签名前不做 URL 编码</b>：先编码再签名会导致验签必然失败。
 *         编码只发生在最终拼接跳转地址那一步；</li>
 *     <li><b>剔除空值参数</b>：值为空的参数不参与签名（支付宝通知里尤其常见）；</li>
 *     <li><b>ASCII 升序</b>：用 {@link TreeMap} 天然满足，不是按字典序也不是按插入顺序；</li>
 *     <li><b>回调时 sign 与 sign_type 都要剔除</b>；发起支付时只需剔除 sign；</li>
 *     <li><b>密钥清洗</b>：用户粘贴的密钥常带 PEM 头尾与换行，统一清理后再做 Base64 解码。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
public final class AlipaySigner {

    /** RSA2 对应 JDK 的 SHA256withRSA */
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    private AlipaySigner() {
        // 工具类，禁止实例化
    }

    /**
     * 构造待签名字符串。
     *
     * <p>规则：剔除 sign 与空值参数 → 按参数名 ASCII 升序 → 用 {@code k=v&k=v} 拼接。
     *
     * @param params 待签名参数（不会被修改）
     */
    public static String buildSignContent(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .filter(e -> !"sign".equals(e.getKey()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * 回调验签前的参数预处理：sign 与 sign_type 均不参与签名计算。
     *
     * <p>支付宝异步通知里 {@code sign_type} 是随通知一起发来的，
     * 而发起支付时它是我们主动放进去的 —— 所以两处的拼接口径不同，必须区分，
     * 这里抽成独立方法，避免以后把两处改成同一个而引入验签失败。
     */
    public static Map<String, String> withoutSignFields(Map<String, String> params) {
        Map<String, String> copy = new TreeMap<>(params);
        copy.remove("sign");
        copy.remove("sign_type");
        return copy;
    }

    /**
     * RSA2 签名（PKCS8 私钥）。
     *
     * @param content           待签名内容，来自 {@link #buildSignContent}
     * @param privateKeyBase64  应用私钥，允许带 PEM 头尾与换行
     * @return Base64 编码的签名值
     * @throws BusinessException 密钥格式错误或算法不可用时抛出（属于配置问题，需要人工介入）
     */
    public static String sign(String content, String privateKeyBase64) {
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(cleanKey(privateKeyBase64))));
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            log.error("支付宝签名失败（请检查应用私钥是否为 PKCS8 格式且未包含头尾标识）", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "支付签名失败，请检查支付配置");
        }
    }

    /**
     * RSA2 验签（X509 公钥）。
     *
     * <p>返回 false 而不是抛异常：验签失败属于「收到了伪造或损坏的通知」这一预期内情况，
     * 调用方据此返回 failure 即可，不该让它变成 500。
     *
     * @param content          待验签内容，来自 {@link #buildSignContent}（需先经过 {@link #withoutSignFields}）
     * @param signBase64       通知里带的签名值
     * @param publicKeyBase64  支付宝公钥，允许带 PEM 头尾与换行
     */
    public static boolean verify(String content, String signBase64, String publicKeyBase64) {
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(cleanKey(publicKeyBase64))));
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            log.error("支付宝验签异常（请检查支付宝公钥是否配置正确）", e);
            return false;
        }
    }

    /**
     * 清洗密钥：去掉换行、空格与 PEM 头尾标识。
     *
     * <p>实际粘贴密钥时经常会带上 {@code -----BEGIN PRIVATE KEY-----} 这类标识或换行，
     * 直接 Base64 解码会失败，这里统一处理，减少「配置看起来没错但就是签不出来」的排查成本。
     */
    public static String cleanKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }
}
