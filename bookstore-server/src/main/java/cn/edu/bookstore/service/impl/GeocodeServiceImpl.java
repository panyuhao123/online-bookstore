package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.util.AmapApiClient;
import cn.edu.bookstore.common.util.AmapApiClient.AmapApiException;
import cn.edu.bookstore.common.util.AmapApiClient.GeocodeItem;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.model.vo.GeocodeResult;
import cn.edu.bookstore.model.vo.GeocodeResult.Candidate;
import cn.edu.bookstore.service.GeocodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 高德地理编码地址解析业务实现。
 *
 * <p>核心逻辑：
 * <ol>
 *     <li>限流 —— 同一用户 10 秒内只允许调用 1 次，超过则拒绝；</li>
 *     <li>调高德 API —— 网络异常/高德错误/无结果 → 统一抛 {@link BusinessException}，
 *         Controller 捕获后返回 400，前端弹提示且不清除用户已输入的地址文本；</li>
 *     <li>候选截断 —— 高德可能返回几十条，按 {@code maxCandidates} 截断，防止刷接口；</li>
 *     <li>日志 —— 每次调用记录 userId、地址、耗时、返回候选数，便于排查和统计。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodeServiceImpl implements GeocodeService {

    private final AmapApiClient amapClient;
    private final AppProperties appProperties;

    /**
     * 限流桶 —— 同一用户最后一次调用的时间戳（毫秒）。
     *
     * <p>用 {@link ConcurrentHashMap} 本地 fallback（不依赖 Redis 也能跑），
     * 类似 CaptchaService 的双轨策略。10 秒窗口下本地内存完全够用，
     * 不会因为 GC 丢数据导致「刚调完又能调」的宽松。
     */
    private static final ConcurrentMap<Long, Long> RATE_LIMIT = new ConcurrentHashMap<>();

    @Override
    public GeocodeResult parse(Long userId, String address) {
        // userId 可能为 null（白名单模式下未登录也能调），给 0L 占位保证限流不崩
        long effectiveUserId = userId != null ? userId : 0L;
        AppProperties.Gaode gaode = appProperties.getGaode();
        long now = System.currentTimeMillis();
        long windowMs = gaode.getRateLimitSeconds() * 1000L;

        // ========== 1. 限流检查 ==========
        Long lastCall = RATE_LIMIT.get(effectiveUserId);
        if (lastCall != null && (now - lastCall) < windowMs) {
            log.warn("地址解析限流触发：userId={}, address={}, within={}ms",
                    effectiveUserId, address, now - lastCall);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "操作太频繁，请稍后再试（" + gaode.getRateLimitSeconds() + "秒内只能解析 1 次）");
        }

        RATE_LIMIT.put(effectiveUserId, now);

        long start = System.currentTimeMillis();
        try {
            List<GeocodeItem> rawItems = amapClient.geocode(address);

            int limit = gaode.getMaxCandidates();
            if (rawItems.size() > limit) {
                rawItems = rawItems.subList(0, limit);
            }

            List<Candidate> candidates = new ArrayList<>();
            for (GeocodeItem item : rawItems) {
                candidates.add(GeocodeResult.from(item));
            }

            long cost = System.currentTimeMillis() - start;
            log.info("地址解析成功：userId={}, address={}, candidates={}, cost={}ms",
                    effectiveUserId, address, candidates.size(), cost);

            GeocodeResult result = new GeocodeResult();
            result.setCandidates(candidates);
            result.setCount(candidates.size());
            result.setUniqueMatch(candidates.size() == 1);
            return result;

        } catch (AmapApiException e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("地址解析高德报错：userId={}, address={}, error={}, cost={}ms",
                    effectiveUserId, address, e.getMessage(), cost);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "地址解析失败：" + e.getMessage());
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("地址解析未知异常：userId={}, address={}, cost={}ms",
                    effectiveUserId, address, cost, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "地址解析失败，请稍后重试");
        }
    }
}
