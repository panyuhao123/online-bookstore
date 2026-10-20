package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端评价汇总（列表页顶部「汇总条」数据源）。
 *
 * <p>平均分只统计「正常」评价：被隐藏的违规评价若还参与平均分，
 * 运营隐藏它之后列表页的分数不会变化，看起来像没生效。
 *
 * @author 毕设项目组
 */
@Data
public class AdminReviewSummaryVO {

    /** 评价总数（含已隐藏） */
    private Long total;

    /** 正常展示的评价数 */
    private Long visible;

    /** 已隐藏的评价数 */
    private Long hidden;

    /** 平均分（仅正常评价）；无评价时为 null，前端显示「—」而不是 0 分 */
    private BigDecimal avgRating;
}
