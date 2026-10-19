package cn.edu.bookstore.common.web;

import cn.edu.bookstore.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求 ID 过滤器：为每一次请求生成 requestId 并写入 MDC 与响应头。
 *
 * <p>作用：
 * <ul>
 *     <li>日志可通过 requestId 串联同一次请求；</li>
 *     <li>APP 端报错时把响应头 X-Request-Id 反馈给开发者即可定位；</li>
 *     <li>若上游（如 Nginx）已传递 X-Request-Id，则直接复用。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 请求头名称 */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestContext.generate();
        }
        RequestContext.setRequestId(requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清理：Tomcat 线程复用会导致 requestId 串号
            RequestContext.clear();
        }
    }
}
