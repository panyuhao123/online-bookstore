package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.BookList;
import cn.edu.bookstore.entity.BookListItem;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.BookListItemMapper;
import cn.edu.bookstore.mapper.BookListMapper;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.BookListRequest;
import cn.edu.bookstore.model.vo.BookListDetailVO;
import cn.edu.bookstore.model.vo.BookListVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BookListServiceImpl} 单元测试。
 *
 * <p>书单是「个人数据 + 公开内容」的混合体，因此这里重点覆盖两类只有它才会遇到的问题：
 * <ul>
 *     <li><b>越权</b>：改别人的书单、看别人的私密书单 —— 必须 403，不能静默失败
 *         （静默失败会让前端以为成功，用户看到数据没变只会反复点击）；</li>
 *     <li><b>批量加入的边界</b>：重复 ID、已存在的图书、不存在的图书混在一起时，
 *         不能整批失败，且返回值必须是「实际新增数」而不是「请求条数」，
 *         否则前端无法区分「全部已在书单里」与「真的加进去了」。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookListServiceImpl —— 书单归属、上限与批量加书")
class BookListServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;

    @Mock private BookListMapper bookListMapper;
    @Mock private BookListItemMapper bookListItemMapper;
    @Mock private BookMapper bookMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private BookListServiceImpl bookListService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(BookList.class, BookListItem.class, Book.class, User.class);
    }

    // ------------------------------------------------------------------ 造数据

    private BookList list(long id, long userId, int isPublic, String name) {
        BookList l = new BookList();
        l.setId(id);
        l.setUserId(userId);
        l.setName(name);
        l.setIsPublic(isPublic);
        l.setBookCount(0);
        // 存量与新建书单一律是「正常」状态（V7 的 DEFAULT 1）。
        // 造数据时显式写上，避免单测里出现「status=null」这种线上不存在的形态 ——
        // 那会让「隐藏判定」用一个空值走过，看起来通过、其实没测到。
        l.setStatus(1);
        return l;
    }

    private BookListRequest request(String name) {
        BookListRequest r = new BookListRequest();
        r.setName(name);
        r.setIsPublic(1);
        return r;
    }

    private Book book(long id, String title) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setPrice(new java.math.BigDecimal("10.00"));
        b.setStock(5);
        b.setStatus(1);
        b.setCoverUrl("/files/cover/" + id + ".jpg");
        return b;
    }

    private BookListItem item(long id, long listId, long bookId, int sort) {
        BookListItem i = new BookListItem();
        i.setId(id);
        i.setListId(listId);
        i.setBookId(bookId);
        i.setSort(sort);
        return i;
    }

    /** 让 selectPage 返回给定记录（total 与记录数一致，pages 由 Page 自行推导） */
    private void stubPage(List<BookList> records) {
        when(bookListMapper.selectPage(any(), any())).thenAnswer(inv -> {
            IPage<BookList> page = inv.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        });
    }

    private static String sqlOf(Wrapper<?> wrapper) {
        return wrapper == null ? "" : wrapper.getSqlSegment().replace("`", "").replaceAll("\\s+", " ").trim();
    }

    // ============================================================== 详情

    @Nested
    @DisplayName("详情 detail()")
    class Detail {

        @Test
        @DisplayName("书单不存在 → 404")
        void notFound() {
            when(bookListMapper.selectById(9L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.detail(USER_ID, 9L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("私密书单：他人访问 403（而不是伪装成 404 —— 书单 ID 自增，藏不住存在性）")
        void privateListRejectsOthers() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 0, "我的私藏"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.detail(USER_ID, 1L));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("私密书单：匿名访问同样 403（未登录不是绕过归属校验的理由）")
        void privateListRejectsAnonymous() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 0, "我的私藏"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.detail(null, 1L));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("创建者本人可看自己的私密书单，且 mine=true")
        void ownerCanReadPrivateList() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 0, "我的私藏"));
            when(bookListItemMapper.selectList(any())).thenReturn(List.of());
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(USER_ID, "书虫小王")));

            BookListDetailVO vo = bookListService.detail(USER_ID, 1L);

            assertNotNull(vo);
            assertTrue(vo.getMine());
            assertEquals("书虫小王", vo.getOwnerNickname());
        }

        @Test
        @DisplayName("公开书单匿名可读，且 mine=false")
        void publicListReadableAnonymously() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 1, "高分书单"));
            when(bookListItemMapper.selectList(any())).thenReturn(List.of());
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OTHER_USER_ID, "路人甲")));

            BookListDetailVO vo = bookListService.detail(null, 1L);

            assertEquals("路人甲", vo.getOwnerNickname());
            assertEquals(Boolean.FALSE, vo.getMine());
        }

        @Test
        @DisplayName("书单内图书被物理删除 → 跳过该条，书单仍能打开")
        void skipsDanglingBooks() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "书单"));
            when(bookListItemMapper.selectList(any()))
                    .thenReturn(List.of(item(1L, 1L, 11L, 0), item(2L, 1L, 12L, 1)));
            // 书 12 已不存在
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "算法导论")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(USER_ID, "小王")));

            BookListDetailVO vo = bookListService.detail(USER_ID, 1L);

            assertEquals(1, vo.getBooks().size());
            assertEquals("算法导论", vo.getBooks().get(0).getTitle());
        }

        @Test
        @DisplayName("平台已隐藏的书单：他人访问 404（隐藏就是要让内容不可见，回 403 等于告诉对方「这里有东西」）")
        void hiddenListInvisibleToOthers() {
            BookList hidden = list(1L, OTHER_USER_ID, 1, "违规书单");
            hidden.setStatus(0);
            when(bookListMapper.selectById(1L)).thenReturn(hidden);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.detail(USER_ID, 1L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("平台已隐藏的书单：创建者本人仍能打开，且 status=0（否则用户以为数据丢了）")
        void hiddenListReadableByOwner() {
            BookList hidden = list(1L, USER_ID, 1, "被误判的书单");
            hidden.setStatus(0);
            when(bookListMapper.selectById(1L)).thenReturn(hidden);
            when(bookListItemMapper.selectList(any())).thenReturn(List.of());
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(USER_ID, "小王")));

            BookListDetailVO vo = bookListService.detail(USER_ID, 1L);

            assertEquals(0, vo.getStatus().intValue(), "创建者要看到「已被隐藏」的状态，前端才能给出提示");
            assertTrue(vo.getMine());
        }
    }

    // ============================================================== 新建

    @Nested
    @DisplayName("新建 create()")
    class Create {

        @Test
        @DisplayName("同名书单 → 409（同一用户内重名会让用户无法分辨）")
        void rejectsDuplicateName() {
            when(bookListMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.create(USER_ID, request("想读")));

            assertEquals(ErrorCode.CONFLICT.getCode(), ex.getErrorCode().getCode());
            verify(bookListMapper, never()).insert(any(BookList.class));
        }

        @Test
        @DisplayName("书单数量达上限 → 422，且不落库")
        void rejectsWhenListQuotaExceeded() {
            when(bookListMapper.selectCount(any())).thenReturn(50L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.create(USER_ID, request("第 51 个")));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("50"));
            verify(bookListMapper, never()).insert(any(BookList.class));
        }

        @Test
        @DisplayName("正常创建：名称去空格、初始计数为 0、mine=true")
        void createsList() {
            when(bookListMapper.selectCount(any())).thenReturn(0L);

            BookListVO vo = bookListService.create(USER_ID, request("  想读  "));

            ArgumentCaptor<BookList> captor = ArgumentCaptor.forClass(BookList.class);
            verify(bookListMapper).insert(captor.capture());
            assertEquals("想读", captor.getValue().getName());
            assertEquals(0, captor.getValue().getBookCount());
            assertEquals(1, captor.getValue().getIsPublic());
            assertEquals(USER_ID, captor.getValue().getUserId());
            assertTrue(vo.getMine());
        }

        @Test
        @DisplayName("isPublic=0 时建的是私密书单；缺省一律按公开处理")
        void respectsPrivacyFlag() {
            when(bookListMapper.selectCount(any())).thenReturn(0L);

            BookListRequest privateReq = request("私藏");
            privateReq.setIsPublic(0);
            bookListService.create(USER_ID, privateReq);

            ArgumentCaptor<BookList> captor = ArgumentCaptor.forClass(BookList.class);
            verify(bookListMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getIsPublic());

            BookListRequest unspecified = request("未指定");
            unspecified.setIsPublic(null);
            bookListService.create(USER_ID, unspecified);

            // 换一个全新的 captor：同一个 captor 被两次 verify 复用会重复累积捕获值，
            // 想取「第二次插入」反而会拿到第一次的（第一份是私密=0、第二份是公开=1，最容易看错）
            ArgumentCaptor<BookList> second = ArgumentCaptor.forClass(BookList.class);
            verify(bookListMapper, times(2)).insert(second.capture());
            assertEquals(1, second.getAllValues().get(1).getIsPublic());
        }
    }

    // ============================================================== 编辑与删除

    @Nested
    @DisplayName("编辑 / 删除的归属校验")
    class Ownership {

        @Test
        @DisplayName("编辑他人书单 → 403，且不写库")
        void updateRejectsOthers() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 1, "别人的"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.update(USER_ID, 1L, request("改成我的")));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
            verify(bookListMapper, never()).updateById(any(BookList.class));
        }

        @Test
        @DisplayName("编辑时同名校验要排除自身 —— 只改简介不改名不该被自己挡住")
        void updateExcludesItselfFromNameCheck() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));
            when(bookListMapper.selectCount(any())).thenReturn(0L);

            bookListService.update(USER_ID, 1L, request("想读"));

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectCount(captor.capture());
            String sql = sqlOf(captor.getValue());
            // 必须带上「id <> 自身」的排除条件，否则改名保存会恒报 409
            assertTrue(sql.contains("id <"), "未排除自身：" + sql);
        }

        @Test
        @DisplayName("删除书单：先删条目再删主表（保持先子后父，将来加外键也不用改代码）")
        void removeDeletesItemsFirst() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));

            bookListService.remove(USER_ID, 1L);

            var order = org.mockito.Mockito.inOrder(bookListItemMapper, bookListMapper);
            order.verify(bookListItemMapper).delete(any());
            order.verify(bookListMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除他人书单 → 403，且条目与主表都不动")
        void removeRejectsOthers() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 1, "别人的"));

            assertThrows(BusinessException.class, () -> bookListService.remove(USER_ID, 1L));

            verify(bookListItemMapper, never()).delete(any());
            verify(bookListMapper, never()).deleteById(any(Long.class));
        }
    }

    // ============================================================== 加书

    @Nested
    @DisplayName("批量加书 addBooks()")
    class AddBooks {

        /**
         * 场景：请求 5 个 ID，其中 1 个重复、1 个已在书单、1 个图书不存在，
         * 真正能加进去的只有 2 本。
         */
        private void stubMixedBatch() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));
            when(bookListItemMapper.selectCount(any())).thenReturn(1L);
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(
                    book(11L, "算法导论"), book(12L, "深入理解计算机系统"), book(13L, "代码大全")));
            // 书 13 已经在书单里
            when(bookListItemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L, 13L, 0)));
        }

        @Test
        @DisplayName("去重 + 跳过已存在 + 跳过不存在，返回值只计实际新增")
        void countsOnlyActuallyAdded() {
            stubMixedBatch();

            int added = bookListService.addBooks(USER_ID, 1L, List.of(11L, 12L, 11L, 13L, 99L));

            // 11（新增）、12（新增）、13（已在书单）、99（图书不存在）→ 2
            assertEquals(2, added);
            ArgumentCaptor<BookListItem> captor = ArgumentCaptor.forClass(BookListItem.class);
            verify(bookListItemMapper, times(2)).insert(captor.capture());
            assertEquals(List.of(11L, 12L), captor.getAllValues().stream().map(BookListItem::getBookId).toList());
        }

        @Test
        @DisplayName("排序值从「已有条数」往后排，不覆盖已有顺序")
        void assignsSortAfterExisting() {
            stubMixedBatch();

            bookListService.addBooks(USER_ID, 1L, List.of(11L, 12L));

            ArgumentCaptor<BookListItem> captor = ArgumentCaptor.forClass(BookListItem.class);
            verify(bookListItemMapper, times(2)).insert(captor.capture());
            assertEquals(1, captor.getAllValues().get(0).getSort());
            assertEquals(2, captor.getAllValues().get(1).getSort());
        }

        @Test
        @DisplayName("加完后重算冗余计数 book_count（唯一允许写该字段的地方）")
        void syncsBookCount() {
            stubMixedBatch();
            when(bookListItemMapper.selectCount(any())).thenReturn(1L, 3L);

            bookListService.addBooks(USER_ID, 1L, List.of(11L, 12L));

            verify(bookListMapper).update(any(), any());
        }

        @Test
        @DisplayName("超出单书单上限 → 422（把「已有 + 本次」一起算，防止分批提交绕过）")
        void rejectsOverCapacity() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));
            when(bookListItemMapper.selectCount(any())).thenReturn(199L);
            when(bookMapper.selectBatchIds(anyList()))
                    .thenReturn(List.of(book(11L, "A"), book(12L, "B")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.addBooks(USER_ID, 1L, List.of(11L, 12L)));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("200"));
            verify(bookListItemMapper, never()).insert(any(BookListItem.class));
        }

        @Test
        @DisplayName("全部图书都不存在 → 返回 0 且不写任何条目，也不重算计数")
        void returnsZeroWhenNothingValid() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of());

            assertEquals(0, bookListService.addBooks(USER_ID, 1L, List.of(99L, 98L)));

            verify(bookListItemMapper, never()).insert(any(BookListItem.class));
            verify(bookListMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("往他人书单加书 → 403")
        void rejectsOthers() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 1, "别人的"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bookListService.addBooks(USER_ID, 1L, List.of(11L)));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
            verify(bookListItemMapper, never()).insert(any(BookListItem.class));
        }
    }

    // ============================================================== 移出图书

    @Nested
    @DisplayName("移出图书 removeBook()")
    class RemoveBook {

        @Test
        @DisplayName("确实删掉了才重算计数（没删到就不该多一次写）")
        void syncsOnlyWhenRowDeleted() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));

            when(bookListItemMapper.delete(any())).thenReturn(1);
            bookListService.removeBook(USER_ID, 1L, 11L);
            verify(bookListMapper).update(any(), any());

            org.mockito.Mockito.reset(bookListMapper);
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, USER_ID, 1, "想读"));
            when(bookListItemMapper.delete(any())).thenReturn(0);
            bookListService.removeBook(USER_ID, 1L, 12L);
            verify(bookListMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("移出他人书单里的图书 → 403")
        void rejectsOthers() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OTHER_USER_ID, 1, "别人的"));

            assertThrows(BusinessException.class, () -> bookListService.removeBook(USER_ID, 1L, 11L));

            verify(bookListItemMapper, never()).delete(any());
        }
    }

    // ============================================================== 广场

    @Nested
    @DisplayName("书单广场 page()")
    class Plaza {

        @Test
        @DisplayName("带登录用户时：公开书单 + 自己的私密书单（否则用户建完私密书单就「找不到了」）")
        void includesOwnPrivateListsWhenLoggedIn() {
            stubPage(List.of(list(1L, USER_ID, 0, "私藏")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(USER_ID, "小王")));
            when(bookListItemMapper.selectList(any())).thenReturn(List.of());

            PageVO<BookListVO> page = bookListService.page(USER_ID, 1L, 12L, null);

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("is_public"), "缺少公开条件：" + sql);
            assertTrue(sql.contains("user_id"), "缺少「自己的书单」条件：" + sql);
            assertEquals(1, page.getList().size());
            assertTrue(page.getList().get(0).getMine());
        }

        @Test
        @DisplayName("匿名访问：只查公开书单，SQL 里不出现按用户过滤的条件")
        void anonymousSeesPublicOnly() {
            stubPage(List.of(list(1L, OTHER_USER_ID, 1, "高分书单")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OTHER_USER_ID, "路人甲")));
            when(bookListItemMapper.selectList(any())).thenReturn(List.of());

            bookListService.page(null, 1L, 12L, null);

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            assertTrue(sqlOf(captor.getValue()).contains("is_public"));
        }

        @Test
        @DisplayName("无封面 → 用书单内第一本书的封面兜底，避免广场卡片全是占位图")
        void fillsCoverFromFirstBook() {
            BookList noCover = list(1L, USER_ID, 1, "想读");
            noCover.setCoverUrl(null);
            noCover.setBookCount(2);
            stubPage(List.of(noCover));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(USER_ID, "小王")));
            when(bookListItemMapper.selectList(any()))
                    .thenReturn(List.of(item(1L, 1L, 11L, 0), item(2L, 1L, 12L, 1)));
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "算法导论")));

            PageVO<BookListVO> page = bookListService.page(USER_ID, 1L, 12L, null);

            assertEquals("/files/cover/11.jpg", page.getList().get(0).getCoverUrl());
        }

        @Test
        @DisplayName("分页参数越界时收敛到安全值（pageSize 上限 50）")
        void normalizesPaging() {
            stubPage(List.of());
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of());

            bookListService.page(null, 0L, 999L, null);

            ArgumentCaptor<IPage<BookList>> captor = ArgumentCaptor.forClass(IPage.class);
            verify(bookListMapper).selectPage(captor.capture(), any());
            assertEquals(1L, captor.getValue().getCurrent());
            assertEquals(50L, captor.getValue().getSize());
        }

        @Test
        @DisplayName("广场恒按 status=1 过滤：平台隐藏的书单不能从「自己的那一支」漏出来")
        void excludesHiddenListsEvenForOwner() {
            stubPage(List.of());
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of());

            bookListService.page(USER_ID, 1L, 12L, null);

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            String sql = sqlOf(captor.getValue());
            // status 条件必须与 or 分组并列，而不是被 or 进去：
            // 一旦写进 .and(w -> w.eq(isPublic).or().eq(userId))，
            // 就变成「(公开 or 自己的) and status=1」以外的语义，隐藏书单会漏出
            assertTrue(sql.contains("status"), "广场查询缺少 status 条件：" + sql);
            assertTrue(sql.indexOf("status") < sql.indexOf("is_public"),
                    "status 必须是 AND 链上的前置条件，不能落在 or 分组里：" + sql);
        }
    }

    private User user(long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        u.setPhone("13800138000");
        return u;
    }
}
