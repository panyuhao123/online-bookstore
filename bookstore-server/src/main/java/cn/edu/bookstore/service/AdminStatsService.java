package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.SalesTrendVO;

import java.util.List;

/**
 * 管理端数据统计接口（后台看板）。
 *
 * @author 毕设项目组
 */
public interface AdminStatsService {

    /**
     * 销售趋势：近 N 天的每日有效订单数与销售额。
     *
     * <p>返回值会**补齐没有订单的日期为 0**，保证 X 轴连续，
     * 否则折线图会跳过空日期，把趋势画歪。
     *
     * @param days 统计天数，服务端收敛到 1~90
     * @return 按日期升序排列，长度恰好为 days
     */
    List<SalesTrendVO> salesTrend(int days);

    /**
     * 图书销量排行（按累计销量倒序）。
     *
     * <p>与用户端热门榜的区别：这里**包含已下架图书**，只看销量。
     * 因为后台关心的是「哪本书卖得好」，下架的书历史销量同样有分析价值。
     *
     * @param limit 需要的条数，服务端收敛到 1~50
     * @return 按销量倒序的图书列表
     */
    List<BookVO> topSellingBooks(int limit);
}
