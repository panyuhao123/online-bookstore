package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理端订单查询条件：订单号 / 状态 / 下单日期区间。
 *
 * <p>日期用 {@link LocalDate} 而不是时间戳接收：前端筛选器给的是「哪一天到哪一天」，
 * 由服务端负责把「结束日期」换算成当天 23:59:59，
 * 否则前端要自己拼时分秒，很容易写成「结束日 00:00:00」而把当天订单全漏掉。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderQuery extends BasePageQuery {

    /** 订单号，支持右模糊匹配 */
    private String orderNo;

    /**
     * 统一关键词：同时匹配订单号与下单用户手机号。
     *
     * <p>为什么不在前端拆成两个输入框：运营手上拿到的往往是「一个串」——
     * 可能是订单号也可能是买家手机号，让系统自己判断比让用户先选类型更省一次点击。
     *
     * <p>手机号匹配走「先查用户 ID、再用 IN 过滤订单」两步，而不是把 keyword 拼进 SQL 字符串：
     * 拼接既可能被注入，也会让 user 表无法命中索引。
     */
    private String keyword;

    /** 订单状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消；为空表示不筛选 */
    private Integer status;

    /** 下单用户 ID，为空表示不筛选 */
    private Long userId;

    /** 下单起始日期（含当天 00:00:00），格式 yyyy-MM-dd，为空表示不限 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createTimeStart;

    /** 下单结束日期（含当天 23:59:59），格式 yyyy-MM-dd，为空表示不限 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createTimeEnd;
}
