package cn.edu.bookstore.config;

import cn.edu.bookstore.common.web.JwtAuthenticationFilter;
import cn.edu.bookstore.common.web.RestAuthExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置：认证（JWT 无状态）与授权（路径级 + 方法级）。
 *
 * <p>设计要点：
 * <ul>
 *     <li><b>无状态</b>：令牌自带身份，服务端不存 session，接口可以水平扩容；</li>
 *     <li><b>授权规则集中</b>：白名单、管理员路径、其余需登录，三档规则只在这里定义一次，
 *         避免以往「拦截器里判路径、Controller 里再判角色」两处逻辑漂移；</li>
 *     <li><b>方法级授权</b>：开启 @EnableMethodSecurity，管理端接口额外标注 @PreAuthorize，
 *         即使将来路径前缀写错也不会被越权访问（双保险）；</li>
 *     <li><b>响应结构统一</b>：401/403 由 RestAuthExceptionHandler 输出与业务一致的 JSON。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 匿名可访问的路径。
     *
     * <p>依据需求文档 3.1.1：图书与分类允许未登录浏览；
     * 注册、登录、登出、健康检查、静态资源（封面图）无需令牌。
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/user/register",
            "/api/user/login",
            "/api/user/logout",
            "/api/admin/login",
            // 图形验证码：匿名可访问（登录/注册前必须能拿到验证码图片）
            "/api/captcha",
            "/api/captcha/**",
            "/api/book/**",
            "/api/category/**",
            // 图书评价的「读」接口同样开放：看别人的评价属于浏览行为，
            // 与图书详情一样不应要求登录。注意这里只放行 /book/** 这一段，
            // 「发表评价 / 我的评价 / 订单评价项」仍在 /api/review 下的其他路径，
            // 依然是登录才能访问 —— 白名单按最小范围给，不要写成 /api/review/**。
            "/api/review/book/**",
            // 支付渠道异步通知：调用方是支付平台服务器，不可能携带用户令牌。
            // 安全性由「回调验签」保证（见 AlipayPayServiceImpl），不依赖登录态。
            "/api/order/notify",
            "/health",
            "/ready",
            "/files/**",
            "/error",
            // 接口文档（Knife4j / springdoc）：文档页面本身不含业务数据，
            // 且调试时需要匿名加载静态资源；真正的接口调用仍要带令牌。
            // 注意：生产环境应把 /doc.html 与 /v3/api-docs/** 关掉或加访问限制，
            // 否则会把全部接口清单暴露出去。本项目为教学演示，故默认开放。
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/favicon.ico"
    };

    private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  JwtAuthenticationFilter jwtAuthenticationFilter,
                                                  RestAuthExceptionHandler restAuthExceptionHandler)
            throws Exception {
        http
                // 无状态接口 + 令牌放在请求头，天然不受 CSRF 影响，关闭即可
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 不使用表单登录与 HTTP Basic，登录由 /api/user/login、/api/admin/login 自行处理
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> registry
                        // 跨域预检请求不含令牌，必须放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 后台接口：必须是管理员角色（APP 用户令牌访问返回 403）
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余接口（购物车、地址、订单等）必须登录
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthExceptionHandler)
                        .accessDeniedHandler(restAuthExceptionHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 跨域配置：来源、方法、请求头全部显式白名单，不使用通配符。
     *
     * <p>这里同时承担了原先 WebMvcConfigurer#addCorsMappings 的职责 —— 由 Spring Security
     * 统一处理 CORS，避免两处同时写响应头导致浏览器报「多个 Access-Control-Allow-Origin」。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // allowCredentials=true 时不能用 "*"，必须用 originPatterns 显式声明
        configuration.setAllowedOriginPatterns(appProperties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
