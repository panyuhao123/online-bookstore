package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.vo.OverviewVO;
import cn.edu.bookstore.service.AdminDashboardService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端首页统计实现。
 *
 * <p>所有指标都用 COUNT / SUM 在数据库侧完成，不在内存中遍历全表 ——
 * 演示数据量虽小，但后台统计接口最容易被写成「先查全表再 stream 过滤」，
 * 那会在数据量增长后直接拖垮接口，这里保持一次 SQL 一个指标。
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** 库存告急阈值：低于该值（含）的商品需要提醒管理员补货 */
    private static final int LOW_STOCK_THRESHOLD = 5;

    /** 上架状态 */
    private static final int STATUS_ON_SALE = 1;

    private final BookMapper bookMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final OrdersMapper ordersMapper;

    @Override
    public OverviewVO overview() {
        OverviewVO vo = new OverviewVO();
        vo.setBookCount(nvl(bookMapper.selectCount(null)));
        vo.setOnSaleBookCount(nvl(bookMapper.selectCount(
                new LambdaQueryWrapper<Book>().eq(Book::getStatus, STATUS_ON_SALE))));
        vo.setLowStockBookCount(nvl(bookMapper.selectCount(
                new LambdaQueryWrapper<Book>()
                        .eq(Book::getStatus, STATUS_ON_SALE)
                        .le(Book::getStock, LOW_STOCK_THRESHOLD))));
        vo.setCategoryCount(nvl(categoryMapper.selectCount(null)));
        vo.setUserCount(nvl(userMapper.selectCount(null)));
        // 已取消订单不计入总数：后台关注的是真实成交单量
        vo.setOrderCount(nvl(ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>().ne(Orders::getStatus, OrderStatus.CANCELLED.getCode()))));
        vo.setPendingShipCount(nvl(ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, OrderStatus.PAID.getCode()))));
        vo.setSalesAmount(ordersMapper.sumSalesAmount());
        return vo;
    }

    /** MyBatis-Plus 的 selectCount 理论上不会返回 null，这里兜底为 0 避免前端拿到 null */
    private Long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
