package cn.edu.bookstore.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举（类型化错误体系）。
 *
 * <p>所有对外错误都必须通过本枚举定义，禁止在业务代码里写死错误码与错误文案，
 * 保证 APP 端拿到的错误结构统一、可解析。
 *
 * @author 毕设项目组
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /** 业务处理成功 */
    SUCCESS(200, "操作成功"),

    /** 请求参数缺失或格式不正确 */
    BAD_REQUEST(400, "请求参数有误"),

    /** 手机号或密码错误、令牌缺失/过期 */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 已登录但无访问权限（如普通用户访问管理员接口） */
    FORBIDDEN(403, "无权限访问该资源"),

    /** 查询的数据不存在 */
    NOT_FOUND(404, "数据不存在"),

    /** 数据冲突：手机号已注册、分类名重复等 */
    CONFLICT(409, "数据冲突，请检查后重试"),

    /** 业务规则不允许：库存不足、订单状态不合法等 */
    BIZ_ERROR(422, "业务处理失败"),

    /** 服务器内部异常，对外只暴露通用文案 + requestId，不返回堆栈 */
    INTERNAL_ERROR(500, "服务器繁忙，请稍后重试");

    /** HTTP 风格错误码，与 Result.code 一致 */
    private final int code;

    /** 默认错误文案 */
    private final String message;
}
