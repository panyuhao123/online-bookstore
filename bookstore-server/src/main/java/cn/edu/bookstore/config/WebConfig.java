package cn.edu.bookstore.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：静态资源映射。
 *
 * <p>说明：
 * <ul>
 *     <li>图书封面通过 /files/** 映射到本地 upload 目录（本地存储模式）；</li>
 *     <li>认证与授权已全部交给 Spring Security（见 {@link SecurityConfig}），
 *         跨域也在 Security 的 CORS 配置中统一处理，
 *         本类不再承担拦截器与 CORS 职责，避免与框架重复配置。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = appProperties.getUpload().getAccessPrefix();
        String location = appProperties.getUpload().getBaseDir();
        // 目录路径需以 "/" 结尾，Spring 才会识别为目录
        String dirLocation = location.endsWith("/") ? location : location + "/";
        String handler = prefix.endsWith("/") ? prefix + "**" : prefix + "/**";
        registry.addResourceHandler(handler)
                .addResourceLocations("file:" + dirLocation);
    }
}
