package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.entity.OrderItem;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.model.dto.AdminBookQuery;
import cn.edu.bookstore.model.dto.BookSaveRequest;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.CategoryService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminBookServiceImpl} 单元测试。
 *
 * <p>管理端图书维护是整个系统里「破坏力最强」的一组操作：改错了会直接影响
 * 所有用户的浏览与下单。这里重点固定四条不可回退的规则：
 * <ol>
 *     <li><b>同名同作者判重</b>（409）—— 数据库有唯一约束，但业务层要先给出可读提示；</li>
 *     <li><b>销量归集于下单流程</b> —— 管理端新增固定为 0、修改时保留原值，
 *         否则运营手一抖就能把销量排行改乱；</li>
 *     <li><b>已产生订单的图书只能下架不能删除</b>（409）—— 删掉会让历史订单的明细失去参照；</li>
 *     <li><b>分类必须存在</b> —— 避免写入指向不存在分类的脏数据。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBookServiceImpl —— 图书新增 / 修改 / 上下架 / 删除")
class AdminBookServiceImplTest {

    @Mock private BookMapper bookMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private CategoryService categoryService;

    @InjectMocks private AdminBookServiceImpl adminBookService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不起容器，lambda 条件构造器需要手工注册实体元数据（详见 MybatisPlusTestSupport）
        MybatisPlusTestSupport.initTableInfo(Book.class, Category.class, OrderItem.class);
    }

    /** 去掉反引号与多余空白后再断言 SQL 片段，免得为「列名加不加引号」这种实现细节写死断言 */
    private static String normalize(String sql) {
        return sql == null ? "" : sql.replace("`", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ 造数据

    private Book book(long id, String title, int sales, int status) {
        Book b = new Book();
        b.setId(id);
        b.setCategoryId(1L);
        b.setTitle(title);
        b.setAuthor("作者");
        b.setPublisher("出版社");
        b.setPrice(new BigDecimal("39.00"));
        b.setStock(10);
        b.setSales(sales);
        b.setStatus(status);
        return b;
    }

    private BookSaveRequest saveRequest(String title, String author) {
        BookSaveRequest r = new BookSaveRequest();
        r.setCategoryId(1L);
        r.setTitle(title);
        r.setAuthor(author);
        r.setPublisher("出版社");
        r.setPrice(new BigDecimal("39.00"));
        r.setStock(10);
        r.setCoverUrl("/static/cover/1.jpg");
        r.setDescription("简介");
        r.setStatus(1);
        return r;
    }

    private CategoryVO categoryVO(long id, String name) {
        CategoryVO v = new CategoryVO();
        v.setId(id);
        v.setName(name);
        return v;
    }

    /** 分类存在 + 无同名图书，让流程能走到落库那一步 */
    private void stubHappyPath() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);
        when(bookMapper.selectCount(any())).thenReturn(0L);
    }

    // ================================================================== 新增

    @Nested
    @DisplayName("新增 create()")
    class Create {

        @Test
        @DisplayName("所选分类不存在 → 400，拒绝写入脏数据")
        void rejectsMissingCategory() {
            when(categoryMapper.selectCount(any())).thenReturn(0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookService.create(saveRequest("新书", "作者")));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("所选分类不存在"));
            verify(bookMapper, never()).insert(any(Book.class));
        }

        @Test
        @DisplayName("同名同作者已存在 → 409")
        void rejectsDuplicateTitleAndAuthor() {
            when(categoryMapper.selectCount(any())).thenReturn(1L);
            when(bookMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookService.create(saveRequest("重复书", "作者")));

            assertEquals(ErrorCode.CONFLICT.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("已存在同名图书"));
            verify(bookMapper, never()).insert(any(Book.class));
        }

        @Test
        @DisplayName("新增时销量强制为 0（销量只由下单流程累加）")
        void forcesSalesToZero() {
            stubHappyPath();
            when(bookMapper.insert(any(Book.class))).thenAnswer(inv -> {
                inv.getArgument(0, Book.class).setId(5001L);
                return 1;
            });

            Long id = adminBookService.create(saveRequest("新书", "作者"));

            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
            verify(bookMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getSales());
            assertEquals(5001L, id);
        }

        @Test
        @DisplayName("书名去首尾空格；作者/出版社为空串时归一化为 null")
        void trimsAndNullifiesBlankFields() {
            stubHappyPath();
            when(bookMapper.insert(any(Book.class))).thenReturn(1);
            BookSaveRequest request = saveRequest("  三体  ", "   ");
            request.setPublisher(null);

            adminBookService.create(request);

            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
            verify(bookMapper).insert(captor.capture());
            assertEquals("三体", captor.getValue().getTitle());
            assertNull(captor.getValue().getAuthor(), "空白作者应存为 null，而不是空串");
            assertNull(captor.getValue().getPublisher());
        }
    }

    // ================================================================== 修改

    @Nested
    @DisplayName("修改 update()")
    class Update {

        @Test
        @DisplayName("图书不存在 → 404")
        void rejectsMissingBook() {
            when(bookMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookService.update(999L, saveRequest("书", "作者")));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("修改时保留原销量，不被表单覆盖")
        void keepsOriginalSales() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "旧书名", 88, 1));
            stubHappyPath();

            adminBookService.update(1L, saveRequest("新书名", "作者"));

            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
            verify(bookMapper).updateById(captor.capture());
            assertEquals(88, captor.getValue().getSales(), "销量必须沿用原值");
            assertEquals(1L, captor.getValue().getId());
            assertEquals("新书名", captor.getValue().getTitle());
        }

        @Test
        @DisplayName("判重时排除自身 ID（改自己的书不该被判为重复）")
        void excludesSelfWhenCheckingDuplicate() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "旧书名", 88, 1));
            stubHappyPath();

            adminBookService.update(1L, saveRequest("新书名", "作者"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<Book>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bookMapper).selectCount(captor.capture());
            String sql = normalize(captor.getValue().getSqlSegment());
            assertTrue(sql.contains("id <"), "判重未排除自身：" + sql);
            assertTrue(sql.contains("title"), "判重未按书名：" + sql);
        }
    }

    // ============================================================== 上下架

    @Nested
    @DisplayName("上下架 updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("状态值不合法（非 0/1）→ 400")
        void rejectsIllegalValue() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "书", 0, 1));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookService.updateStatus(1L, 2));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
            verify(bookMapper, never()).updateById(any(Book.class));
        }

        @Test
        @DisplayName("状态为空 → 400")
        void rejectsNullValue() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "书", 0, 1));

            assertThrows(BusinessException.class, () -> adminBookService.updateStatus(1L, null));

            verify(bookMapper, never()).updateById(any(Book.class));
        }

        @Test
        @DisplayName("下架：只提交 id 与 status 两个字段（不整行覆盖，避免并发丢更新）")
        void updatesOnlyStatusField() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "书", 0, 1));

            adminBookService.updateStatus(1L, 0);

            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
            verify(bookMapper).updateById(captor.capture());
            assertEquals(0, captor.getValue().getStatus());
            assertEquals(1L, captor.getValue().getId());
            assertNull(captor.getValue().getTitle(), "上下架不应顺带覆盖其他字段");
            assertNull(captor.getValue().getPrice());
        }
    }

    // ================================================================== 删除

    @Nested
    @DisplayName("删除 delete()")
    class Delete {

        @Test
        @DisplayName("已产生订单的图书 → 409，提示改为下架")
        void rejectsBookWithOrders() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "热销书", 5, 1));
            when(orderItemMapper.selectCount(any())).thenReturn(3L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBookService.delete(1L));

            assertEquals(ErrorCode.CONFLICT.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("请改为下架"));
            verify(bookMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("无订单记录才允许物理删除")
        void removesWhenNoOrders() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "新书", 0, 1));
            when(orderItemMapper.selectCount(any())).thenReturn(0L);

            adminBookService.delete(1L);

            verify(bookMapper).deleteById(1L);
        }

        @Test
        @DisplayName("图书不存在 → 404，不执行删除")
        void rejectsMissingBook() {
            when(bookMapper.selectById(1L)).thenReturn(null);

            assertThrows(BusinessException.class, () -> adminBookService.delete(1L));

            verify(bookMapper, never()).deleteById(any(Long.class));
        }
    }

    // ================================================================== 查询

    @Nested
    @DisplayName("查询 detail() / page()")
    class Query {

        @Test
        @DisplayName("详情带上分类名称（管理端列表要展示中文分类，而不是 ID）")
        void detailReturnsCategoryName() {
            when(bookMapper.selectById(1L)).thenReturn(book(1L, "三体", 10, 1));
            when(categoryService.list()).thenReturn(List.of(categoryVO(1L, "科幻文学")));

            BookVO vo = adminBookService.detail(1L);

            assertEquals("三体", vo.getTitle());
            assertEquals("科幻文学", vo.getCategoryName());
        }

        @Test
        @DisplayName("分类映射只加载一次，避免逐行查询造成 N+1")
        void pageLoadsCategoryMapOnce() {
            Page<Book> page = new Page<>(1, 10);
            page.setRecords(List.of(book(1L, "书A", 1, 1), book(2L, "书B", 2, 1), book(3L, "书C", 3, 1)));
            page.setTotal(3);
            when(bookMapper.selectPage(any(), any())).thenReturn(page);
            when(categoryService.list()).thenReturn(List.of(categoryVO(1L, "科幻文学")));

            PageVO<BookVO> vo = adminBookService.page(new AdminBookQuery());

            verify(categoryService, times(1)).list();
            assertEquals(3, vo.getList().size());
            assertEquals("科幻文学", vo.getList().get(0).getCategoryName());
        }

        @Test
        @DisplayName("分页参数归一化：pageSize 超限收敛到 50")
        void pageNormalizesQuery() {
            Page<Book> page = new Page<>(1, 50);
            page.setRecords(List.of());
            page.setTotal(0);
            when(bookMapper.selectPage(any(), any())).thenReturn(page);
            when(categoryService.list()).thenReturn(List.of());
            AdminBookQuery query = new AdminBookQuery();
            query.setPageSize(9999L);

            adminBookService.page(query);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<IPage<Book>> captor = ArgumentCaptor.forClass(IPage.class);
            verify(bookMapper).selectPage(captor.capture(), any());
            assertEquals(50L, captor.getValue().getSize());
        }
    }
}
