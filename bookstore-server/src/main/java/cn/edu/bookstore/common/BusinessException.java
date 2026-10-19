package cn.edu.bookstore.common;

import lombok.Getter;

/**
 * 业务异常：唯一允许在 Service 层主动抛出的异常类型。
 *
 * <p>用法：{@code throw new BusinessException(ErrorCode.BIZ_ERROR, "库存不足，仅剩 2 件");}
 * 由全局异常处理器转换为统一 Result，不会向客户端暴露堆栈。
 *
 * @author 毕设项目组
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码枚举 */
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
