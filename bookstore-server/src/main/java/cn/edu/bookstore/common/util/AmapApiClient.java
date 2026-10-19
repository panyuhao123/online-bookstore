package cn.edu.bookstore.common.util;

import cn.edu.bookstore.config.AppProperties;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 高德开放平台 Web服务API 客户端（地理编码）。
 *
 * <p>职责：
 * <ul>
 *     <li>封装高德签名逻辑（key + secret → sig）；</li>
 *     <li>统一请求/响应格式，业务层只需传地址文本；</li>
 *     <li>异常全部抛出 {@link AmapApiException}，由 Service 层捕获。</li>
 * </ul>
 *
 * <p>安全降级：secret 留空时自动退化为仅 key 模式，
 * 此时高德控制台需选择「key 安全模式」而非「签名安全模式」。
 *
 * @author 毕设项目组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmapApiClient {

    private final AppProperties appProperties;

    /**
     * 地理编码：地址文本 → 省/市/区/adcode/精确格式化地址。
     *
     * @param address 用户输入的完整地址文本（如「北京市朝阳区望京街道SOHO T1」）
     * @return 候选地址列表（高德可能返回多条，按置信度从高到低）
     * @throws AmapApiException 网络超时 / 高德返回错误码 / 无结果
     */
    public List<GeocodeItem> geocode(String address) {
        AppProperties.Gaode gaode = appProperties.getGaode();

        // 0. 未配置 key 时提前失败：避免带着空 key 去打高德，拿回一个看不懂的 INVALID_KEY，
        //    这里直接给出「功能未启用、请手动填写地址」的可操作提示。
        if (StrUtil.isBlank(gaode.getKey())) {
            throw new AmapApiException("地址自动解析功能未启用（未配置高德 key），请手动填写完整地址");
        }

        String path = "/geo"; // 地理编码接口
        String baseUrl = gaode.getBaseUrl(); // https://restapi.amap.com/v3/geocode

        // 1. 构造请求参数（高德要求 output=JSON 否则返回 XML）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", gaode.getKey());
        params.put("address", address);
        params.put("output", "JSON");

        // 2. 计算签名（仅当配置了 secret 时）
        if (StrUtil.isNotBlank(gaode.getSecret())) {
            params.put("sig", computeSig(params, gaode.getSecret()));
        }

        // 3. 拼接完整 URL（手动拼，确保签名前不编码）
        String fullUrl = buildUrl(baseUrl + path, params);

        // 4. 发请求 + 超时保护
        String respBody;
        try (HttpResponse resp = HttpRequest.get(fullUrl)
                .timeout(gaode.getTimeoutMs())
                .execute()) {
            if (!resp.isOk()) {
                throw new AmapApiException("HTTP " + resp.getStatus() + " 调用高德失败");
            }
            respBody = resp.body();
        } catch (Exception e) {
            if (e instanceof AmapApiException aex) throw aex;
            throw new AmapApiException("网络异常 / 请求超时：" + e.getMessage(), e);
        }

        // 5. 解析高德返回 JSON
        JSONObject json;
        try {
            json = JSONUtil.parseObj(respBody);
        } catch (Exception e) {
            throw new AmapApiException("高德返回 JSON 解析失败：" + respBody, e);
        }

        // 高德返回结构：{status: "1", info: "OK", geocodes: [{...}, ...]}
        String status = json.getStr("status");
        String info = json.getStr("info");

        if (!"1".equals(status)) {
            // status=0 表示业务错误，info 里有具体原因（如 INVALID_KEY / QUERY_OVER_LIMIT）
            String infocode = json.getStr("infocode", "");
            log.warn("高德地理编码失败：status={}, info={}, infocode={}, address={}",
                    status, info, infocode, address);
            throw new AmapApiException("高德错误[" + info + "]"
                    + (StrUtil.isNotBlank(infocode) ? "（" + infocode + "）" : ""));
        }

        JSONArray geocodes = json.getJSONArray("geocodes");
        if (geocodes == null || geocodes.isEmpty()) {
            throw new AmapApiException("高德未匹配到该地址，请检查拼写是否正确");
        }

        // 6. 转成业务 DTO
        List<GeocodeItem> items = new ArrayList<>();
        for (int i = 0; i < geocodes.size(); i++) {
            JSONObject g = geocodes.getJSONObject(i);
            items.add(new GeocodeItem(
                    g.getStr("formatted_address"),
                    g.getStr("province"),
                    g.getStr("city"),
                    g.getStr("district"),
                    g.getStr("adcode"),
                    g.getStr("location")    // "lng,lat" 格式
            ));
        }
        return items;
    }

    /**
     * 高德数字签名算法。
     *
     * <p>规则（官方文档）：
     * <ol>
     *     <li>所有请求参数（不含 sig 本身）按参数名 ASCII 码从小到大排序；</li>
     *     <li>按 key1=value1&amp;key2=value2 形式拼接；</li>
     *     <li>末尾直接追加 secret（注意不是 &amp;secret=xxx，是值直接串在末尾）；</li>
     *     <li>MD5 32 位大写。</li>
     * </ol>
     */
    private String computeSig(Map<String, String> params, String secret) {
        // 1. 按 key 字典序排序
        List<String> sortedKeys = params.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        // 2. 拼 key=value&key=value...（注意：排序前的 value 是原始值，URL 编码由 HttpRequest 自己处理）
        String paramStr = sortedKeys.stream()
                .map(k -> k + "=" + params.get(k))
                .collect(Collectors.joining("&"));

        // 3. 末尾直接加 secret 值
        String toSign = paramStr + secret;

        // 4. MD5 大写
        return SecureUtil.md5(toSign).toUpperCase();
    }

    /** 拼接完整 URL：path?key1=val1&key2=val2（值做 URL 编码） */
    private String buildUrl(String path, Map<String, String> params) {
        StringJoiner sj = new StringJoiner("&", path + "?", "");
        for (Map.Entry<String, String> e : params.entrySet()) {
            sj.add(e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sj.toString();
    }

    // ========================== DTO ==========================

    /**
     * 单条候选地址。
     *
     * <p>高德地理编码返回的 geocodes 数组中的每一项。
     * 注意高德对直辖市返回的 province 和 city 可能相同（如 province=北京市, city=北京市）。
     */
    public record GeocodeItem(
            /** 高德格式化完整地址，如「北京市朝阳区望京街道SOHO T1」 */
            String formattedAddress,
            /** 省份名称，如「北京市」「广东省」 */
            String province,
            /** 城市名称，如「北京市」「深圳市」 */
            String city,
            /** 区县名称，如「朝阳区」「南山区」 */
            String district,
            /** 区县 adcode，如「110105」 */
            String adcode,
            /** 经纬度 lng,lat 字符串（可拆出 longitude/latitude） */
            String location
    ) {}

    /** 高德 API 异常（统一捕获，不抛原始 HTTP/SocketException） */
    public static class AmapApiException extends RuntimeException {
        public AmapApiException(String msg) { super(msg); }
        public AmapApiException(String msg, Throwable cause) { super(msg, cause); }
    }
}
