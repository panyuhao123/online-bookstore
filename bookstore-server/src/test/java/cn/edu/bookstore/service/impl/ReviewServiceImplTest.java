package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.entity.Review;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.mapper.ReviewMapper;
import cn.edu.bookstore.model.dto.ReviewCreateRequest;
import cn.edu.bookstore.model.vo.BookRatingStatVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.ReviewSummaryVO;
import cn.edu.bookstore.model.vo.ReviewVO;
import cn.edu.bookstore.model.vo.ReviewableItemVO;
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
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价模块业务规则单元测试。
 *
 * <p>为什么重点测「四道购买资格校验」：
 * 评价体系的全部价值都建立在「评价来自真实购买」之上 ——
 * 只要有一道校验能被绕过，评分就失去参考价值，功能做了等于没做。
 * 这四道校验不可能靠手工点界面穷举验证（组合太多），
 * 用 Mock 把数据库挡掉之后就能把每种拒绝理由都断言到位。
 *
 * <p>这里不启动 Spring 上下文，也不连数据库 / Redis：
 * 单测只回答「业务判断对不对」，集成层面的问题由 HTTP 冒烟脚本负责。
 * 因此 {@code @CacheEvict} 之类的注解在本测试中不会生效 —— 这是符合预期的，
 * 缓存一致性由冒烟脚本里「写后立即读」的断言来保证。
 *
 * @author 毕设项目组
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("评价模块业务规则")
class ReviewServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long BOOK_ID = 6L;
    private static final Long ORDER_ID = 10L;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrdersMapper ordersMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    /** 构造一笔指定状态、指定归属用户的订单 */
    private Orders order(Long id, Long userId, OrderStatus status) {
        Orders orders = new Orders();
        orders.setId(id);
        orders.setUserId(userId);
        orders.setStatus(status.getCode());
        orders.setOrderNo("BK202610161600001234");
        orders.setTotalAmount(new BigDecimal("28.00"));
        return orders;
    }

    private ReviewCreateRequest request(Long orderId, Long bookId, int rating) {
        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(orderId);
        req.setBookId(bookId);
        req.setRating(rating);
        req.setContent("内容扎实，讲解清晰。");
        req.setAnonymous(Boolean.FALSE);
        return req;
    }

    @Nested
    @DisplayName("发表评价的四道资格校验")
    class CreateGates {

        @Test
        @DisplayName("校验1：订单不存在 → 404")
        void shouldRejectWhenOrderMissing() {
            when(ordersMapper.selectById(ORDER_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.create(USER_ID, request(ORDER_ID, BOOK_ID, 5)));

            assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
            verify(reviewMapper, never()).insert(any(Review.class));
        }

        @Test
        @DisplayName("校验1：订单不属于当前用户 → 403（防止评价他人订单里的书）")
        void shouldRejectWhenOrderBelongsToOthers() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, OTHER_USER_ID, OrderStatus.COMPLETED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.create(USER_ID, request(ORDER_ID, BOOK_ID, 5)));

            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
            verify(reviewMapper, never()).insert(any(Review.class));
        }

        @Test
        @DisplayName("校验2：订单未完成 → 422（没收到货就谈不上评价）")
        void shouldRejectWhenOrderNotCompleted() {
            for (OrderStatus status : List.of(OrderStatus.PENDING_PAY, OrderStatus.PAID)) {
                when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, USER_ID, status));

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> reviewService.create(USER_ID, request(ORDER_ID, BOOK_ID, 5)),
                        "订单状态「" + status.getText() + "」不应允许评价");

                assertEquals(ErrorCode.BIZ_ERROR, ex.getErrorCode());
                assertTrue(ex.getMessage().contains(status.getText()),
                        "错误提示应告知当前订单状态，实际：" + ex.getMessage());
            }
            verify(reviewMapper, never()).insert(any(Review.class));
        }

        @Test
        @DisplayName("校验3：订单里没有这本书 → 422（防止拿任意订单评价任意图书）")
        void shouldRejectWhenBookNotInOrder() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(orderItemMapper.selectCount(any())).thenReturn(0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.create(USER_ID, request(ORDER_ID, BOOK_ID, 5)));

            assertEquals(ErrorCode.BIZ_ERROR, ex.getErrorCode());
            verify(reviewMapper, never()).insert(any(Review.class));
        }

        @Test
        @DisplayName("校验4：同一订单同一本书重复评价 → 409（不能刷分）")
        void shouldRejectDuplicateReview() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(orderItemMapper.selectCount(any())).thenReturn(1L);
            when(reviewMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.create(USER_ID, request(ORDER_ID, BOOK_ID, 5)));

            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
            verify(reviewMapper, never()).insert(any(Review.class));
        }

        @Test
        @DisplayName("并发兜底：唯一键冲突被转换为 409 而不是 500")
        void shouldConvertDuplicateKeyException() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(orderItemMapper.selectCount(any())).thenReturn(1L);
            when(reviewMapper.selectCount(any())).thenReturn(0L);
            // 两个请求同时通过了业务层检查，数据库唯一键拦住其中一个
            when(reviewMapper.insert(any(Review.class)))
                    .thenThrow(new DuplicateKeyException("uk_review_user_book_order"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.create(USER_ID, request(ORDER_ID, BOOK_ID, 5)));

            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }

        @Test
        @DisplayName("通过校验：入库字段正确（评价人取服务端、状态置正常）")
        void shouldPersistReviewOnSuccess() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(orderItemMapper.selectCount(any())).thenReturn(1L);
            when(reviewMapper.selectCount(any())).thenReturn(0L);
            when(reviewMapper.insert(any(Review.class))).thenReturn(1);

            ReviewCreateRequest req = request(ORDER_ID, BOOK_ID, 4);
            req.setAnonymous(Boolean.TRUE);
            reviewService.create(USER_ID, req);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewMapper).insert(captor.capture());
            Review saved = captor.getValue();

            assertEquals(BOOK_ID, saved.getBookId());
            assertEquals(USER_ID, saved.getUserId(), "评价人必须取登录用户，不能由请求参数指定");
            assertEquals(ORDER_ID, saved.getOrderId());
            assertEquals(4, saved.getRating().intValue());
            assertEquals(1, saved.getAnonymous().intValue(), "anonymous=true 应落库为 1");
            assertEquals(1, saved.getStatus().intValue(), "新评价默认状态为 1（正常）");
            assertEquals("内容扎实，讲解清晰。", saved.getContent());
        }

        @Test
        @DisplayName("内容为空白时落库为 null，避免出现「全是空格」的评价")
        void shouldStoreNullForBlankContent() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(orderItemMapper.selectCount(any())).thenReturn(1L);
            when(reviewMapper.selectCount(any())).thenReturn(0L);
            when(reviewMapper.insert(any(Review.class))).thenReturn(1);

            ReviewCreateRequest req = request(ORDER_ID, BOOK_ID, 5);
            req.setContent("   ");
            reviewService.create(USER_ID, req);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewMapper).insert(captor.capture());
            assertNull(captor.getValue().getContent());
            assertEquals(0, captor.getValue().getAnonymous().intValue(), "未勾选匿名应为 0");
        }
    }

    @Nested
    @DisplayName("评分汇总")
    class Summary {

        @Test
        @DisplayName("平均分按加权计算并保留一位小数，分布固定补满 5 个星级")
        void shouldAggregateAndPadDistribution() {
            // 5 星 2 条 + 2 星 1 条 → (5*2 + 2) / 3 = 4.0
            when(reviewMapper.selectRatingDistribution(BOOK_ID)).thenReturn(List.of(
                    new ReviewSummaryVO.RatingDistributionVO(5, 2L),
                    new ReviewSummaryVO.RatingDistributionVO(2, 1L)));

            ReviewSummaryVO vo = reviewService.summary(BOOK_ID);

            assertEquals(3, vo.getReviewCount().intValue());
            assertEquals(new BigDecimal("4.0"), vo.getAvgRating());
            assertEquals(5, vo.getDistribution().size(), "固定返回 5~1 星，缺失的补 0");
            assertEquals(5, vo.getDistribution().get(0).getRating().intValue());
            assertEquals(2L, vo.getDistribution().get(0).getCount().longValue());
            assertEquals(0L, vo.getDistribution().get(1).getCount().longValue(), "4 星无数据应为 0");
            assertEquals(1L, vo.getDistribution().get(3).getCount().longValue(), "2 星 1 条");
            assertEquals(1, vo.getDistribution().get(4).getRating().intValue(), "最后一个元素是 1 星");
        }

        @Test
        @DisplayName("平均分四舍五入到一位小数（32/7 = 4.571… → 4.6）")
        void shouldRoundHalfUp() {
            when(reviewMapper.selectRatingDistribution(BOOK_ID)).thenReturn(List.of(
                    new ReviewSummaryVO.RatingDistributionVO(5, 4L),
                    new ReviewSummaryVO.RatingDistributionVO(4, 3L)));

            assertEquals(new BigDecimal("4.6"), reviewService.summary(BOOK_ID).getAvgRating());
        }

        @Test
        @DisplayName("无评价时平均分为 null（APP 显示「暂无评分」而不是 0 分）")
        void shouldReturnNullAverageWhenNoReview() {
            when(reviewMapper.selectRatingDistribution(BOOK_ID)).thenReturn(Collections.emptyList());

            ReviewSummaryVO vo = reviewService.summary(BOOK_ID);

            assertEquals(0, vo.getReviewCount().intValue());
            assertNull(vo.getAvgRating());
            assertEquals(5, vo.getDistribution().size(), "即使没有评价也要返回结构完整的 5 个星级");
            assertTrue(vo.getDistribution().stream().allMatch(d -> d.getCount() == 0L));
        }

        @Test
        @DisplayName("Mapper 返回 null 时按空结果处理，不抛空指针")
        void shouldTolerateNullDistribution() {
            when(reviewMapper.selectRatingDistribution(BOOK_ID)).thenReturn(null);

            ReviewSummaryVO vo = reviewService.summary(BOOK_ID);

            assertEquals(0, vo.getReviewCount().intValue());
            assertNull(vo.getAvgRating());
        }
    }

    @Nested
    @DisplayName("分页与批量统计")
    class PagingAndStats {

        @Test
        @DisplayName("评价分页：非法页码收敛为 1，页大小上限收敛为 50")
        void shouldClampPagingParams() {
            when(reviewMapper.selectBookReviewPage(any(), eq(BOOK_ID)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PageVO<ReviewVO> huge = reviewService.pageByBook(BOOK_ID, -3L, 999L);
            assertEquals(1L, huge.getPageNum(), "页码小于 1 收敛为 1");
            assertEquals(50L, huge.getPageSize(), "页大小上限 50，防止大分页拖库");

            PageVO<ReviewVO> defaults = reviewService.pageByBook(BOOK_ID, null, null);
            assertEquals(1L, defaults.getPageNum());
            assertEquals(10L, defaults.getPageSize(), "不传分页参数时默认每页 10 条");
        }

        @Test
        @DisplayName("图书 ID 为空时拒绝查询，避免全表统计")
        void shouldRejectNullBookId() {
            assertEquals(ErrorCode.BAD_REQUEST,
                    assertThrows(BusinessException.class, () -> reviewService.pageByBook(null, 1L, 10L))
                            .getErrorCode());
            assertEquals(ErrorCode.BAD_REQUEST,
                    assertThrows(BusinessException.class, () -> reviewService.summary(null))
                            .getErrorCode());
        }

        @Test
        @DisplayName("批量评分统计：空入参直接返回空 Map，不查库")
        void shouldSkipQueryWhenNoBookIds() {
            assertTrue(reviewService.statsByBookIds(Collections.emptyList()).isEmpty());
            assertTrue(reviewService.statsByBookIds(null).isEmpty());
            verify(reviewMapper, never()).selectRatingStats(any());
        }

        @Test
        @DisplayName("批量评分统计：按 bookId 建索引返回")
        void shouldIndexStatsByBookId() {
            BookRatingStatVO stat = new BookRatingStatVO();
            stat.setBookId(BOOK_ID);
            stat.setAvgRating(new BigDecimal("4.5"));
            stat.setReviewCount(2);
            when(reviewMapper.selectRatingStats(List.of(BOOK_ID))).thenReturn(List.of(stat));

            Map<Long, BookRatingStatVO> stats = reviewService.statsByBookIds(List.of(BOOK_ID));

            assertEquals(1, stats.size());
            assertEquals(new BigDecimal("4.5"), stats.get(BOOK_ID).getAvgRating());
            assertEquals(2, stats.get(BOOK_ID).getReviewCount().intValue());
            assertFalse(stats.containsKey(999L), "无评价的图书不应出现在结果里（由调用方兜底为 0）");
        }
    }

    @Nested
    @DisplayName("可评价项查询")
    class ReviewableItems {

        @Test
        @DisplayName("按 reviewId 推导 reviewed，避免前端重复判空")
        void shouldDeriveReviewedFlag() {
            ReviewableItemVO reviewed = new ReviewableItemVO();
            reviewed.setBookId(BOOK_ID);
            reviewed.setReviewId(88L);
            reviewed.setRating(5);

            ReviewableItemVO notReviewed = new ReviewableItemVO();
            notReviewed.setBookId(7L);

            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(reviewMapper.selectReviewableItems(USER_ID, ORDER_ID))
                    .thenReturn(List.of(reviewed, notReviewed));

            List<ReviewableItemVO> items = reviewService.reviewableItems(USER_ID, ORDER_ID);

            assertEquals(2, items.size());
            assertTrue(items.get(0).getReviewed());
            assertEquals(88L, items.get(0).getReviewId().longValue());
            assertEquals(5, items.get(0).getRating().intValue());
            assertFalse(items.get(1).getReviewed());
            assertNull(items.get(1).getRating());
        }

        @Test
        @DisplayName("查看他人订单的可评价项 → 403")
        void shouldRejectOthersOrder() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, OTHER_USER_ID, OrderStatus.COMPLETED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.reviewableItems(USER_ID, ORDER_ID));

            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
            verify(reviewMapper, never()).selectReviewableItems(any(), any());
        }

        @Test
        @DisplayName("Mapper 返回 null 时返回空列表，不把 null 透给前端")
        void shouldReturnEmptyListWhenMapperReturnsNull() {
            when(ordersMapper.selectById(ORDER_ID))
                    .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.COMPLETED));
            when(reviewMapper.selectReviewableItems(USER_ID, ORDER_ID)).thenReturn(null);

            assertTrue(reviewService.reviewableItems(USER_ID, ORDER_ID).isEmpty());
        }
    }
}
