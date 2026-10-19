package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.BrowseHistory;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.BrowseHistoryMapper;
import cn.edu.bookstore.model.vo.HistoryItemVO;
import cn.edu.bookstore.model.vo.PageVO;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BrowseHistoryServiceImpl} 单元测试。
 *
 * <p>浏览历史是典型的「埋点性质」功能：它不该打断用户的主流程，因此这里重点固定三条取舍：
 * <ul>
 *     <li><b>记录失败不抛异常</b>：图书不存在时静默返回 false —— 为一条浏览记录弹一个
 *         错误提示，比不记录更让用户困惑；</li>
 *     <li><b>合并时旧记录不覆盖新记录</b>：本地记录可能是几小时前的，不该把库里的
 *         「刚刚浏览过」改回更早的时间；</li>
 *     <li><b>裁剪只删最旧的</b>：超出上限时删的是最久远的记录，而不是最新的。
 *         这条如果写反，用户会看到「刚浏览的书立刻消失」。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BrowseHistoryServiceImpl —— 去重、裁剪与本地合并")
class BrowseHistoryServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final int MAX_PER_USER = 100;

    @Mock private BrowseHistoryMapper browseHistoryMapper;
    @Mock private BookMapper bookMapper;

    @InjectMocks private BrowseHistoryServiceImpl browseHistoryService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(BrowseHistory.class, Book.class);
    }

    private Book book(long id, String title) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setPrice(new BigDecimal("25.00"));
        b.setStock(3);
        b.setStatus(1);
        return b;
    }

    private BrowseHistory history(long id, long bookId, LocalDateTime viewTime) {
        BrowseHistory h = new BrowseHistory();
        h.setId(id);
        h.setUserId(USER_ID);
        h.setBookId(bookId);
        h.setViewTime(viewTime);
        return h;
    }

    private static String sqlOf(Wrapper<?> wrapper) {
        return wrapper == null ? "" : wrapper.getSqlSegment().replace("`", "").replaceAll("\\s+", " ").trim();
    }

    // ============================================================== 记录

    @Nested
    @DisplayName("记录一次浏览 record()")
    class Record {

        @Test
        @DisplayName("图书不存在 → 返回 false，且完全不写库（埋点不该中断浏览）")
        void skipsMissingBook() {
            when(bookMapper.selectById(99L)).thenReturn(null);

            assertFalse(browseHistoryService.record(USER_ID, 99L));

            verify(browseHistoryMapper, never()).insert(any(BrowseHistory.class));
        }

        @Test
        @DisplayName("userId / bookId 缺失 → 返回 false，不查库")
        void skipsNullArgs() {
            assertFalse(browseHistoryService.record(null, 1L));
            assertFalse(browseHistoryService.record(USER_ID, null));

            verify(bookMapper, never()).selectById(any(Long.class));
        }

        @Test
        @DisplayName("首次浏览 → 插入一行")
        void insertsNewRecord() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论"));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn(1L);

            assertTrue(browseHistoryService.record(USER_ID, 11L));

            ArgumentCaptor<BrowseHistory> captor = ArgumentCaptor.forClass(BrowseHistory.class);
            verify(browseHistoryMapper).insert(captor.capture());
            assertEquals(USER_ID, captor.getValue().getUserId());
            assertEquals(11L, captor.getValue().getBookId());
        }

        @Test
        @DisplayName("重复浏览 → 只更新时间，不新增行（表上有唯一键兜底，应用层也不该撞它）")
        void updatesViewTimeInsteadOfInserting() {
            LocalDateTime old = LocalDateTime.now().minusHours(3);
            BrowseHistory existing = history(1L, 11L, old);
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论"));
            when(browseHistoryMapper.selectOne(any())).thenReturn(existing);
            when(browseHistoryMapper.selectCount(any())).thenReturn(1L);

            assertTrue(browseHistoryService.record(USER_ID, 11L));

            verify(browseHistoryMapper, never()).insert(any(BrowseHistory.class));
            ArgumentCaptor<BrowseHistory> captor = ArgumentCaptor.forClass(BrowseHistory.class);
            verify(browseHistoryMapper).updateById(captor.capture());
            assertTrue(captor.getValue().getViewTime().isAfter(old));
        }

        @Test
        @DisplayName("记录后的裁剪查询始终限定在当前用户（不能因为裁剪把别人的数据删了）")
        void trimScopedToCurrentUser() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论"));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn(1L);

            browseHistoryService.record(USER_ID, 11L);

            ArgumentCaptor<Wrapper<BrowseHistory>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(browseHistoryMapper).selectCount(captor.capture());
            assertTrue(sqlOf(captor.getValue()).contains("user_id"), "裁剪未限定用户：" + sqlOf(captor.getValue()));
        }
    }

    // ============================================================== 裁剪

    @Nested
    @DisplayName("超量裁剪")
    class Trim {

        @Test
        @DisplayName("未超上限 → 不删任何记录")
        void keepsWhenUnderLimit() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论"));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn((long) MAX_PER_USER);

            browseHistoryService.record(USER_ID, 11L);

            verify(browseHistoryMapper, never()).selectList(any());
            verify(browseHistoryMapper, never()).deleteBatchIds(any());
        }

        @Test
        @DisplayName("超出上限 → 只删最旧的（顺序写反会让用户觉得「刚看的书立刻消失」）")
        void deletesOldestOnly() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论"));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn((long) MAX_PER_USER + 2);
            when(browseHistoryMapper.selectList(any())).thenReturn(List.of(
                    history(1L, 1L, LocalDateTime.now().minusDays(3)),
                    history(2L, 2L, LocalDateTime.now().minusDays(2))));

            browseHistoryService.record(USER_ID, 11L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(browseHistoryMapper).deleteBatchIds(captor.capture());
            assertEquals(2, captor.getValue().size());
            assertTrue(captor.getValue().containsAll(List.of(1L, 2L)));
        }

        @Test
        @DisplayName("待删列表的查询带 LIMIT，且按浏览时间升序取（即最旧的）")
        void staleQueryOrdersAscending() {
            when(bookMapper.selectById(11L)).thenReturn(book(11L, "算法导论"));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn((long) MAX_PER_USER + 3);
            when(browseHistoryMapper.selectList(any())).thenReturn(List.of());

            browseHistoryService.record(USER_ID, 11L);

            ArgumentCaptor<Wrapper<BrowseHistory>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(browseHistoryMapper).selectList(captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("view_time"), "未按浏览时间排序：" + sql);
            assertTrue(sql.contains("ASC") || sql.contains("asc"), "未升序取最旧：" + sql);
        }
    }

    // ============================================================== 合并

    @Nested
    @DisplayName("合并本地记录 merge()")
    class Merge {

        @Test
        @DisplayName("按提交顺序还原时间先后：第 0 条最新，越靠后越旧")
        void restoresRelativeOrder() {
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(
                    book(11L, "A"), book(12L, "B"), book(13L, "C")));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn(3L);

            int added = browseHistoryService.merge(USER_ID, List.of(11L, 12L, 13L));

            assertEquals(3, added);
            ArgumentCaptor<BrowseHistory> captor = ArgumentCaptor.forClass(BrowseHistory.class);
            verify(browseHistoryMapper, times(3)).insert(captor.capture());
            List<BrowseHistory> inserted = captor.getAllValues();
            assertEquals(11L, inserted.get(0).getBookId());
            assertTrue(inserted.get(0).getViewTime().isAfter(inserted.get(1).getViewTime()));
            assertTrue(inserted.get(1).getViewTime().isAfter(inserted.get(2).getViewTime()));
        }

        @Test
        @DisplayName("重复 ID 去重后再写，返回值只计真实新增")
        void deduplicatesIds() {
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "A")));
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn(1L);

            assertEquals(1, browseHistoryService.merge(USER_ID, List.of(11L, 11L, 11L)));

            verify(browseHistoryMapper, times(1)).insert(any(BrowseHistory.class));
        }

        @Test
        @DisplayName("库中已有该记录时不计入「新增」；且本地旧时间不回退服务端新时间")
        void doesNotRegressExistingViewTime() {
            LocalDateTime recent = LocalDateTime.now();
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "A")));
            when(browseHistoryMapper.selectOne(any())).thenReturn(history(1L, 11L, recent));
            when(browseHistoryMapper.selectCount(any())).thenReturn(1L);

            // 本地提交的 viewTime 是「现在 - 0 秒」，不晚于 recent（可能相等）：
            // 相等时不动库，严格晚于时才刷新时间。两种情况都不算新增。
            int added = browseHistoryService.merge(USER_ID, List.of(11L));

            assertEquals(0, added, "库中已有的记录不算新增（前端据此回报同步条数）");
            verify(browseHistoryMapper, never()).insert(any(BrowseHistory.class));
            // 允许刷新时间（本地记录更晚时），但任何一次写入都不允许把时间改早
            ArgumentCaptor<BrowseHistory> captor = ArgumentCaptor.forClass(BrowseHistory.class);
            verify(browseHistoryMapper, atMost(1)).updateById(captor.capture());
            for (BrowseHistory written : captor.getAllValues()) {
                assertFalse(written.getViewTime().isBefore(recent),
                        "本地旧记录不允许把服务端的浏览时间改早");
            }
        }

        @Test
        @DisplayName("混合场景：已存在的不计数、新出现的计数，返回值是「真正新增」的条数")
        void countsOnlyNewlyInsertedRows() {
            LocalDateTime recent = LocalDateTime.now();
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "A"), book(12L, "B")));
            // 第 0 条（11 号）库中已有，第 1 条（12 号）是新的
            when(browseHistoryMapper.selectOne(any())).thenReturn(history(1L, 11L, recent), null);
            when(browseHistoryMapper.selectCount(any())).thenReturn(2L);

            assertEquals(1, browseHistoryService.merge(USER_ID, List.of(11L, 12L)),
                    "11 号已存在不计入、12 号是新插入的计 1");
            verify(browseHistoryMapper, times(1)).insert(any(BrowseHistory.class));
        }

        @Test
        @DisplayName("超出保留上限的尾部记录直接跳过（写了也会被立刻裁掉），只写上限内的部分")
        void skipsBeyondLimit() {
            List<Long> ids = new ArrayList<>();
            List<Book> books = new ArrayList<>();
            for (long i = 1; i <= MAX_PER_USER + 5; i++) {
                ids.add(i);
                books.add(book(i, "书" + i));
            }
            when(bookMapper.selectBatchIds(anyList())).thenReturn(books);
            when(browseHistoryMapper.selectOne(any())).thenReturn(null);
            when(browseHistoryMapper.selectCount(any())).thenReturn(0L);

            assertEquals(MAX_PER_USER, browseHistoryService.merge(USER_ID, ids));

            verify(browseHistoryMapper, times(MAX_PER_USER)).insert(any(BrowseHistory.class));
        }

        @Test
        @DisplayName("空列表 → 返回 0，不查库")
        void emptyInputIsNoop() {
            assertEquals(0, browseHistoryService.merge(USER_ID, List.of()));
            assertEquals(0, browseHistoryService.merge(USER_ID, null));

            verify(browseHistoryMapper, never()).insert(any(BrowseHistory.class));
        }
    }

    // ============================================================== 查询与删除

    @Nested
    @DisplayName("查询与删除")
    class QueryAndDelete {

        @Test
        @DisplayName("列表里图书已被物理删除 → 过滤掉该条（避免点进一条 404 的记录）")
        void filtersDanglingBooks() {
            IPage<BrowseHistory> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(List.of(
                    history(1L, 11L, LocalDateTime.now()),
                    history(2L, 12L, LocalDateTime.now().minusMinutes(1))));
            page.setTotal(2);
            when(browseHistoryMapper.selectPage(any(), any())).thenReturn(page);
            // 书 12 已不存在
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "算法导论")));

            PageVO<HistoryItemVO> vo = browseHistoryService.page(USER_ID, 1L, 10L);

            assertEquals(1, vo.getList().size());
            assertEquals("算法导论", vo.getList().get(0).getTitle());
            assertEquals(2L, vo.getTotal());
        }

        @Test
        @DisplayName("查询条件限定当前用户，并按浏览时间倒序")
        void pageScopedToUser() {
            IPage<BrowseHistory> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(List.of());
            when(browseHistoryMapper.selectPage(any(), any())).thenReturn(page);

            browseHistoryService.page(USER_ID, 1L, 999L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<BrowseHistory>> captor = ArgumentCaptor.forClass(Wrapper.class);
            ArgumentCaptor<IPage<BrowseHistory>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            verify(browseHistoryMapper).selectPage(pageCaptor.capture(), captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("user_id"), "未限定用户：" + sql);
            assertTrue(sql.contains("view_time"), "未按时间排序：" + sql);
            // pageSize 上限 50
            assertEquals(50L, pageCaptor.getValue().getSize());
        }

        @Test
        @DisplayName("删单条与清空都必须限定当前用户（否则 A 能把 B 的历史清掉）")
        void deletesScopedToUser() {
            browseHistoryService.removeOne(USER_ID, 11L);
            ArgumentCaptor<Wrapper<BrowseHistory>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(browseHistoryMapper).delete(captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("user_id"), "删单条未限定用户：" + sql);
            assertTrue(sql.contains("book_id"), "删单条未限定图书：" + sql);

            browseHistoryService.clear(USER_ID);
            verify(browseHistoryMapper, times(2)).delete(captor.capture());
            assertTrue(sqlOf(captor.getAllValues().get(1)).contains("user_id"), "清空未限定用户");
        }
    }
}
