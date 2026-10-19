package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.service.impl.CaptchaServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码接口（匿名可访问，无需登录）。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaServiceImpl captchaService;

    /**
     * 获取图形验证码。
     *
     * <p>返回：{ captchaKey, imageBase64 }。captchaKey 前端原样传回登录接口，imageBase64 直接
     * 作为 {@code <img :src="imageBase64">} 展示。
     */
    @GetMapping
    public Result<CaptchaServiceImpl.CaptchaResult> generate() {
        return Result.success(captchaService.generate());
    }
}
