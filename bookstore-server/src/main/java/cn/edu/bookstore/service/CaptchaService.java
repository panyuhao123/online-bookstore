package cn.edu.bookstore.service;

/**
 * 图形验证码服务。
 *
 * <p>职责：生成图形验证码（4 位随机数字）→ 存储（Redis / 本地内存 fallback）→ 校验（一次性，校验后立即失效）。
 *
 * @author 毕设项目组
 */
public interface CaptchaService {

    /** 验证码有效期（秒）：5 分钟 */
    long CAPTCHA_TTL_SECONDS = 5 * 60;

    /** 一次性校验 + 校验后立即删除。
     *
     * @param captchaKey  客户端获取验证码时拿到的唯一标识
     * @param captchaCode 用户填写的验证码
     * @throws cn.edu.bookstore.common.BusinessException 各种失败场景带不同错误文案：过期/无效/不匹配/请先获取
     */
    void verify(String captchaKey, String captchaCode);
}
