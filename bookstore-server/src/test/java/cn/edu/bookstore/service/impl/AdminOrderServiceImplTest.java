package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.AdminOrderQuery;
import cn.edu.bookstore.model.vo.BatchShipResultVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminOrderServiceImpl} 单元测试（批量发货 / 关键词检索 / 日期区间）。
 *
 * <p>三个被测点都对应「页码正确但业务错」的高风险位置：
 * <ul>
 *     <li><b>批量发货</b>：混入已发货或已取消的单时，不能整体失败，也不能把状态改错；</li>
 *     <li><b>关键词</b>：手机号必须通过「先查用户 ID 再 IN 过滤」实现，
 *         而不是把 keyword 拼进 SQL 文本；</li>
 *     <li><b>日期区间</b>：起晚于止要报错，不能静默交换。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminOrderServiceImpl —— 批量发货与筛选条件")
class AdminOrderServiceImplTest {

    @Mock private OrdersMapper ordersMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private AdminOrderServiceImpl orderService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(Orders.class, User.class);
    }

    private Orders order(Long id, String orderNo, Integer status, Long userId) {
        Orders o = new Orders();
        o.setId(id);
        o.setOrderNo(orderNo);
        o.setStatus(status);
        o.setUserId(userId);
        o.setCreateTime(LocalDateTime.of(2026, 10, 20, 10, 0));
        return o;
    }

    @Test
    @DisplayName("批量发货：只发已支付的单，其余记录跳过原因且不影响成功笔数")
    void batchShipOnlyShipsPaidOrders() {
        when(ordersMapper.selectBatchIds(any())).thenReturn(List.of(
                order(1L, "NO1", OrderStatus.PAID.getCode(), 8L),
                order(2L, "NO2", OrderStatus.SHIPPED.getCode(), 8L),
                order(3L, "NO3", OrderStatus.CANCELLED.getCode(), 8L)));
        // 条件更新：已支付那单 affected=1，其余 affected=0（状态条件不满足）
        when(ordersMapper.update(isNull(), any())).thenReturn(1, 0, 0);

        BatchShipResultVO result = orderService.batchShip(List.of(1L, 2L, 3L));

        assertEquals(1, result.getShipped(), "只应发出已支付的一单");
        assertEquals(2, result.getSkipped().size(), "两单未发货都要给出原因");
        assertTrue(result.getSkipped().stream().anyMatch(s -> s.getOrderNo().equals("NO2")));
        assertTrue(result.getSkipped().stream()
                        .allMatch(s -> s.getReason().contains("仅已支付可发货")),
                "跳过原因必须写清为什么没发");
    }

    @Test
    @DisplayName("批量发货：订单不存在时记为跳过，而不是抛异常中断整批")
    void batchShipRecordsMissingOrder() {
        when(ordersMapper.selectBatchIds(any())).thenReturn(List.of(
                order(1L, "NO1", OrderStatus.PAID.getCode(), 8L)));
        when(ordersMapper.update(isNull(), any())).thenReturn(1);

        BatchShipResultVO result = orderService.batchShip(List.of(1L, 99L));

        assertEquals(1, result.getShipped());
        assertEquals(1, result.getSkipped().size());
        assertEquals("订单不存在", result.getSkipped().get(0).getReason());
    }

    @Test
    @DisplayName("批量发货：空入参直接拒绝，不打到数据库")
    void batchShipRejectsEmptyIds() {
        assertThrows(BusinessException.class, () -> orderService.batchShip(List.of()));
        verify(ordersMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("关键词检索：手机号走 user_id IN，而不是拼进 SQL 文本")
    void keywordSearchesPhoneViaUserIdIn() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(8L, "13800138000")));
        when(ordersMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        AdminOrderQuery query = new AdminOrderQuery();
        query.setKeyword("13800138000");
        orderService.page(query);

        ArgumentCaptor<Wrapper<Orders>> captor = wrapperCaptor();
        verify(ordersMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("order_no LIKE"), "应同时匹配订单号，实际 SQL：" + sql);
        assertTrue(sql.contains("user_id IN"), "手机号应转成 user_id IN 条件，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("关键词无手机号命中时只按订单号匹配，不生成空的 IN ()")
    void keywordWithoutPhoneMatchOnlyMatchesOrderNo() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(ordersMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        AdminOrderQuery query = new AdminOrderQuery();
        query.setKeyword("NO2026");
        orderService.page(query);

        ArgumentCaptor<Wrapper<Orders>> captor = wrapperCaptor();
        verify(ordersMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("order_no LIKE"));
        assertTrue(!sql.contains("user_id IN"), "没有命中用户时不应出现 IN 条件，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("日期区间：起始晚于结束直接 400，不静默交换")
    void reversedDateRangeRejected() {
        AdminOrderQuery query = new AdminOrderQuery();
        query.setCreateTimeStart(LocalDate.of(2026, 10, 20));
        query.setCreateTimeEnd(LocalDate.of(2026, 10, 1));

        assertThrows(BusinessException.class, () -> orderService.page(query));
        verify(ordersMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("日期区间闭合到天：结束日按 23:59:59 比较，避免单日查询为 0")
    void dateRangeClosedToEndOfDay() {
        when(ordersMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        AdminOrderQuery query = new AdminOrderQuery();
        query.setCreateTimeStart(LocalDate.of(2026, 10, 20));
        query.setCreateTimeEnd(LocalDate.of(2026, 10, 20));
        PageVO<?> result = orderService.page(query);

        ArgumentCaptor<Wrapper<Orders>> captor = wrapperCaptor();
        verify(ordersMapper).selectPage(any(), captor.capture());
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
        String sql = wrapper.getSqlSegment();
        String params = wrapper.getParamNameValuePairs().values().toString();
        assertTrue(sql.contains("create_time >= ") || sql.contains("create_time >="),
                "应生成起始时间条件，实际 SQL：" + sql);
        assertTrue(params.contains("2026-10-20T00:00"), "起始应闭合到当天 0 点，实际：" + params + " / SQL=" + sql);
        assertTrue(params.contains("2026-10-20T23:59:59.999999999"),
                "结束应闭合到当天最后一刻，实际：" + params + " / SQL=" + sql);
        assertEquals(0L, result.getTotal());
    }

    private User user(Long id, String phone) {
        User u = new User();
        u.setId(id);
        u.setPhone(phone);
        return u;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Wrapper<Orders>> wrapperCaptor() {
        return (ArgumentCaptor<Wrapper<Orders>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
    }
}
