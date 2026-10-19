package cn.edu.bookstore.common.log;

import cn.edu.bookstore.common.web.LoginUserContext;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.entity.OperationLog;
import cn.edu.bookstore.mapper.OperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作日志切面：拦截标注了 {@link OpLog} 的方法，把「谁、什么时候、做了什么、结果如何」落库。
 *
 * <p>四个必须做对的细节：
 * <ol>
 *     <li><b>绝不因记日志而让业务失败</b>：整个落库过程包在 try/catch 里。
 *         日志是附属品，不能反过来影响主流程 —— 这是切面最容易出事的地方；</li>
 *     <li><b>参数脱敏</b>：请求体里可能含 password / token 等字段，
 *         序列化后统一用正则把它们替换成 ***，避免把凭据写进数据库；</li>
 *     <li><b>跳过不可序列化的参数</b>：文件上传参数（MultipartFile）、
 *         Servlet 请求响应对象序列化时会抛异常或产生超大字符串，直接过滤掉；</li>
 *     <li><b>长度截断</b>：参数可能很长，按字段长度截断，防止插入报错。</li>
 * </ol>
 *
 * <p>关于失败也记录：审计日志的价值恰恰在于「谁尝试做了什么但失败了」。
 * 切面捕获异常后先落一条 success=0 的记录，再把异常原样抛出，不改变原有异常行为。
 *
 * @author 毕设项目组
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class OpLogAspect {

    /** 参数列长度上限，与 operation_log.params 的 VARCHAR(2000) 保持一致 */
    private static final int PARAMS_MAX_LENGTH = 1900;

    /** 错误信息列长度上限 */
    private static final int ERROR_MAX_LENGTH = 400;

    /** 敏感字段脱敏：匹配 JSON 里的 "password":"xxx" 这类键值对 */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(\"(?i)(password|pwd|secret|token|accessKey|secretKey|authorization)\"\\s*:\\s*\")[^\"]*(\")");

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    /** 只拦截显式标注了 @OpLog 的方法：避免把所有接口都记一遍，日志表被无关请求撑爆 */
    @Pointcut("@annotation(cn.edu.bookstore.common.log.OpLog)")
    public void opLogPointcut() {
    }

    @Around("opLogPointcut() && @annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                persist(joinPoint, opLog, System.currentTimeMillis() - start, error);
            } catch (Exception e) {
                // 记日志失败绝不能影响业务：只打一条告警，继续把原结果返回给调用方
                log.warn("操作日志写入失败（不影响业务）module={} action={}：{}",
                        opLog.module(), opLog.action(), e.getMessage());
            }
        }
    }

    /** 组装并写入日志记录 */
    private void persist(ProceedingJoinPoint joinPoint, OpLog opLog, long costMs, Throwable error) {
        OperationLog entity = new OperationLog();
        entity.setModule(opLog.module());
        entity.setAction(opLog.action());

        LoginUserContext context = LoginUserHolder.get();
        if (context != null) {
            entity.setAdminId(context.getUserId());
            entity.setAdminName(context.getUsername());
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setHttpMethod(request.getMethod());
            entity.setUri(request.getRequestURI());
            entity.setIp(resolveClientIp(request));
        }

        entity.setParams(serializeArgs(joinPoint.getArgs()));
        entity.setDurationMs(costMs);
        entity.setSuccess(error == null ? 1 : 0);
        if (error != null) {
            entity.setErrorMsg(truncate(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(), ERROR_MAX_LENGTH));
        }

        operationLogMapper.insert(entity);
    }

    /**
     * 序列化方法参数为 JSON 字符串。
     *
     * <p>过滤掉文件、Servlet 对象等不可序列化的参数；
     * 若某个参数无法序列化，就用类型名占位，不让它影响整条日志。
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> serializable = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof MultipartFile || arg instanceof HttpServletRequest
                    || arg instanceof jakarta.servlet.http.HttpServletResponse
                    || arg instanceof jakarta.servlet.http.HttpSession) {
                // 文件与 Servlet 对象不记录内容，只留类型名，便于知道「这是个上传接口」
                serializable.add("<" + arg.getClass().getSimpleName() + ">");
                continue;
            }
            if (arg instanceof Object[] nested) {
                // Controller 很少直接收数组，这里做一次浅层处理
                serializable.add(nested);
                continue;
            }
            serializable.add(arg);
        }
        if (serializable.isEmpty()) {
            return null;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(serializable);
        } catch (Exception e) {
            json = "<参数序列化失败：" + e.getClass().getSimpleName() + ">";
        }
        return truncate(SENSITIVE_PATTERN.matcher(json).replaceAll("$1***$2"), PARAMS_MAX_LENGTH);
    }

    /** 取客户端真实 IP：优先 X-Forwarded-For（经过 Nginx 等代理时），否则用 remoteAddr */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            // X-Forwarded-For 可能是「客户端IP, 代理1IP, 代理2IP」，第一个才是真实客户端
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(已截断)";
    }
}
