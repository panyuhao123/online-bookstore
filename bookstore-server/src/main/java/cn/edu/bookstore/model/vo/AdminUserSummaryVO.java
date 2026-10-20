package cn.edu.bookstore.model.vo;

import lombok.Data;

/**
 * 管理端用户汇总（列表页顶部「汇总条」数据源）。
 *
 * @author 毕设项目组
 */
@Data
public class AdminUserSummaryVO {

    /** 注册用户总数 */
    private Long total;

    /** 正常状态用户数 */
    private Long active;

    /** 已禁用用户数 */
    private Long disabled;

    /** 本月新增注册数（按 create_time 落在本月第一天及之后计） */
    private Long newThisMonth;
}
