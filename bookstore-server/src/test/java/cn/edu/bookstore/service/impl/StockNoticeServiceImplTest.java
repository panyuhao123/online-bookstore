package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.StockNotice;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.StockNoticeMapper;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeVO;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockNoticeServiceImpl} 单元测试。
 *
 * <p>订阅动作看起来简单，但它有四道拒绝语义，每一道漏掉都会产生一类「说不通的数据」：
 * <ul>
 *     <li>图书不存在 → 404；</li>
 *     <li>图书当前<b>有货</b> → 422。让用户订阅一本现在就能买的书没有意义，
 *         而这条最容易被漏掉 —— 前端只在缺货时才显示按钮，后端却不能依赖前端；</li>
 *     <li>已在待通知 → 409（重复订阅）；</li>
 *     <li>此前已取消 / 已通知过 → <b>复用原记录</b>重新置为待通知，
 *         而不是插新行 —— (user_id, book_id) 上有唯一键，插新行会直接撞约束。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockNoticeServiceImpl —— 订阅规则、复用记录与取消幂等")
class StockNoticeServiceImplTest {

    private static final long USER_ID = 1001L;

    @Mock private StockNoticeMapper stockNoticeMapper;
    @Mock private BookMapper bookMapper;

    @InjectMocks private StockNoticeServiceImpl stockNoticeService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(StockNotice.class, Book.class);
    }

    private Book book(long id, String title, int stock, int status) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setPrice(new BigDecimal("39.00"));
        b.setStock(stock);
        b.setStatus(status);
        return b;
    }

    private StockNotice notice(long id, long bookId, int status) {
        StockNotice n = new StockNotice();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setBookId(bookId);
        n.setStatus(status);
        n.setCreateTime(LocalDateTime.now().minusDays(1));
        return n;
    }

    private static String sqlOf(Wrapper<?> wrapper) {
        return wrapper == null ? "" : wrapper.getSqlSegment().replace("`", "").replaceAll("\\s+", " ").trim();
    }

    // ============================================================== 订阅

    @Nested
    @DisplayName("订阅 subscribe()")
    class Subscribe {

        @Test
        @DisplayName("图书不存在 → 404")
        void notFound() {
            when(bookMapper.selectById(99L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> stockNoticeService.subscribe(USER_ID, 99L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
            verify(stockNoticeMapper, never()).insert(any(StockNotice.class));
        }

        @Test
        @DisplayName("图书已下架 → 422（即便补了货也买不到，提示订阅没有意义）")
        void rejectsOffSaleBook() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "停印书", 0, 0));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> stockNoticeService.subscribe(USER_ID, 11L));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("下架"));
        }

        @Test
        @DisplayName("图书当前有货 → 422，且提示语带上书名（让用户知道可以直接下单）")
        void rejectsWhenInStock() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论", 5, 1));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> stockNoticeService.subscribe(USER_ID, 11L));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("算法导论"));
            verify(stockNoticeMapper, never()).insert(any(StockNotice.class));
        }

        @Test
        @DisplayName("首次订阅（缺货）→ 插入一条「待通知」记录")
        void insertsPendingNotice() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论", 0, 1));
            when(stockNoticeMapper.selectOne(any())).thenReturn(null);

            StockNoticeVO vo = stockNoticeService.subscribe(USER_ID, 11L);

            ArgumentCaptor<StockNotice> captor = ArgumentCaptor.forClass(StockNotice.class);
            verify(stockNoticeMapper).insert(captor.capture());
            assertEquals(StockNotice.STATUS_PENDING, captor.getValue().getStatus());
            assertEquals(USER_ID, captor.getValue().getUserId());
            assertEquals(11L, captor.getValue().getBookId());
            assertEquals("算法导论", vo.getBookTitle());
        }

        @Test
        @DisplayName("已在待通知状态 → 409（不重复插行）")
        void rejectsDuplicatePending() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论", 0, 1));
            when(stockNoticeMapper.selectOne(any())).thenReturn(notice(1L, 11L, StockNotice.STATUS_PENDING));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> stockNoticeService.subscribe(USER_ID, 11L));

            assertEquals(ErrorCode.CONFLICT.getCode(), ex.getErrorCode().getCode());
            verify(stockNoticeMapper, never()).insert(any(StockNotice.class));
        }

        @Test
        @DisplayName("此前已取消 → 复用原记录重置为待通知（插新行会撞 (user_id, book_id) 唯一键）")
        void reusesCancelledRecord() {
            StockNotice cancelled = notice(7L, 11L, StockNotice.STATUS_CANCELLED);
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论", 0, 1));
            when(stockNoticeMapper.selectOne(any())).thenReturn(cancelled);

            stockNoticeService.subscribe(USER_ID, 11L);

            verify(stockNoticeMapper, never()).insert(any(StockNotice.class));
            ArgumentCaptor<StockNotice> captor = ArgumentCaptor.forClass(StockNotice.class);
            verify(stockNoticeMapper).updateById(captor.capture());
            assertEquals(7L, captor.getValue().getId());
            assertEquals(StockNotice.STATUS_PENDING, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("此前已通知过 → 复用原记录并把 notifyTime 清空（新一轮补货要重新通知）")
        void reusesNotifiedRecordAndClearsNotifyTime() {
            StockNotice notified = notice(7L, 11L, StockNotice.STATUS_NOTIFIED);
            notified.setNotifyTime(LocalDateTime.now().minusDays(1));
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论", 0, 1));
            when(stockNoticeMapper.selectOne(any())).thenReturn(notified);

            stockNoticeService.subscribe(USER_ID, 11L);

            ArgumentCaptor<StockNotice> captor = ArgumentCaptor.forClass(StockNotice.class);
            verify(stockNoticeMapper).updateById(captor.capture());
            assertEquals(StockNotice.STATUS_PENDING, captor.getValue().getStatus());
            assertNull(captor.getValue().getNotifyTime(), "旧的 notifyTime 未清空，会让人误以为已通知过");
        }
    }

    // ============================================================== 列表

    @Nested
    @DisplayName("我的订阅 myNotices()")
    class MyNotices {

        @Test
        @DisplayName("排除「已取消」，并按订阅时间倒序、限定当前用户")
        void excludesCancelledAndScopesToUser() {
            IPage<StockNotice> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(List.of());
            when(stockNoticeMapper.selectPage(any(), any())).thenReturn(page);

            stockNoticeService.myNotices(USER_ID, 1L, 10L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<StockNotice>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(stockNoticeMapper).selectPage(any(), captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("user_id"), "未限定用户：" + sql);
            assertTrue(sql.contains("<>"), "未排除已取消状态：" + sql);
            assertTrue(sql.contains("create_time"), "未按订阅时间排序：" + sql);
        }

        @Test
        @DisplayName("图书被物理删除 → 过滤该条，避免点进 404")
        void filtersDanglingBooks() {
            IPage<StockNotice> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(List.of(notice(1L, 11L, StockNotice.STATUS_PENDING),
                    notice(2L, 12L, StockNotice.STATUS_PENDING)));
            page.setTotal(2);
            when(stockNoticeMapper.selectPage(any(), any())).thenReturn(page);
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "算法导论", 0, 1)));

            PageVO<StockNoticeVO> vo = stockNoticeService.myNotices(USER_ID, 1L, 10L);

            assertEquals(1, vo.getList().size());
            assertEquals("算法导论", vo.getList().get(0).getBookTitle());
        }

        @Test
        @DisplayName("pageSize 上限 50")
        void capsPageSize() {
            IPage<StockNotice> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(List.of());
            when(stockNoticeMapper.selectPage(any(), any())).thenReturn(page);

            stockNoticeService.myNotices(USER_ID, 1L, 999L);

            ArgumentCaptor<IPage<StockNotice>> captor = ArgumentCaptor.forClass(IPage.class);
            verify(stockNoticeMapper).selectPage(captor.capture(), any());
            assertEquals(50L, captor.getValue().getSize());
        }
    }

    // ============================================================== 取消

    @Nested
    @DisplayName("取消订阅 unsubscribe()")
    class Unsubscribe {

        @Test
        @DisplayName("置为「已取消」而不是物理删除（保留记录才能回答「多少人后来又不想要了」）")
        void marksCancelled() {
            when(stockNoticeMapper.selectOne(any())).thenReturn(notice(1L, 11L, StockNotice.STATUS_PENDING));

            stockNoticeService.unsubscribe(USER_ID, 11L);

            ArgumentCaptor<StockNotice> captor = ArgumentCaptor.forClass(StockNotice.class);
            verify(stockNoticeMapper).updateById(captor.capture());
            assertEquals(StockNotice.STATUS_CANCELLED, captor.getValue().getStatus());
            verify(stockNoticeMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("取消一个不存在的订阅 → 静默成功（接口幂等，重复点击不该报错）")
        void idempotentWhenAbsent() {
            when(stockNoticeMapper.selectOne(any())).thenReturn(null);

            stockNoticeService.unsubscribe(USER_ID, 11L);

            verify(stockNoticeMapper, never()).updateById(any(StockNotice.class));
        }

        @Test
        @DisplayName("取消操作的查询条件必须同时带 user_id 与 book_id（否则会取消到别人的订阅）")
        void scopedToUserAndBook() {
            when(stockNoticeMapper.selectOne(any())).thenReturn(null);

            stockNoticeService.unsubscribe(USER_ID, 11L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<StockNotice>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(stockNoticeMapper).selectOne(captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("user_id"), "未限定用户：" + sql);
            assertTrue(sql.contains("book_id"), "未限定图书：" + sql);
        }
    }
}
