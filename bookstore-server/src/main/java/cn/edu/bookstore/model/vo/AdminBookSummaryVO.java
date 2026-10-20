package cn.edu.bookstore.model.vo;

import lombok.Data;

/**
 * 管理端图书汇总（列表页顶部「汇总条」数据源）。
 *
 * <p>为什么不复用 {@code OverviewVO.bookCount}：那个数字是「在售图书」，
 * 而图书管理页要回答的是「一共多少本、下架多少、有多少本快断货」——
 * 口径不同，混用会让页面上的两个数字互相矛盾。
 *
 * @author 毕设项目组
 */
@Data
public class AdminBookSummaryVO {

    /** 图书总数（含下架） */
    private Long total;

    /** 上架中数量 */
    private Long onSale;

    /** 已下架数量 */
    private Long offSale;

    /** 库存告急数量：仅统计上架且库存 ≤ 5 的图书（下架书断货不影响售卖） */
    private Long lowStock;

    /** 在库总册数（只算上架图书，用于「还剩多少货」） */
    private Long totalStock;
}
