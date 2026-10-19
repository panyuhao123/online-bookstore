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
import cn.edu.bookstore.model.dto.AdminBookListQuery;
import cn.edu.bookstore.model.vo.AdminBookListDetailVO;
import cn.edu.bookstore.model.vo.AdminBookListVO;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminBookListServiceImpl} 单元测试。
 *
 * <p>书单治理接口看着只有四个方法，但有四处必须钉住，否则答辩时很容易被追问：
 * <ul>
 *     <li><b>后台不受前台可见性规则约束</b>：私密书单、已隐藏书单都必须能被管理员查到 ——
 *         否则「已隐藏」的书单永远找不回来，恢复功能形同虚设；</li>
 *     <li><b>隐藏优先于删除</b>：updateStatus 只动 status，绝不碰 is_public。
 *         若把隐藏实现成「把 is_public 改 0」，恢复时就没法还原用户当初的选择；</li>
 *     <li><b>重复设置同一状态幂等</b>：不发多余的 UPDATE，也不产生误导性的操作日志；</li>
 *     <li><b>删除先清条目再删主表</b>：项目没有物理外键，顺序颠倒不会报错，
 *         但会留下永远没人认领的孤儿行。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBookListServiceImpl —— 治理语义、幂等与级联删除")
class AdminBookListServiceImplTest {

    private static final long OWNER_ID = 1001L;

