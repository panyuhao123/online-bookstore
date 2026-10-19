package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端操作日志实体，对应表 operation_log。
 *
 * <p>由 {@code OpLogAspect} 切面在管理端写操作执行前后自动写入，业务代码无需手动埋点。
 *
 * @author 毕设项目组
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员 ID（不可变标识） */
    private Long adminId;

    /**
     * 操作人名称快照（当前写入的是管理员真实姓名）。
     *
     * <p>审计日志的标准做法是「ID + 名称快照」双写：
     * ID 用于精确关联，名称快照保证历史记录可读 ——
     * 即使管理员之后改名或离职，日志里仍能看到操作当时是谁。
     * 本字段不存登录账号，需要账号时用 adminId 关联 admin 表查询。
     */
    private String adminName;

    /** 业务模块，如「图书管理」 */
    private String module;

    /** 操作动作，如「新增图书」 */
    private String action;

    private String httpMethod;

    private String uri;

    /** 请求参数（已脱敏 + 截断） */
    private String params;

    private String ip;

    private Long durationMs;

    /** 是否成功：1-成功 0-失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
