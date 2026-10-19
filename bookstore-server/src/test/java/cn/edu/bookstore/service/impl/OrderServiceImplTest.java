package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.common.observability.BusinessMetrics;
import cn.edu.bookstore.entity.Address;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Cart;
import cn.edu.bookstore.entity.OrderItem;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.mapper.AddressMapper;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CartMapper;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.model.dto.OrderCreateRequest;
import cn.edu.bookstore.model.vo.OrderVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BookService;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrderServiceImpl} 单元测试 —— 本项目最核心的事务逻辑。
 *
 * <p>为什么值得单独写这么多用例：下单这条链路上叠了四类风险，任何一类失效都不会
 * 在「功能看起来正常」的冒烟测试里暴露：
 * <ul>
 *     <li><b>越权</b>：用别人的地址下单、看别人的订单；</li>
 *     <li><b>超卖</b>：并发下库存扣成负数 —— 靠带条件的 UPDATE 兜底，必须断言「影响行数为 0 必须抛异常」；</li>
 *     <li><b>金额与快照</b>：总价算错、明细没存书名/单价快照（后续改价会篡改历史订单）；</li>
 *     <li><b>状态机</b>：非法流转（未发货就确认收货、重复支付）必须被拦住。</li>
 * </ul>
 *
 * <p>覆盖层次是「业务分支」而非「SQL 语句」：Mapper 被 mock，所以这里验证的是
 * 「在给定的数据状态下，Service 做出什么决定、向 Mapper 发出什么指令」，
 * 真实 SQL 的正确性由端到端脚本（{@code tools/verify_review_pay_cart.py} 等）保证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderServiceImpl —— 下单 / 支付 / 收货 / 取消 / 查询")
class OrderServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;

    @Mock private OrdersMapper ordersMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private CartMapper cartMapper;
    @Mock private AddressMapper addressMapper;
    @Mock private BookMapper bookMapper;
    @Mock private BookService bookService;
    @Mock private ObjectProvider<CacheManager> cacheManagerProvider;
    @Mock private BusinessMetrics businessMetrics;

    @InjectMocks private OrderServiceImpl orderService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不起容器，lambda 条件构造器需要手工注册实体元数据（详见 MybatisPlusTestSupport）
        MybatisPlusTestSupport.initTableInfo(Address.class, Book.class, Cart.class, OrderItem.class, Orders.class);
    }

    /** 去掉反引号与多余空白后再断言 SQL 片段，免得为「列名加不加引号」这种实现细节写死断言 */
    private static String normalize(String sql) {
        return sql == null ? "" : sql.replace("`", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ 造数据

    private Address address(long id, long userId) {
        Address a = new Address();
        a.setId(id);
        a.setUserId(userId);
        a.setReceiver("张三");
        a.setPhone("13800138000");
        a.setProvince("广东省");
        a.setCity("深圳市");
        a.setDistrict("南山区");
        a.setDetail("科技园 1 号");
        return a;
    }

    private Book book(long id, String title, String price, int stock, int status) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setAuthor("作者");
        b.setCoverUrl("/static/cover/" + id + ".jpg");
        b.setPrice(new BigDecimal(price));
        b.setStock(stock);
        b.setStatus(status);
        b.setSales(0);
        return b;
    }

    private Cart cart(long id, long bookId, int quantity, int checked) {
        Cart c = new Cart();
        c.setId(id);
        c.setUserId(USER_ID);
        c.setBookId(bookId);
        c.setQuantity(quantity);
        c.setChecked(checked);
        return c;
    }

    private OrderCreateRequest request(Long addressId, List<Long> cartIds) {
        OrderCreateRequest r = new OrderCreateRequest();
        r.setAddressId(addressId);
        r.setCartIds(cartIds);
        return r;
    }

    /** 让 insert 回填自增主键 —— 真实 MyBatis-Plus 会做这件事，Service 依赖 orders.getId() 写明细 */
    private void stubOrderInsert() {
        when(ordersMapper.insert(any(Orders.class))).thenAnswer(inv -> {
            inv.getArgument(0, Orders.class).setId(9001L);
            return 1;
        });
    }

    // ================================================================== 下单

    @Nested
    @DisplayName("下单 create()")
    class Create {

        @Test
        @DisplayName("收货地址不存在 → 422，且不写入任何数据")
        void rejectsMissingAddress() {
            when(addressMapper.selectById(99L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(USER_ID, request(99L, List.of(1L))));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("收货地址不存在"));
            verify(ordersMapper, never()).insert(any(Orders.class));
        }

        @Test
        @DisplayName("使用他人收货地址 → 403（越权拦截）")
        void rejectsAddressOwnedByOthers() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, OTHER_USER_ID));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(USER_ID, request(1L, List.of(1L))));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("无权使用他人"));
            verify(ordersMapper, never()).insert(any(Orders.class));
        }

        @Test
        @DisplayName("购物车无待结算条目 → 422")
        void rejectsEmptyCart() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any())).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(USER_ID, request(1L, List.of(1L))));

            assertTrue(ex.getMessage().contains("请先选择要购买的商品"));
        }

        @Test
        @DisplayName("购物车含已下架图书 → 422（不能把下架商品卖出去）")
        void rejectsOffSaleBook() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 1L, 1, 1)));
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "下架书", "20.00", 10, 0)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(USER_ID, request(1L, List.of(11L))));

            assertTrue(ex.getMessage().contains("已下架"));
            verify(ordersMapper, never()).insert(any(Orders.class));
        }

        @Test
        @DisplayName("库存不足 → 422，错误信息带上书名与剩余库存")
        void rejectsInsufficientStock() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 1L, 5, 1)));
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "稀缺书", "20.00", 2, 1)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(USER_ID, request(1L, List.of(11L))));

            assertTrue(ex.getMessage().contains("稀缺书"));
            assertTrue(ex.getMessage().contains("仅剩 2 件"));
            verify(businessMetrics, never()).orderCreated();
        }

        @Test
        @DisplayName("正常下单：总金额求和正确，明细保存书名/封面/单价/小计快照")
        void computesTotalAmountAndItemSnapshots() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any()))
                    .thenReturn(List.of(cart(11L, 1L, 2, 1), cart(12L, 2L, 1, 1)));
            when(bookService.mapByIds(anyList())).thenReturn(Map.of(
                    1L, book(1L, "深入理解计算机系统", "10.00", 5, 1),
                    2L, book(2L, "算法导论", "25.50", 3, 1)));
            stubOrderInsert();
            when(bookMapper.update(isNull(), any())).thenReturn(1);

            OrderVO vo = orderService.create(USER_ID, request(1L, List.of(11L, 12L)));

            // 总金额 = 10.00×2 + 25.50×1 = 45.50
            ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
            verify(ordersMapper).insert(orderCaptor.capture());
            assertEquals(0, new BigDecimal("45.50").compareTo(orderCaptor.getValue().getTotalAmount()));
            assertEquals(OrderStatus.PENDING_PAY.getCode(), orderCaptor.getValue().getStatus());
            assertTrue(orderCaptor.getValue().getOrderNo().startsWith("BK"));

            // 地址快照：收件人 电话 省市区 详细地址，一行铺平
            String snapshot = orderCaptor.getValue().getAddressSnapshot();
            assertTrue(snapshot.contains("张三"));
            assertTrue(snapshot.contains("广东省深圳市南山区科技园 1 号"));

            // 明细快照：单价与书名在「下单这一刻」固化，之后管理端改价不影响历史订单
            ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
            verify(orderItemMapper, times(2)).insert(itemCaptor.capture());
            List<OrderItem> items = itemCaptor.getAllValues();
            assertEquals("深入理解计算机系统", items.get(0).getBookTitle());
            assertEquals(0, new BigDecimal("10.00").compareTo(items.get(0).getPrice()));
            assertEquals(0, new BigDecimal("20.00").compareTo(items.get(0).getSubtotal()));
            assertEquals("/static/cover/1.jpg", items.get(0).getBookCover());
            assertEquals(9001L, items.get(0).getOrderId());

            assertEquals(3, vo.getTotalQuantity());
            assertEquals(2, vo.getItems().size());
        }

        @Test
        @DisplayName("逐个商品执行带条件的库存扣减（stock>=n），并同步累加销量")
        void deductsStockOncePerItem() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 1L, 2, 1)));
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "书", "10.00", 5, 1)));
            stubOrderInsert();
            when(bookMapper.update(isNull(), any())).thenReturn(1);

            orderService.create(USER_ID, request(1L, List.of(11L)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaUpdateWrapper<Book>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(bookMapper).update(isNull(), wrapperCaptor.capture());
            // 一条 UPDATE 分两部分看：getSqlSegment() 是 WHERE 条件，getSqlSet() 是 SET 赋值
            String where = normalize(wrapperCaptor.getValue().getSqlSegment());
            String set = normalize(wrapperCaptor.getValue().getSqlSet());
            // 防超卖的关键：扣减必须带 stock >= n 的前置条件，且销量同步 +n
            assertTrue(where.contains("stock >= "), "缺少库存充足条件，存在超卖风险：" + where);
            assertTrue(set.contains("stock - 2"), "库存扣减量不对：" + set);
            assertTrue(set.contains("sales + 2"), "销量未同步累加：" + set);
        }

        @Test
        @DisplayName("条件更新影响 0 行（并发抢空库存）→ 抛异常触发回滚，绝不生成订单")
        void throwsWhenConditionalUpdateAffectsNoRow() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any()))
                    .thenReturn(List.of(cart(11L, 1L, 1, 1), cart(12L, 2L, 1, 1)));
            when(bookService.mapByIds(anyList())).thenReturn(Map.of(
                    1L, book(1L, "书A", "10.00", 5, 1),
                    2L, book(2L, "书B", "10.00", 5, 1)));
            stubOrderInsert();
            // 第一条扣减成功，第二条被并发抢空
            when(bookMapper.update(isNull(), any())).thenReturn(1, 0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(USER_ID, request(1L, List.of(11L, 12L))));

            assertTrue(ex.getMessage().contains("书B"));
            assertTrue(ex.getMessage().contains("请调整数量后重试"));
            // 事务回滚由 @Transactional 保证，这里断言「确实抛出了异常」——
            // 只要不吞异常，已写入的订单与已扣的库存就会被 Spring 一并撤销
            verify(businessMetrics, never()).orderCreated();
        }

        @Test
        @DisplayName("下单成功后删除已结算的购物车条目")
        void deletesOrderedCartEntries() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any()))
                    .thenReturn(List.of(cart(11L, 1L, 1, 1), cart(12L, 2L, 1, 1)));
            when(bookService.mapByIds(anyList())).thenReturn(Map.of(
                    1L, book(1L, "书A", "10.00", 5, 1),
                    2L, book(2L, "书B", "10.00", 5, 1)));
            stubOrderInsert();
            when(bookMapper.update(isNull(), any())).thenReturn(1);

            orderService.create(USER_ID, request(1L, List.of(11L, 12L)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(cartMapper).deleteBatchIds(captor.capture());
            assertEquals(2, captor.getValue().size());
            assertTrue(captor.getValue().containsAll(List.of(11L, 12L)));
        }

        @Test
        @DisplayName("未指定 cartIds 时按「已勾选」筛选（并限定在当前用户名下）")
        void fallsBackToCheckedEntriesWhenCartIdsAbsent() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 1L, 1, 1)));
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "书", "10.00", 5, 1)));
            stubOrderInsert();
            when(bookMapper.update(isNull(), any())).thenReturn(1);

            orderService.create(USER_ID, request(1L, null));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<Cart>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(cartMapper).selectList(captor.capture());
            String sql = normalize(captor.getValue().getSqlSegment());
            assertTrue(sql.contains("checked"), "未按勾选状态筛选：" + sql);
            // 越权防线：筛选条件必须带 user_id，否则会结算到别人的购物车
            assertTrue(sql.contains("user_id"), "未限定用户：" + sql);
            verify(businessMetrics, times(1)).orderCreated();
        }

        @Test
        @DisplayName("埋点只在订单真正落库后 +1（失败路径不计数）")
        void countsCreatedMetricOnce() {
            when(addressMapper.selectById(1L)).thenReturn(address(1L, USER_ID));
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 1L, 1, 1)));
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "书", "10.00", 5, 1)));
            stubOrderInsert();
            when(bookMapper.update(isNull(), any())).thenReturn(1);

            orderService.create(USER_ID, request(1L, List.of(11L)));

            verify(businessMetrics, times(1)).orderCreated();
        }
    }

    // ================================================================== 支付

    @Nested
    @DisplayName("模拟支付 pay()")
    class Pay {

        private Orders order(int status) {
            Orders o = new Orders();
            o.setId(500L);
            o.setOrderNo("BK202610190001");
            o.setUserId(USER_ID);
            o.setStatus(status);
            return o;
        }

        @Test
        @DisplayName("重复支付 → 422")
        void rejectsAlreadyPaidOrder() {
            when(ordersMapper.selectById(500L)).thenReturn(order(OrderStatus.PAID.getCode()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.pay(USER_ID, 500L));

            assertTrue(ex.getMessage().contains("已支付"));
            verify(businessMetrics, never()).orderPaid();
        }

        @Test
        @DisplayName("非「待支付」状态（如已发货）→ 422，错误文案带上中文状态名")
        void rejectsNonPendingOrder() {
            when(ordersMapper.selectById(500L)).thenReturn(order(OrderStatus.SHIPPED.getCode()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.pay(USER_ID, 500L));

            assertTrue(ex.getMessage().contains("已发货"));
        }

        @Test
        @DisplayName("支付成功：置为已支付、写入支付时间、埋点 +1")
        void marksPaidAndCountsMetric() {
            when(ordersMapper.selectById(500L)).thenReturn(order(OrderStatus.PENDING_PAY.getCode()));

            orderService.pay(USER_ID, 500L);

            ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
            verify(ordersMapper).updateById(captor.capture());
            assertEquals(OrderStatus.PAID.getCode(), captor.getValue().getStatus());
            assertNotNull(captor.getValue().getPayTime());
            verify(businessMetrics, times(1)).orderPaid();
        }
    }

    // ============================================================ 支付回调幂等

    @Nested
    @DisplayName("支付回调 markPaidByOrderNo()")
    class MarkPaid {

        @Test
        @DisplayName("订单号为空/空白 → 直接 false，不查库")
        void returnsFalseOnBlankOrderNo() {
            assertEquals(false, orderService.markPaidByOrderNo(null));
            assertEquals(false, orderService.markPaidByOrderNo("  "));
            verify(ordersMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("首次入账成功 → true 且埋点 +1")
        void marksPaidAndCounts() {
            when(ordersMapper.update(isNull(), any())).thenReturn(1);

            assertTrue(orderService.markPaidByOrderNo("BK202610190001"));

            verify(businessMetrics, times(1)).orderPaid();
        }

        @Test
        @DisplayName("重复推送（影响 0 行）→ false 且不计数，避免支付量虚高")
        void idempotentOnDuplicateNotify() {
            when(ordersMapper.update(isNull(), any())).thenReturn(0);

            assertEquals(false, orderService.markPaidByOrderNo("BK202610190001"));

            verify(businessMetrics, never()).orderPaid();
        }
    }

    // ============================================================== 确认收货

    @Nested
    @DisplayName("确认收货 confirm()")
    class Confirm {

        private Orders order(int status) {
            Orders o = new Orders();
            o.setId(600L);
            o.setUserId(USER_ID);
            o.setStatus(status);
            return o;
        }

        @Test
        @DisplayName("仅「已发货」可确认收货，未发货 → 422")
        void rejectsOrderNotShipped() {
            when(ordersMapper.selectById(600L)).thenReturn(order(OrderStatus.PAID.getCode()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.confirm(USER_ID, 600L));

            assertTrue(ex.getMessage().contains("仅「已发货」订单可确认收货"));
            verify(ordersMapper, never()).updateById(any(Orders.class));
        }

        @Test
        @DisplayName("已发货 → 已完成")
        void movesShippedToCompleted() {
            when(ordersMapper.selectById(600L)).thenReturn(order(OrderStatus.SHIPPED.getCode()));

            orderService.confirm(USER_ID, 600L);

            ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
            verify(ordersMapper).updateById(captor.capture());
            assertEquals(OrderStatus.COMPLETED.getCode(), captor.getValue().getStatus());
        }
    }

    // ================================================================== 取消

    @Nested
    @DisplayName("取消订单 cancel()")
    class Cancel {

        private Orders order(int status) {
            Orders o = new Orders();
            o.setId(700L);
            o.setOrderNo("BK202610190007");
            o.setUserId(USER_ID);
            o.setStatus(status);
            return o;
        }

        @Test
        @DisplayName("只有「待支付」可取消，已支付 → 422")
        void rejectsNonPendingOrder() {
            when(ordersMapper.selectById(700L)).thenReturn(order(OrderStatus.PAID.getCode()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.cancel(USER_ID, 700L));

            assertTrue(ex.getMessage().contains("仅「待支付」订单可取消"));
            verify(bookMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("取消成功：回补库存、回退销量（sales 用 GREATEST 兜底不为负）")
        void returnsStockAndRollsBackSales() {
            when(ordersMapper.selectById(700L)).thenReturn(order(OrderStatus.PENDING_PAY.getCode()));
            when(ordersMapper.update(isNull(), any())).thenReturn(1);
            OrderItem i1 = new OrderItem();
            i1.setOrderId(700L);
            i1.setBookId(1L);
            i1.setQuantity(2);
            when(orderItemMapper.selectList(any())).thenReturn(List.of(i1));

            orderService.cancel(USER_ID, 700L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaUpdateWrapper<Book>> captor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(bookMapper).update(isNull(), captor.capture());
            String set = normalize(captor.getValue().getSqlSet());
            assertTrue(set.contains("stock + 2"), "库存未回补：" + set);
            assertTrue(set.contains("GREATEST(sales - 2, 0)"), "销量未安全回退：" + set);
        }

        @Test
        @DisplayName("状态已被抢先改变（影响 0 行）→ 422，不重复回补库存")
        void throwsWhenStatusAlreadyChanged() {
            when(ordersMapper.selectById(700L)).thenReturn(order(OrderStatus.PENDING_PAY.getCode()));
            when(ordersMapper.update(isNull(), any())).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.cancel(USER_ID, 700L));

            assertTrue(ex.getMessage().contains("订单状态已变更"));
            // 关键：状态没改成功就不能回补库存，否则库存会被凭空加多
            verify(bookMapper, never()).update(isNull(), any());
        }
    }

    // ================================================================== 查询

    @Nested
    @DisplayName("订单查询 detail() / page()")
    class Query {

        @Test
        @DisplayName("订单不存在 → 404")
        void detailRejectsMissingOrder() {
            when(ordersMapper.selectById(800L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.detail(USER_ID, 800L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("查看他人订单 → 403（越权拦截）")
        void detailRejectsOtherUsersOrder() {
            Orders o = new Orders();
            o.setId(800L);
            o.setUserId(OTHER_USER_ID);
            when(ordersMapper.selectById(800L)).thenReturn(o);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.detail(USER_ID, 800L));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("详情返回明细与总件数")
        void detailReturnsItemsAndTotalQuantity() {
            Orders o = new Orders();
            o.setId(800L);
            o.setUserId(USER_ID);
            o.setStatus(OrderStatus.PAID.getCode());
            when(ordersMapper.selectById(800L)).thenReturn(o);
            OrderItem i1 = new OrderItem();
            i1.setOrderId(800L);
            i1.setQuantity(2);
            OrderItem i2 = new OrderItem();
            i2.setOrderId(800L);
            i2.setQuantity(3);
            when(orderItemMapper.selectList(any())).thenReturn(List.of(i1, i2));

            OrderVO vo = orderService.detail(USER_ID, 800L);

            assertEquals(5, vo.getTotalQuantity());
            assertEquals(2, vo.getItems().size());
        }

        @Test
        @DisplayName("pageSize 超限被收敛到 50（防止大分页拖垮数据库）")
        void pageClampsOversizedPageSize() {
            when(ordersMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 50));
            when(orderItemMapper.selectList(any())).thenReturn(List.of());

            PageVO<OrderVO> vo = orderService.page(USER_ID, null, 1L, 9999L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<IPage<Orders>> captor = ArgumentCaptor.forClass(IPage.class);
            verify(ordersMapper).selectPage(captor.capture(), any());
            assertEquals(50L, captor.getValue().getSize());
            assertEquals(0, vo.getList().size());
        }

        @Test
        @DisplayName("页码非法时回落到第 1 页")
        void pageFallsBackToFirstPage() {
            when(ordersMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
            when(orderItemMapper.selectList(any())).thenReturn(List.of());

            orderService.page(USER_ID, null, 0L, 10L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<IPage<Orders>> captor = ArgumentCaptor.forClass(IPage.class);
            verify(ordersMapper).selectPage(captor.capture(), any());
            assertEquals(1L, captor.getValue().getCurrent());
        }
    }

    // ============================================================== 超时取消

    @Nested
    @DisplayName("超时自动取消 autoCancelTimeout()")
    class AutoCancel {

        @Test
        @DisplayName("无候选订单 → 返回 0，且完全不触碰缓存（避免白白冲掉图书缓存）")
        void returnsZeroAndSkipsCacheWhenNoCandidates() {
            when(ordersMapper.selectList(any())).thenReturn(List.of());

            assertEquals(0, orderService.autoCancelTimeout(30, 100));

            verify(cacheManagerProvider, never()).getIfAvailable();
            verify(bookMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("候选存在但状态已被抢先改变 → 计 0 单，同样不清缓存")
        void skipsCacheWhenNothingActuallyCancelled() {
            Orders candidate = new Orders();
            candidate.setId(900L);
            candidate.setCreateTime(LocalDateTime.now().minusHours(2));
            when(ordersMapper.selectList(any())).thenReturn(List.of(candidate));
            when(ordersMapper.update(isNull(), any())).thenReturn(0);

            assertEquals(0, orderService.autoCancelTimeout(30, 100));

            verify(cacheManagerProvider, never()).getIfAvailable();
        }

        @Test
        @DisplayName("确有订单被取消 → 清空 book 与 hotBook 缓存（顺序与销量都变了）")
        void clearsBookAndHotBookCacheWhenAnyCancelled() {
            Orders candidate = new Orders();
            candidate.setId(900L);
            candidate.setOrderNo("BK202610190009");
            candidate.setCreateTime(LocalDateTime.now().minusHours(2));
            when(ordersMapper.selectList(any())).thenReturn(List.of(candidate));
            when(ordersMapper.update(isNull(), any())).thenReturn(1);
            when(orderItemMapper.selectList(any())).thenReturn(List.of());
            CacheManager cacheManager = org.mockito.Mockito.mock(CacheManager.class);
            Cache cache = org.mockito.Mockito.mock(Cache.class);
            when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            assertEquals(1, orderService.autoCancelTimeout(30, 100));

            verify(cache, times(2)).clear();
            verify(cacheManager).getCache("book");
            verify(cacheManager).getCache("hotBook");
        }
    }
}
