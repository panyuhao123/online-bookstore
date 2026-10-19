package cn.edu.bookstore.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 请求上下文：持有贯穿一次请求的 requestId。
 *
 * <p>requestId 由 {@link cn.edu.bookstore.common.web.RequestIdFilter} 写入 MDC，
 * 全局异常处理器回写给前端，日志中也会打印，便于真机联调时按 ID 定位问题。
 *
 * @author 毕设项目组
 */
public final class RequestContext {

    /** MDC 中的 key */
    public static final String REQUEST_ID_KEY = "requestId";

    private RequestContext() {
    }

    /** 生成一个新的 requestId（16 位十六进制，短小够用） */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 获取当前请求的 requestId，无上下文时返回 "no-request-id" */
    public static String getRequestId() {
        String id = MDC.get(REQUEST_ID_KEY);
        return id == null ? "no-request-id" : id;
    }

    /** 写入当前线程上下文 */
    public static void setRequestId(String requestId) {
        MDC.put(REQUEST_ID_KEY, requestId);
    }

    /** 清理当前线程上下文，避免线程复用导致脏数据 */
    public static void clear() {
        MDC.remove(REQUEST_ID_KEY);
    }
}
