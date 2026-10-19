package cn.edu.bookstore.common.web;

import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 的认证/授权失败统一输出。
 *
 * <p>同时实现 {@link AuthenticationEntryPoint}（未认证 → 401）与
 * {@link AccessDeniedHandler}（已认证但无权限 → 403），两者共用同一套 JSON 结构，
 * 保证「Security 层抛出的错误」与「业务层抛出的错误」对 APP 端而言完全一致。
 *
 * <p>关键约定：HTTP 状态码统一返回 200，业务成败由 body.code 表达
 * （与 GlobalExceptionHandler 一致），APP 端只需解析 body.code，不必区分 HTTP 状态码。
 *
 * @author 毕设项目组
 */
@Component
@RequiredArgsConstructor
public class RestAuthExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 未认证：令牌缺失、过期、签名非法，或账号已被禁用 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED, "未登录或登录已过期，请重新登录");
    }

    /** 已认证但权限不足：例如 APP 用户令牌访问 /api/admin/** */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN, "无权限访问该资源");
    }

    /** 供 JWT 过滤器复用：令牌有效但账号被禁用等场景需要更精确的提示 */
    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.fail(errorCode, message);
        response.getOutputStream().write(
                objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8));
    }
}
