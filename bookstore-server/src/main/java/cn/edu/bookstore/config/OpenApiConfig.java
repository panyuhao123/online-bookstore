package cn.edu.bookstore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（OpenAPI 3 + Knife4j UI）。
 *
 * <p>访问地址：<a href="http://127.0.0.1:8080/doc.html">http://127.0.0.1:8080/doc.html</a>
 *
 * <p>做三件事：
 * <ol>
 *     <li><b>按角色分组</b>：APP 端接口与管理端接口分成两个分组，
 *         避免几十个接口堆在一起，也一眼能看出权限边界；</li>
 *     <li><b>声明 Bearer 鉴权</b>：在文档里点「Authorize」填入登录返回的 token，
 *         就能直接调试需要登录的接口，不必再手拼 curl；</li>
 *     <li><b>标明统一返回约定</b>：文档描述里写清「HTTP 恒 200、业务结果看 code」，
 *         避免用 Swagger 调试的人误以为是接口坏了。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Configuration
public class OpenApiConfig {

    /** 与 JwtAuthenticationFilter 中解析的请求头保持一致 */
    private static final String SECURITY_SCHEME_NAME = "BearerToken";

    @Bean
    public OpenAPI bookstoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("线上书店 APP 后端接口文档")
                        .description("""
                                线上书店系统后端 REST 接口（SpringBoot 3 + MyBatis-Plus + MySQL + Redis）。

                                **统一返回约定**：HTTP 状态码恒为 200，业务成败只看响应体中的 `code`：
                                200 成功 / 400 参数错误 / 401 未登录或令牌过期 / 403 无权限 /
                                404 不存在 / 409 冲突 / 422 业务失败 / 500 服务器异常。

                                **鉴权方式**：先调登录接口拿到 `token`，再点击右上角「Authorize」
                                填入该 token，即可调试所有需要登录的接口。
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("毕设项目组"))
                        .license(new License().name("仅用于教学演示")))
                // 声明 Bearer 令牌，让文档页面能直接带 token 调试
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("登录接口返回的 token，格式：Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /** APP 端（用户端）接口分组 */
    @Bean
    public GroupedOpenApi appGroup() {
        return GroupedOpenApi.builder()
                .group("01-APP端接口")
                .pathsToMatch(
                        "/api/user/**",
                        "/api/book/**",
                        "/api/category/**",
                        "/api/review/**",
                        "/api/cart/**",
                        "/api/address/**",
                        "/api/order/**")
                .build();
    }

    /** 管理端（Web 后台）接口分组 */
    @Bean
    public GroupedOpenApi adminGroup() {
        return GroupedOpenApi.builder()
                .group("02-管理端接口")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    /** 运维 / 监控接口分组 */
    @Bean
    public GroupedOpenApi systemGroup() {
        return GroupedOpenApi.builder()
                .group("03-系统接口")
                .pathsToMatch("/health", "/ready")
                .build();
    }
}