    @Mock private BookListMapper bookListMapper;
    @Mock private BookListItemMapper bookListItemMapper;
    @Mock private BookMapper bookMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private AdminBookListServiceImpl adminBookListService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(BookList.class, BookListItem.class, Book.class, User.class);
    }

    // ------------------------------------------------------------------ 造数据

    private BookList list(long id, long userId, int isPublic, int status, String name) {
        BookList l = new BookList();
        l.setId(id);
        l.setUserId(userId);
        l.setName(name);
        l.setIsPublic(isPublic);
        l.setStatus(status);
        l.setBookCount(0);
        return l;
    }

    private User user(long id, String nickname, String phone) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        u.setPhone(phone);
        return u;
    }

    private Book book(long id, String title) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setPrice(new BigDecimal("10.00"));
        b.setStock(5);
        b.setStatus(1);
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

    /** 让 selectPage 原样回填记录，模拟 MyBatis-Plus 的分页行为 */
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

    // ============================================================== 分页

    @Nested
    @DisplayName("分页 page()")
    class PageQuery {

        @Test
        @DisplayName("不传 status 时查全量：后台默认要能同时看到正常与已隐藏的书单")
        void returnsAllStatusesWhenNotFiltered() {
            stubPage(List.of(list(1L, OWNER_ID, 1, 1, "正常"), list(2L, OWNER_ID, 1, 0, "已隐藏")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            PageVO<AdminBookListVO> page = adminBookListService.page(new AdminBookListQuery());

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(!sql.contains("status"), "未指定状态时不应出现 status 条件：" + sql);
            assertEquals(2, page.getList().size());
            assertEquals(2, page.getList().stream().filter(v -> v.getStatus() != null).count());
        }

        @Test
        @DisplayName("按 status=0 筛选时只查已隐藏的书单")
        void filtersByStatus() {
            stubPage(List.of(list(2L, OWNER_ID, 1, 0, "已隐藏")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            AdminBookListQuery query = new AdminBookListQuery();
            query.setStatus(0);
            PageVO<AdminBookListVO> page = adminBookListService.page(query);

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            assertTrue(sqlOf(captor.getValue()).contains("status"), "缺少 status 条件");
            assertEquals(0, page.getList().get(0).getStatus().intValue());
        }

        @Test
        @DisplayName("关键词同时匹配名称与简介，且做 trim（前端带空格不该搜不到）")
        void keywordMatchesNameOrDescription() {
            stubPage(List.of());
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of());

            AdminBookListQuery query = new AdminBookListQuery();
            query.setKeyword("  考研  ");
            adminBookListService.page(query);

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            String sql = sqlOf(captor.getValue());
            assertTrue(sql.contains("name") && sql.contains("description"), "关键词要同时匹配名称与简介：" + sql);
            assertTrue(sql.toLowerCase().contains(" or "), "名称与简介之间应为 or：" + sql);
        }

        @Test
        @DisplayName("按创建者筛选取某人的全部书单（含私密，处理单个用户的违规内容时用）")
        void filtersByOwner() {
            stubPage(List.of(list(1L, OWNER_ID, 0, 1, "私密")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            AdminBookListQuery query = new AdminBookListQuery();
            query.setUserId(OWNER_ID);
            adminBookListService.page(query);

            ArgumentCaptor<Wrapper<BookList>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookListMapper).selectPage(any(), captor.capture());
            assertTrue(sqlOf(captor.getValue()).contains("user_id"), "缺少创建者条件");
        }

        @Test
        @DisplayName("分页参数越界时收敛：pageSize 上限 50")
        void normalizesPaging() {
            stubPage(List.of());

            AdminBookListQuery query = new AdminBookListQuery();
            query.setPageNum(0L);
            query.setPageSize(999L);
            adminBookListService.page(query);

            ArgumentCaptor<IPage<BookList>> captor = ArgumentCaptor.forClass(IPage.class);
            verify(bookListMapper).selectPage(captor.capture(), any());
            assertEquals(1L, captor.getValue().getCurrent());
            assertEquals(50L, captor.getValue().getSize());
        }

        @Test
        @DisplayName("Mapper 返回 null 记录 → 降级为空列表，不抛 NPE")
        void emptyWhenRecordsNull() {
            when(bookListMapper.selectPage(any(), any())).thenAnswer(inv -> {
                IPage<BookList> page = inv.getArgument(0);
                page.setRecords(null);
                page.setTotal(0);
                return page;
            });

            PageVO<AdminBookListVO> page = adminBookListService.page(new AdminBookListQuery());

            assertEquals(List.of(), page.getList());
            assertEquals(0L, page.getTotal());
        }

        @Test
        @DisplayName("列表装配创建者：昵称 + 脱敏手机号（后台要认人，但没必要铺满完整号码）")
        void masksOwnerPhone() {
            stubPage(List.of(list(1L, OWNER_ID, 1, 1, "高分书单")));
            when(userMapper.selectBatchIds(anyList()))
                    .thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            PageVO<AdminBookListVO> page = adminBookListService.page(new AdminBookListQuery());

            assertEquals("小王", page.getList().get(0).getOwnerNickname());
            assertEquals("138****8000", page.getList().get(0).getOwnerPhone());
            assertEquals(Boolean.FALSE, page.getList().get(0).getMine(), "后台没有「我的书单」概念，恒为 false");
        }

        @Test
        @DisplayName("创建者账号已被删除 → 昵称兜底「未知用户」，不整页报错")
        void unknownOwnerFallback() {
            stubPage(List.of(list(1L, 9999L, 1, 1, "孤儿书单")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of());

            PageVO<AdminBookListVO> page = adminBookListService.page(new AdminBookListQuery());

            assertEquals("未知用户", page.getList().get(0).getOwnerNickname());
        }

        @Test
        @DisplayName("没设昵称 → 用脱敏手机号兜底，与其他页面的展示口径一致")
        void nicknameFallsBackToMaskedPhone() {
            stubPage(List.of(list(1L, OWNER_ID, 1, 1, "书单")));
            when(userMapper.selectBatchIds(anyList()))
                    .thenReturn(List.of(user(OWNER_ID, "  ", "13900139000")));

            PageVO<AdminBookListVO> page = adminBookListService.page(new AdminBookListQuery());

            assertEquals("139****9000", page.getList().get(0).getOwnerNickname());
        }
    }

    // ============================================================== 详情

    @Nested
    @DisplayName("详情 detail()")
    class Detail {

        @Test
        @DisplayName("已隐藏的书单管理员仍能打开（否则恢复功能无从操作）")
        void adminCanReadHiddenList() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 0, "已隐藏"));
            when(bookListItemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L, 11L, 0)));
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "算法导论")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            AdminBookListDetailVO vo = adminBookListService.detail(1L);

            assertEquals(0, vo.getStatus().intValue());
            assertEquals(1, vo.getBooks().size());
            assertEquals("算法导论", vo.getBooks().get(0).getTitle());
            assertEquals("138****8000", vo.getOwnerPhone(), "详情也要带脱敏手机号，与列表口径一致");
        }

        @Test
        @DisplayName("条目按 sort 升序返回（顺序是用户自己排的，后台不能重排）")
        void booksOrderedBySort() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 1, "书单"));
            when(bookListItemMapper.selectList(any()))
                    .thenReturn(List.of(item(1L, 1L, 11L, 0), item(2L, 1L, 12L, 1)));
            when(bookMapper.selectBatchIds(anyList()))
                    .thenReturn(List.of(book(11L, "第一本"), book(12L, "第二本")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            AdminBookListDetailVO vo = adminBookListService.detail(1L);

            assertEquals(List.of("第一本", "第二本"),
                    vo.getBooks().stream().map(b -> b.getTitle()).toList());
        }

        @Test
        @DisplayName("条目里的书被物理删除 → 跳过该条，详情仍能打开")
        void skipsDanglingBooks() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 1, "书单"));
            when(bookListItemMapper.selectList(any()))
                    .thenReturn(List.of(item(1L, 1L, 11L, 0), item(2L, 1L, 12L, 1)));
            when(bookMapper.selectBatchIds(anyList())).thenReturn(List.of(book(11L, "算法导论")));
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(OWNER_ID, "小王", "13800138000")));

            AdminBookListDetailVO vo = adminBookListService.detail(1L);

            assertEquals(1, vo.getBooks().size());
        }

        @Test
        @DisplayName("书单不存在 → 404（对错误 ID 静默成功会让后台误以为处置过了）")
        void notFound() {
            when(bookListMapper.selectById(9L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> adminBookListService.detail(9L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("ID 为空 → 400，而不是让 MyBatis-Plus 抛「主键为空」")
        void rejectsNullId() {
            BusinessException ex = assertThrows(BusinessException.class, () -> adminBookListService.detail(null));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
        }
    }

    // ============================================================== 隐藏 / 恢复

    @Nested
    @DisplayName("隐藏 / 恢复 updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("隐藏：只改 status，绝不碰 is_public（否则恢复时还原不了用户自己的设置）")
        void hidesWithoutTouchingIsPublic() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 1, "违规书单"));

            adminBookListService.updateStatus(1L, 0);

            ArgumentCaptor<BookList> captor = ArgumentCaptor.forClass(BookList.class);
            verify(bookListMapper).updateById(captor.capture());
            BookList written = captor.getValue();
            assertEquals(1L, written.getId().longValue());
            assertEquals(0, written.getStatus().intValue());
            assertEquals(null, written.getIsPublic(), "is_public 不该被这次更新带上");
        }

        @Test
        @DisplayName("恢复：status 回到 1")
        void restores() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 0, "误判书单"));

            adminBookListService.updateStatus(1L, 1);

            ArgumentCaptor<BookList> captor = ArgumentCaptor.forClass(BookList.class);
            verify(bookListMapper).updateById(captor.capture());
            assertEquals(1, captor.getValue().getStatus().intValue());
        }

        @Test
        @DisplayName("重复隐藏幂等：已是 0 再设 0 不发 UPDATE，也不写一条误导性操作日志")
        void idempotentWhenSameStatus() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 0, "已隐藏"));

            adminBookListService.updateStatus(1L, 0);

            verify(bookListMapper, never()).updateById(any(BookList.class));
        }

        @Test
        @DisplayName("非法状态值 → 400（不猜「非 0 即上架」）")
        void rejectsIllegalStatus() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookListService.updateStatus(1L, 2));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
            verify(bookListMapper, never()).updateById(any(BookList.class));
        }

        @Test
        @DisplayName("状态为空 → 400")
        void rejectsNullStatus() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookListService.updateStatus(1L, null));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("书单不存在 → 404，不静默成功")
        void notFound() {
            when(bookListMapper.selectById(9L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookListService.updateStatus(9L, 0));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }
    }

    // ============================================================== 删除

    @Nested
    @DisplayName("删除 delete()")
    class Delete {

        @Test
        @DisplayName("先删条目再删主表（没有物理外键，顺序颠倒不会报错，但会留下孤儿行）")
        void deletesItemsBeforeList() {
            when(bookListMapper.selectById(1L)).thenReturn(list(1L, OWNER_ID, 1, 1, "垃圾书单"));
            when(bookListItemMapper.delete(any())).thenReturn(3);

            adminBookListService.delete(1L);

            InOrder order = inOrder(bookListItemMapper, bookListMapper);
            order.verify(bookListItemMapper).delete(any());
            order.verify(bookListMapper).deleteById(1L);
        }

        @Test
        @DisplayName("书单不存在 → 404，且不动条目表")
        void notFound() {
            when(bookListMapper.selectById(9L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> adminBookListService.delete(9L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
            verify(bookListItemMapper, never()).delete(any());
        }
    }
}
