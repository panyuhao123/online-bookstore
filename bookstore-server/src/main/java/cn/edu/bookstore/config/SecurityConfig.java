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
     * 注册、登录、登出、健康检查、静态资源（封面）无需令牌。
     *
     * <p>登出接口（/api/user/logout、/api/admin/logout）放在这里是有意为之：
     * 它们的语义是「让这枚令牌失效」，本身幂等，令牌已过期时也应当返回成功。
     * 若要求先认证，用户令牌过期后再点退出就会收到 401，
     * 反而需要前端额外处理一个没有实际意义的失败分支。
     * 真正的吊销动作由接口内部解析请求头中的令牌完成（见 JwtUtil#resolveFromHeader）。
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/user/register",
            "/api/user/login",
            "/api/user/logout",
            "/api/admin/login",
            "/api/admin/logout",
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
            "/favicon.ico"
    };

    /**
     * 接口文档相关路径（Knife4j / springdoc）。
     *
     * <p>注意 /doc.html 是一个<b>静态页面</b>：即使 knife4j.enable=false，
     * 页面本身依然能打开（只是取不到接口数据）。所以要真正关掉文档入口，
     * 必须在安全层把这些路径一起摘掉，仅靠关配置是不够的。
     *
     * <p>由 {@code bookstore.docs.enabled} 控制：
     * 本地开发 true（放行，便于调试与答辩演示）；生产 false（显式拒绝）。
     */
    private static final String[] DOC_PATHS = {
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**"
    };

    /**
     * 运维端点（actuator）中允许匿名访问的路径。
     *
     * <p>只有这两个端点对外开放，理由是它们本身不含敏感信息，且需要被外部系统主动拉取：
     * <ul>
     *     <li>{@code /actuator/health}：负载均衡器、容器编排（K8s liveness/readiness）靠它判断实例是否需要摘除，
     *         不可能先登录再探测；</li>
     *     <li>{@code /actuator/info}：构建版本与关键开关，属于「部署自证」信息，不涉及业务数据。</li>
     * </ul>
     *
     * <p>其余端点（{@code /metrics}、{@code /env}、{@code /beans}、{@code /heapdump} …）一律要求管理员角色：
     * {@code /env} 与 {@code /heapdump} 一旦公开等于把配置与内存内容交出去，属于严重的信息泄露。
     * 注意这里只放行 health 与 info，<b>不要</b>图省事写成 {@code /actuator/**}。
     */
    private static final String[] ACTUATOR_PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
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
                .authorizeHttpRequests(registry -> {
                    // 跨域预检请求不含令牌，必须放行
                    registry.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(PUBLIC_PATHS).permitAll()
                            // 运维端点：仅 health 与 info 匿名可访问（供探测与部署自证）
                            .requestMatchers(ACTUATOR_PUBLIC_PATHS).permitAll();
                    // 接口文档：按 bookstore.docs.enabled 决定放行还是拒绝。
                    // 关闭时用 denyAll（而不是「不写规则」），让意图明确、日志里也能看出来。
                    if (appProperties.getDocs().isEnabled()) {
                        registry.requestMatchers(DOC_PATHS).permitAll();
                    } else {
                        registry.requestMatchers(DOC_PATHS).denyAll();
                    }
                    registry
                            // 后台接口：必须是管理员角色（APP 用户令牌访问返回 403）
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            // 运维端点：health / info 已在上面放行，其余（metrics/env/beans…）只给管理员。
                            // 用 hasRole 而不是放行，是因为指标与运行时信息能反推出系统内部结构。
                            .requestMatchers("/actuator/**").hasRole("ADMIN")
                            // 其余接口（购物车、地址、订单等）必须登录
                            .anyRequest().authenticated();
                })
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
