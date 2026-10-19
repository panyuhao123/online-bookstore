package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.service.CaptchaService;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码实现。
 *
 * <p>存储层：优先 Redis（TTL 5 分钟），不可用时自动降级为本地 {@link ConcurrentHashMap}。
 * 两种存储都做一次性校验 —— verify 后立即删除，防止暴力重试。
 *
 * <p>格式：hutool {@code CaptchaUtil.createLineCaptcha(120, 40, 4, 20)}：120×40 像素、4 位数字、20 条干扰线。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String REDIS_PREFIX = "bookstore:captcha:";
    /** Redis 可用性开关：成功过一次即认为可用，避免每次都付出超时代价 */
    private static volatile boolean redisAvailable = true;
    private static final Map<String, String> LOCAL_CAPTCHA = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOCAL_EXPIRE = new ConcurrentHashMap<>();

    /** hutool 参数 */
    private static final int W = 120;
    private static final int H = 40;
    private static final int N = 4;   // 4 位数字
    private static final int L = 20;  // 20 条干扰线

    private final StringRedisTemplate redisTemplate;

    public CaptchaServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 生成：返回 captchaKey + imageBase64 */
    public CaptchaResult generate() {
        // 1. 生成图形验证码 —— 构造时就指定纯数字字符集，保证图片和存储一致
        LineCaptcha captcha = new LineCaptcha(W, H, N, L);
        captcha.setGenerator(new RandomGenerator("0123456789", N));
        captcha.createCode();   // 触发生成

        String code = captcha.getCode();   // 拿到的就是纯数字，4 位
        if (code == null || code.length() < N) {
            // 兜底
            code = "";
            for (int i = 0; i < N; i++) code += (int) (Math.random() * 10);
        }

        // 2. 生成唯一 captchaKey 并存入存储层
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        store(captchaKey, code);

        // 3. 图片转 Base64
        String base64;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage img = captcha.getImage();
            ImageIO.write(img, "png", baos);
            base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("生成验证码图片失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码生成失败");
        }
        return new CaptchaResult(captchaKey, base64);
    }

    @Override
    public void verify(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先获取验证码");
        }
        if (captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入验证码");
        }
        String expected = loadAndDelete(captchaKey); // 取后即删，一次性
        if (expected == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码已过期或已失效，请重新获取");
        }
        if (!expected.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误，请重新输入");
        }
    }

    // ========================== 存储层 ==========================

    private void store(String key, String code) {
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(REDIS_PREFIX + key, code, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，降级为本地内存存储。原因：{}", e.getMessage());
            }
        }
        // 本地 fallback
        LOCAL_CAPTCHA.put(key, code);
        LOCAL_EXPIRE.put(key, System.currentTimeMillis() + CAPTCHA_TTL_SECONDS * 1000L);
    }

    /** 读取 + 删除（一次性）。过期 / 不存在 → null */
    private String loadAndDelete(String key) {
        if (redisAvailable) {
            try {
                String v = redisTemplate.opsForValue().get(REDIS_PREFIX + key);
                if (v != null) {
                    redisTemplate.delete(REDIS_PREFIX + key);
                }
                return v;
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 读取失败，降级到本地内存。原因：{}", e.getMessage());
            }
        }
        // 本地 fallback
        Long expireAt = LOCAL_EXPIRE.get(key);
        if (expireAt == null || System.currentTimeMillis() > expireAt) {
            LOCAL_CAPTCHA.remove(key);
            LOCAL_EXPIRE.remove(key);
            return null;
        }
        LOCAL_EXPIRE.remove(key);
        return LOCAL_CAPTCHA.remove(key); // ConcurrentHashMap.remove 返回被删除的值
    }

    /** 返回 DTO */
    public record CaptchaResult(String captchaKey, String imageBase64) {}
}
