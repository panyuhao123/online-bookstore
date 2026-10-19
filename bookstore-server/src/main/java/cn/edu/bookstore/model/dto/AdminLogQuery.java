package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端操作日志查询条件。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminLogQuery extends BasePageQuery {

    /** 业务模块，模糊匹配 */
    private String module;

    /** 操作人账号，模糊匹配 */
    private String adminName;

    /** 结果筛选：1-仅成功 0-仅失败，为空表示全部 */
    private Integer success;
}
