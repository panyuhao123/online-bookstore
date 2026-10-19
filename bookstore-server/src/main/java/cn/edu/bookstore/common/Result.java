package cn.edu.bookstore.common;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一接口返回封装。
 *
 * <p>APP 端与后台统一按 {@code {code, message, data, timestamp, requestId}} 解析：
 * <ul>
 *     <li>code = 200 表示成功，其余均为失败；</li>
 *     <li>requestId 贯穿日志，便于联调时定位一次请求。</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 * @author 毕设项目组
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    /** 业务状态码，200 表示成功 */
    private int code;

    /** 提示信息，失败时为可读文案 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 响应时间戳（毫秒） */
    private long timestamp = System.currentTimeMillis();

    /** 链路追踪 ID，用于排查问题 */
    private String requestId;

    private Result() {
    }

    /** 成功，无数据 */
    public static <T> Result<T> success() {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /** 失败，使用预定义错误码 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return build(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 失败，使用预定义错误码 + 自定义文案（如「库存不足，仅剩 2 件」） */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return build(errorCode.getCode(), StrUtil.blankToDefault(message, errorCode.getMessage()), null);
    }

    /** 失败，自定义错误码与文案 */
    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(int code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setRequestId(RequestContext.getRequestId());
        return result;
    }
}
