package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.vo.OverviewVO;
import cn.edu.bookstore.model.vo.StatusCountVO;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * {@link AdminDashboardServiceImpl} 单元测试（状态分布）。
 *
 * <p>测的是「分布是否定长」：工作台的状态计数条与订单页的状态胶囊都直接按
 * 1~5 的顺序渲染，如果后端只返回「有订单的状态」，前端就会出现
 * 「待支付不见了」「今天已取消 0 单却整行消失」这类静默缺口 —— 不报错，但读起来像 bug。
 * 因此断言重点不是「数字对不对」，而是<b>五个状态一个都不能少、顺序必须是枚举顺序</b>。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminDashboardServiceImpl —— 订单状态分布")
class AdminDashboardServiceImplTest {

    @Mock private BookMapper bookMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private UserMapper userMapper;
    @Mock private OrdersMapper ordersMapper;

    @InjectMocks private AdminDashboardServiceImpl dashboardService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(Book.class, Orders.class);
    }

    @Test
    @DisplayName("数据库只返回部分状态时，缺失状态补 0 且顺序固定为 1~5")
    void statusCountsFillMissingStatus() {
        when(ordersMapper.selectStatusCounts()).thenReturn(List.of(
                StatusCountVO.of(4, 12L),
                StatusCountVO.of(2, 3L)));

        OverviewVO vo = dashboardService.overview();

        List<StatusCountVO> counts = vo.getStatusCounts();
        assertEquals(5, counts.size(), "五种状态必须都返回");
        assertEquals(List.of(1, 2, 3, 4, 5), counts.stream().map(StatusCountVO::getStatus).toList(),
                "顺序必须与 OrderStatus 枚举一致，前端才能直接按序渲染");
        assertEquals(0L, counts.get(0).getOrderCount(), "待支付无订单应为 0 而不是 null");
        assertEquals(3L, counts.get(1).getOrderCount());
        assertEquals(0L, counts.get(2).getOrderCount(), "已发货无订单应为 0");
        assertEquals(12L, counts.get(3).getOrderCount());
        assertEquals(0L, counts.get(4).getOrderCount(), "已取消无订单应为 0");
    }

    @Test
    @DisplayName("一条订单都没有时，返回五行全 0 而不是空列表")
    void statusCountsAllZeroWhenNoOrders() {
        when(ordersMapper.selectStatusCounts()).thenReturn(List.of());

        OverviewVO vo = dashboardService.overview();

        assertEquals(5, vo.getStatusCounts().size(), "空库也要给出定长分布");
        assertEquals(5, vo.getStatusCounts().stream().filter(i -> i.getOrderCount() == 0L).count(),
                "全部状态都应为 0");
    }

    @Test
    @DisplayName("状态计数的 null 值兜底为 0，不把 null 透给前端")
    void statusCountsNullCountBecomesZero() {
        when(ordersMapper.selectStatusCounts()).thenReturn(List.of(StatusCountVO.of(1, null)));

        OverviewVO vo = dashboardService.overview();

        assertEquals(0L, vo.getStatusCounts().get(0).getOrderCount(), "null 单量应兜底为 0");
    }
}
