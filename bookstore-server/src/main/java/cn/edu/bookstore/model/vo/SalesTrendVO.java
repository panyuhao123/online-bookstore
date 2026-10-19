package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单日销售趋势数据点（后台看板折线图用）。
 *
 * <p>日期用字符串（yyyy-MM-dd）而不是 Date 类型：
 * 图表 X 轴只需要展示，前端不必再做时区转换，避免出现「差一天」的显示问题。
 *
 * @author 毕设项目组
 */
@Data
public class SalesTrendVO {

    /** 日期，格式 yyyy-MM-dd */
    private String date;

    /** 当日有效订单数（已支付及之后状态） */
    private Long orderCount;

    /** 当日销售额 */
    private BigDecimal amount;
}
