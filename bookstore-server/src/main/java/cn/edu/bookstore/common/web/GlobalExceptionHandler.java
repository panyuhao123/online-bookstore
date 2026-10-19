package cn.edu.bookstore.common.web;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.RequestContext;
import cn.edu.bookstore.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>目标：客户端只看到规范化错误结构，绝不返回堆栈与内部细节；
 * 服务端记录完整堆栈（含 requestId），便于排查。
 *
 * <p>处理顺序：业务异常 → 参数校验异常 → 请求协议异常 → 兜底未知异常。
 *
 * @author 毕设项目组
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：直接返回对应错误码与文案，不打堆栈（属于预期内失败） */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 uri={} code={} msg={}", request.getRequestURI(),
                e.getErrorCode().getCode(), e.getMessage());
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    /** @Valid 校验 @RequestBody 失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败 uri={} msg={}", RequestContext.getRequestId(), msg);
        return Result.fail(ErrorCode.BAD_REQUEST, msg);
    }

    /** 表单/查询参数绑定失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.BAD_REQUEST, msg);
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "缺少参数：" + e.getParameterName());
    }

    /** 参数类型不匹配，如 /book/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "参数格式不正确：" + e.getName());
    }

    /** 请求体为空或 JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "请求体格式有误，请检查 JSON");
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "不支持的请求方法：" + e.getMethod());
    }

    /**
     * 上传文件超过 application.yml 中配置的 max-file-size。
     *
     * <p>该异常在参数解析阶段抛出，早于 Controller，无法在业务代码里拦截，
     * 必须在此转换为可读文案，否则会落到兜底处理器变成「服务器繁忙」。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("上传文件超限 uri={}", request.getRequestURI());
        return Result.fail(ErrorCode.BAD_REQUEST, "上传文件过大，请压缩后重试");
    }

    /**
     * 静态资源不存在：典型场景是浏览器访问根路径时自动请求 /favicon.ico。
     *
     * <p>这不是系统故障，但若不单独拦截就会落到下面的兜底处理器，
     * 打出整屏堆栈淹没真正的 500。此处返回标准 404，且不套用 /api 的统一返回体。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("静态资源不存在 path={}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /** 兜底：未知异常，记录完整堆栈，对外只给通用文案 + requestId */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={} requestId={}", request.getRequestURI(), RequestContext.getRequestId(), e);
        return Result.fail(ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getMessage() + "（requestId=" + RequestContext.getRequestId() + "）");
    }

    /** 格式化字段错误为中文提示 */
    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        return fieldError.getField() + "：" + (defaultMessage == null ? "不合法" : defaultMessage);
    }
}
