package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Cart;
import cn.edu.bookstore.mapper.CartMapper;
import cn.edu.bookstore.model.dto.CartAddRequest;
import cn.edu.bookstore.model.dto.CartMergeRequest;
import cn.edu.bookstore.model.dto.CartUpdateRequest;
import cn.edu.bookstore.model.vo.CartListVO;
import cn.edu.bookstore.model.vo.CartMergeResultVO;
import cn.edu.bookstore.model.vo.CartVO;
import cn.edu.bookstore.service.BookService;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CartServiceImpl} 单元测试。
 *
 * <p>购物车看似只是增删改查，但它承担了两件容易出问题的事：
 * <ul>
 *     <li><b>越权</b>：所有按条目 ID 的操作（改数量、删除）都必须先验证归属，
 *         否则换个 ID 就能操作别人的购物车；</li>
 *     <li><b>数量收敛</b>：加入数量超过库存或超过单品上限（99）时，是「报错」还是
 *         「收敛到能买的上限」，直接决定用户体验 —— 本地购物车合并（merge）选择收敛，
 *         手动加购（add）选择报错，两条路径的差异必须被测试固定下来。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CartServiceImpl —— 加购 / 合并 / 列表 / 修改 / 删除")
class CartServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;
    private static final int STATUS_ON_SALE = 1;

    @Mock private CartMapper cartMapper;
    @Mock private BookService bookService;

    @InjectMocks private CartServiceImpl cartService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不起容器，lambda 条件构造器需要手工注册实体元数据（详见 MybatisPlusTestSupport）
        MybatisPlusTestSupport.initTableInfo(Book.class, Cart.class);
    }

    /** 去掉反引号与多余空白后再断言 SQL 片段，免得为「列名加不加引号」这种实现细节写死断言 */
    private static String normalize(String sql) {
        return sql == null ? "" : sql.replace("`", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ 造数据

    private Book book(long id, String title, String price, int stock, int status) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setCoverUrl("/static/cover/" + id + ".jpg");
        b.setPrice(new BigDecimal(price));
        b.setStock(stock);
        b.setStatus(status);
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

    private CartAddRequest addRequest(long bookId, Integer quantity) {
        CartAddRequest r = new CartAddRequest();
        r.setBookId(bookId);
        r.setQuantity(quantity);
        return r;
    }

    private void stubBook(Book b) {
        when(bookService.mapByIds(anyList())).thenReturn(Map.of(b.getId(), b));
    }

    // ================================================================== 加购

    @Nested
    @DisplayName("加购 add()")
    class Add {

        @Test
        @DisplayName("图书不存在或已下架 → 422")
        void rejectsOffSaleBook() {
            stubBook(book(1L, "下架书", "20.00", 10, 0));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.add(USER_ID, addRequest(1L, 1)));

            assertTrue(ex.getMessage().contains("图书不存在或已下架"));
            verify(cartMapper, never()).insert(any(Cart.class));
        }

        @Test
        @DisplayName("首次加购：新建条目并默认勾选")
        void insertsNewEntryChecked() {
            stubBook(book(1L, "算法导论", "25.50", 10, STATUS_ON_SALE));
            when(cartMapper.selectOne(any())).thenReturn(null);

            cartService.add(USER_ID, addRequest(1L, 2));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).insert(captor.capture());
            assertEquals(USER_ID, captor.getValue().getUserId());
            assertEquals(1L, captor.getValue().getBookId());
            assertEquals(2, captor.getValue().getQuantity());
            assertEquals(1, captor.getValue().getChecked(), "新加购的条目应当默认勾选");
        }

        @Test
        @DisplayName("重复加购：在已有数量上累加，而不是新增一条")
        void accumulatesExistingQuantity() {
            stubBook(book(1L, "算法导论", "25.50", 10, STATUS_ON_SALE));
            when(cartMapper.selectOne(any())).thenReturn(cart(11L, 1L, 2, 1));

            cartService.add(USER_ID, addRequest(1L, 3));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).updateById(captor.capture());
            assertEquals(5, captor.getValue().getQuantity());
            assertEquals(11L, captor.getValue().getId());
            verify(cartMapper, never()).insert(any(Cart.class));
        }

        @Test
        @DisplayName("累加后超过库存 → 422（错误信息带上剩余库存）")
        void rejectsWhenExceedsStock() {
            stubBook(book(1L, "稀缺书", "20.00", 10, STATUS_ON_SALE));
            when(cartMapper.selectOne(any())).thenReturn(cart(11L, 1L, 8, 1));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.add(USER_ID, addRequest(1L, 3)));

            assertTrue(ex.getMessage().contains("仅剩 10 件"));
            verify(cartMapper, never()).updateById(any(Cart.class));
        }

        @Test
        @DisplayName("累加后超过单品上限 99 → 422（即便库存充足）")
        void rejectsWhenExceedsMaxQuantity() {
            stubBook(book(1L, "畅销书", "20.00", 500, STATUS_ON_SALE));
            when(cartMapper.selectOne(any())).thenReturn(cart(11L, 1L, 90, 1));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.add(USER_ID, addRequest(1L, 20)));

            assertTrue(ex.getMessage().contains("最多购买 99 件"));
        }

        @Test
        @DisplayName("未传数量时默认加 1 件")
        void defaultsQuantityToOne() {
            stubBook(book(1L, "书", "10.00", 10, STATUS_ON_SALE));
            when(cartMapper.selectOne(any())).thenReturn(null);

            cartService.add(USER_ID, addRequest(1L, null));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getQuantity());
        }
    }

    // ================================================================== 列表

    @Nested
    @DisplayName("列表 list()")
    class ListCart {

        @Test
        @DisplayName("空购物车：合计 0、件数 0，仍返回列表结构（APP 端无需判空）")
        void returnsEmptyStructure() {
            when(cartMapper.selectList(any())).thenReturn(List.of());

            CartListVO vo = cartService.list(USER_ID);

            assertTrue(vo.getList().isEmpty());
            assertEquals(0, BigDecimal.ZERO.compareTo(vo.getCheckedAmount()));
            assertEquals(0, vo.getCheckedCount());
        }

        @Test
        @DisplayName("合计只统计「已勾选 + 已上架 + 库存充足」的条目")
        void computesCheckedAmountByEligibility() {
            when(cartMapper.selectList(any())).thenReturn(List.of(
                    cart(11L, 1L, 2, 1),   // 勾选、10.00×2 = 20.00 → 计入
                    cart(12L, 2L, 1, 0),   // 未勾选 → 不计入
                    cart(13L, 3L, 1, 1),   // 已下架 → 不计入
                    cart(14L, 4L, 9, 1))); // 库存不足 → 不计入
            when(bookService.mapByIds(anyList())).thenReturn(Map.of(
                    1L, book(1L, "书A", "10.00", 5, STATUS_ON_SALE),
                    2L, book(2L, "书B", "30.00", 5, STATUS_ON_SALE),
                    3L, book(3L, "书C", "40.00", 5, 0),
                    4L, book(4L, "书D", "50.00", 2, STATUS_ON_SALE)));

            CartListVO vo = cartService.list(USER_ID);

            assertEquals(4, vo.getList().size(), "下架/库存不足的条目仍要展示，只是不计入合计");
            assertEquals(0, new BigDecimal("20.00").compareTo(vo.getCheckedAmount()));
            assertEquals(2, vo.getCheckedCount());
        }

        @Test
        @DisplayName("每条明细带小计与「库存是否充足」标记，供 APP 端直接渲染")
        void fillsSubtotalAndStockEnough() {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 1L, 3, 1)));
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "书", "12.50", 2, STATUS_ON_SALE)));

            CartListVO vo = cartService.list(USER_ID);

            CartVO first = vo.getList().get(0);
            assertEquals(0, new BigDecimal("37.50").compareTo(first.getSubtotal()));
            assertFalse(first.getStockEnough(), "要 3 件只剩 2 件，应标记库存不足");
        }

        @Test
        @DisplayName("图书已从库中消失（book 为 null）时不抛异常，标记库存不足")
        void toleratesMissingBook() {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart(11L, 999L, 1, 1)));
            when(bookService.mapByIds(anyList())).thenReturn(Map.of());

            CartListVO vo = cartService.list(USER_ID);

            assertEquals(1, vo.getList().size());
            assertFalse(vo.getList().get(0).getStockEnough());
            assertEquals(0, BigDecimal.ZERO.compareTo(vo.getCheckedAmount()));
        }
    }

    // ================================================================== 修改

    @Nested
    @DisplayName("修改 update()")
    class Update {

        private CartUpdateRequest updateRequest(Integer quantity, Integer checked) {
            CartUpdateRequest r = new CartUpdateRequest();
            r.setQuantity(quantity);
            r.setChecked(checked);
            return r;
        }

        @Test
        @DisplayName("条目不存在 → 404")
        void rejectsMissingCart() {
            when(cartMapper.selectById(11L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.update(USER_ID, 11L, updateRequest(1, 1)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("操作他人条目 → 403（越权拦截）")
        void rejectsOtherUsersCart() {
            Cart other = cart(11L, 1L, 1, 1);
            other.setUserId(OTHER_USER_ID);
            when(cartMapper.selectById(11L)).thenReturn(other);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.update(USER_ID, 11L, updateRequest(1, 1)));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
            verify(cartMapper, never()).updateById(any(Cart.class));
        }

        @Test
        @DisplayName("勾选值被归一化为 1/0（前端传 5 也只当选中）")
        void normalizesCheckedFlag() {
            when(cartMapper.selectById(11L)).thenReturn(cart(11L, 1L, 1, 0));

            cartService.update(USER_ID, 11L, updateRequest(null, 5));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).updateById(captor.capture());
            assertEquals(1, captor.getValue().getChecked());
        }

        @Test
        @DisplayName("改数量时校验库存，超库存 → 422")
        void rejectsWhenQuantityExceedsStock() {
            when(cartMapper.selectById(11L)).thenReturn(cart(11L, 1L, 1, 1));
            stubBook(book(1L, "书", "10.00", 3, STATUS_ON_SALE));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.update(USER_ID, 11L, updateRequest(5, null)));

            assertTrue(ex.getMessage().contains("仅剩 3 件"));
        }
    }

    // ================================================================== 删除

    @Nested
    @DisplayName("删除 remove() / removeBatch()")
    class Remove {

        @Test
        @DisplayName("删除他人条目 → 403，且不执行删除")
        void removeRejectsOtherUsersCart() {
            Cart other = cart(11L, 1L, 1, 1);
            other.setUserId(OTHER_USER_ID);
            when(cartMapper.selectById(11L)).thenReturn(other);

            assertThrows(BusinessException.class, () -> cartService.remove(USER_ID, 11L));

            verify(cartMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("删除自己的条目：先校验归属再删除")
        void removesOwnedEntry() {
            when(cartMapper.selectById(11L)).thenReturn(cart(11L, 1L, 1, 1));

            cartService.remove(USER_ID, 11L);

            verify(cartMapper).deleteById(11L);
        }

        @Test
        @DisplayName("批量删除未选择任何条目 → 400")
        void removeBatchRejectsEmptyIds() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.removeBatch(USER_ID, List.of()));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
            verify(cartMapper, never()).delete(any());
        }

        @Test
        @DisplayName("批量删除限定当前用户，删除条件带 user_id（越权防线）")
        void removeBatchScopesToCurrentUser() {
            when(cartMapper.delete(any())).thenReturn(2);

            cartService.removeBatch(USER_ID, List.of(11L, 12L));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<Cart>> captor =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
            verify(cartMapper).delete(captor.capture());
            String sql = normalize(captor.getValue().getSqlSegment());
            assertTrue(sql.contains("user_id"), "删除条件缺少 user_id，可能删到别人的购物车：" + sql);
            assertTrue(sql.contains("IN"), "未按条目 ID 过滤：" + sql);
        }
    }

    // ============================================================ 本地购物车合并

    @Nested
    @DisplayName("本地购物车合并 merge()")
    class Merge {

        private CartMergeRequest mergeRequest(long... bookIds) {
            CartMergeRequest r = new CartMergeRequest();
            r.setItems(java.util.Arrays.stream(bookIds)
                    .mapToObj(id -> {
                        CartMergeRequest.Item item = new CartMergeRequest.Item();
                        item.setBookId(id);
                        item.setQuantity(1);
                        return item;
                    }).toList());
            return r;
        }

        /** 单本书 + 指定数量，用于验证「超库存收敛」这类与数量强相关的规则 */
        private CartMergeRequest mergeRequestWithQuantity(long bookId, int quantity) {
            CartMergeRequest r = new CartMergeRequest();
            CartMergeRequest.Item item = new CartMergeRequest.Item();
            item.setBookId(bookId);
            item.setQuantity(quantity);
            r.setItems(List.of(item));
            return r;
        }

        @Test
        @DisplayName("已下架/不存在的图书整条跳过，并记录跳过原因")
        void skipsOffSaleBook() {
            stubBook(book(1L, "下架书", "20.00", 10, 0));
            when(cartMapper.selectList(any())).thenReturn(List.of());

            CartMergeResultVO result = cartService.merge(USER_ID, mergeRequest(1L));

            assertEquals(0, result.getMergedCount());
            assertEquals(1, result.getSkipped().size());
            assertEquals("已下架或不存在", result.getSkipped().get(0).getReason());
            verify(cartMapper, never()).insert(any(Cart.class));
        }

        @Test
        @DisplayName("数量超过库存上限时收敛到上限，而不是整条丢弃")
        void convergesQuantityToStockCap() {
            stubBook(book(1L, "稀缺书", "20.00", 1, STATUS_ON_SALE));
            when(cartMapper.selectList(any())).thenReturn(List.of());

            CartMergeResultVO result = cartService.merge(USER_ID, mergeRequestWithQuantity(1L, 5));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getQuantity(), "应把数量收敛到库存上限 1");
            assertEquals(1, result.getMergedCount());
            assertEquals(1, result.getSkipped().size());
            assertTrue(result.getSkipped().get(0).getReason().contains("数量已调整为上限"));
        }

        @Test
        @DisplayName("同时存在的条目累加数量，不存在的条目新建")
        void insertsNewAndUpdatesExisting() {
            when(bookService.mapByIds(anyList())).thenReturn(Map.of(
                    1L, book(1L, "已购书", "10.00", 10, STATUS_ON_SALE),
                    2L, book(2L, "新书", "20.00", 10, STATUS_ON_SALE)));
            // merge 内部两次 selectList：先取「已存在条目」，再算「合计件数」
            when(cartMapper.selectList(any()))
                    .thenReturn(List.of(cart(11L, 1L, 2, 1)), List.of(cart(11L, 1L, 3, 1)));

            CartMergeResultVO result = cartService.merge(USER_ID, mergeRequest(1L, 2L));

            ArgumentCaptor<Cart> updateCaptor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).updateById(updateCaptor.capture());
            assertEquals(3, updateCaptor.getValue().getQuantity(), "已有条目应在原数量上累加");

            ArgumentCaptor<Cart> insertCaptor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper).insert(insertCaptor.capture());
            assertEquals(2L, insertCaptor.getValue().getBookId());

            assertEquals(2, result.getMergedCount());
            assertEquals(3, result.getTotalQuantity());
        }

        @Test
        @DisplayName("合并后回传购物车总件数，供 APP 端提示「已同步 N 件商品」")
        void returnsTotalQuantity() {
            when(bookService.mapByIds(anyList()))
                    .thenReturn(Map.of(1L, book(1L, "书", "10.00", 10, STATUS_ON_SALE)));
            when(cartMapper.selectList(any())).thenReturn(List.of(), List.of(cart(11L, 1L, 7, 1)));

            CartMergeResultVO result = cartService.merge(USER_ID, mergeRequest(1L));

            assertEquals(7, result.getTotalQuantity());
        }

        @Test
        @DisplayName("重复提交同一本书时先合并数量再落库（本地购物车可能产生重复条目）")
        void deduplicatesSameBook() {
            stubBook(book(1L, "书", "10.00", 10, STATUS_ON_SALE));
            when(cartMapper.selectList(any())).thenReturn(List.of(), List.of());

            CartMergeRequest request = new CartMergeRequest();
            CartMergeRequest.Item i1 = new CartMergeRequest.Item();
            i1.setBookId(1L);
            i1.setQuantity(2);
            CartMergeRequest.Item i2 = new CartMergeRequest.Item();
            i2.setBookId(1L);
            i2.setQuantity(3);
            request.setItems(List.of(i1, i2));

            cartService.merge(USER_ID, request);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartMapper, times(1)).insert(captor.capture());
            assertEquals(5, captor.getValue().getQuantity(), "同书两次提交应合并为一条共 5 件");
        }
    }
}
