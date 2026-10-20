package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 后台首页数据总览。
 *
 * <p>只统计后台首页卡片需要的少量聚合值，因此用 COUNT/SUM 一次查完，
 * 不做全表扫描也不返回明细。
 *
 * @author 毕设项目组
 */
@Data
public class OverviewVO {

    /** 图书总数（含下架） */
    private Long bookCount;

    /** 上架图书数 */
    private Long onSaleBookCount;

    /** 库存告急（库存 <= 5）的图书数，提醒管理员补货 */
    private Long lowStockBookCount;

    /** 分类总数 */
    private Long categoryCount;

    /** 注册用户总数 */
    private Long userCount;

    /** 订单总数（不含已取消） */
    private Long orderCount;

    /** 待发货订单数（已支付但未发货） */
    private Long pendingShipCount;

    /** 已收款金额：已支付及之后状态的订单金额合计 */
    private BigDecimal salesAmount;

    /**
     * 订单状态分布：固定按 1~5 顺序返回，无订单的状态也占一行（count=0）。
     *
     * <p>前端用这一份数据同时渲染工作台的状态计数条与订单页的状态胶囊筛选，
     * 避免两处各调一次接口、也避免两处的口径分叉。
     */
    private List<StatusCountVO> statusCounts;
}
