package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.model.vo.SalesTrendVO;
import cn.edu.bookstore.model.vo.UserStatVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单主表数据访问层。
 *
 * <p>除 MyBatis-Plus 提供的通用 CRUD 外，另提供后台需要的聚合查询：
 * 用一条 GROUP BY 完成「本页用户各自的订单数与消费额」，避免逐用户统计的 N+1。
 *
 * @author 毕设项目组
 */
public interface OrdersMapper extends BaseMapper<Orders> {

    /**
     * 按用户维度聚合：累计订单数与消费金额。
     *
     * <p>已取消订单不计入：后台关注真实成交，取消单会干扰统计结果。
     *
     * @param userIds 需要统计的用户 ID（通常来自用户列表当前页）
     * @return 每个用户一行；无订单的用户不在结果集中，调用方需用默认值兜底
     */
    @Select("<script>"
            + "SELECT user_id AS userId, COUNT(*) AS orderCount, "
            + "IFNULL(SUM(total_amount), 0) AS totalAmount "
            + "FROM orders "
            + "WHERE status &lt;&gt; 5 AND user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY user_id"
            + "</script>")
    List<UserStatVO> selectUserStats(@Param("userIds") List<Long> userIds);

    /**
     * 累计销售金额（已支付及之后状态的订单合计）。
     *
     * <p>待支付与已取消不计入 —— 钱没到账就不算销售额。
     *
     * @return 金额合计，无记录时返回 0（IFNULL 保证不会是 null）
     */
    @Select("SELECT IFNULL(SUM(total_amount), 0) FROM orders WHERE status IN (2, 3, 4)")
    BigDecimal sumSalesAmount();

    /**
     * 按天聚合销售趋势（后台看板折线图）。
     *
     * <p>关键点：
     * <ul>
     *     <li>用 {@code DATE_FORMAT} 把时间截断到天并直接在数据库侧分组，
     *         而不是把订单全查出来在 Java 里 stream 分组 —— 后者在数据量增长后会拖垮接口；</li>
     *     <li>只统计 {@code status IN (2,3,4)}（已支付/已发货/已完成），
     *         待支付与已取消都不算真实成交，避免趋势虚高；</li>
     *     <li>没有订单的日期不会出现在结果里，由 Service 补齐为 0，
     *         否则折线图会因为缺点而把 X 轴压扁、趋势失真。</li>
     * </ul>
     *
     * @param start 统计起始时间（含），建议传「今天 - (天数-1) 天的 00:00:00」
     * @return 按日期升序排列的每日汇总，可能少于请求的天数
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date, "
            + "COUNT(*) AS orderCount, "
            + "IFNULL(SUM(total_amount), 0) AS amount "
            + "FROM orders "
            + "WHERE status IN (2, 3, 4) AND create_time >= #{start} "
            + "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') "
            + "ORDER BY date")
    List<SalesTrendVO> selectDailyTrend(@Param("start") LocalDateTime start);
}
