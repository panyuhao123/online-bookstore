package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.OperationLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志展示对象。
 *
 * <p>不直接返回实体：一是保持与项目其余接口一致（Entity -> VO 再返回），
 * 二是时间字段可以用 @JsonFormat 统一格式，避免前端还要处理时间格式差异。
 *
 * @author 毕设项目组
 */
@Data
public class OperationLogVO {

    private Long id;

    private Long adminId;

    private String adminName;

    private String module;

    private String action;

    private String httpMethod;

    private String uri;

    private String params;

    private String ip;

    private Long durationMs;

    /** 是否成功：1-成功 0-失败 */
    private Integer success;

    private String errorMsg;

    private LocalDateTime createTime;

    public static OperationLogVO from(OperationLog entity) {
        if (entity == null) {
            return null;
        }
        OperationLogVO vo = new OperationLogVO();
        vo.setId(entity.getId());
        vo.setAdminId(entity.getAdminId());
        vo.setAdminName(entity.getAdminName());
        vo.setModule(entity.getModule());
        vo.setAction(entity.getAction());
        vo.setHttpMethod(entity.getHttpMethod());
        vo.setUri(entity.getUri());
        vo.setParams(entity.getParams());
        vo.setIp(entity.getIp());
        vo.setDurationMs(entity.getDurationMs());
        vo.setSuccess(entity.getSuccess());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
