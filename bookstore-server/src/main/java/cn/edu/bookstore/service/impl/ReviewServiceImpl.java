package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.OrderItem;
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
import cn.edu.bookstore.service.ReviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图书评价业务实现。
 *
 * <p>核心约束只有一条：**评价资格来自真实购买**。
 * 所有校验都围绕这一点展开，宁可多查一次库也不放宽条件 ——
 * 评价体系一旦可以被刷，评分就失去参考价值，功能做了等于没做。
 *
 * <p>并发兜底：业务层先查一次「是否已评价」给出友好提示，
 * 数据库层再用唯一键 {@code uk_review_user_book_id_order} 兜底。
 * 用户连点两次提交时，后一次会触发唯一键冲突，
 * 这里捕获 {@link DuplicateKeyException} 转换成可读的业务提示，而不是抛出 500。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    /** 评价没有条数时，平均分按 1 位小数展示 */
    private static final int AVG_SCALE = 1;

    private final ReviewMapper reviewMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public PageVO<ReviewVO> pageByBook(Long bookId, Long pageNum, Long pageSize) {
        if (bookId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图书 ID 不能为空");
        }
        long current = (pageNum == null || pageNum < 1) ? 1L : pageNum;
        long size = (pageSize == null || pageSize < 1) ? 10L : Math.min(pageSize, 50L);
        IPage<ReviewVO> page = reviewMapper.selectBookReviewPage(new Page<>(current, size), bookId);
        return PageVO.of(page);
    }

    @Override
    public ReviewSummaryVO summary(Long bookId) {
        if (bookId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图书 ID 不能为空");
        }
        List<ReviewSummaryVO.RatingDistributionVO> rows = reviewMapper.selectRatingDistribution(bookId);
        Map<Integer, Long> byRating = rows == null ? Map.of()
                : rows.stream().collect(Collectors.toMap(
                        ReviewSummaryVO.RatingDistributionVO::getRating,
                        ReviewSummaryVO.RatingDistributionVO::getCount,
                        (a, b) -> a));

        // 固定补齐 5~1 星：前端可以直接按顺序渲染评分分布条，不必处理「某星级缺失」的分支
        List<ReviewSummaryVO.RatingDistributionVO> distribution = new ArrayList<>(5);
        long total = 0;
        long weighted = 0;
        for (int star = 5; star >= 1; star--) {
            long count = byRating.getOrDefault(star, 0L);
            distribution.add(new ReviewSummaryVO.RatingDistributionVO(star, count));
            total += count;
            weighted += count * star;
        }

        ReviewSummaryVO vo = new ReviewSummaryVO();
        vo.setReviewCount((int) total);
        // 没有评价时平均分留 null，APP 端显示「暂无评分」而不是误导性的 0 分
        vo.setAvgRating(total == 0 ? null
                : BigDecimal.valueOf(weighted)
                        .divide(BigDecimal.valueOf(total), AVG_SCALE, RoundingMode.HALF_UP));
        vo.setDistribution(distribution);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            // 图书详情缓存里含评分字段，评价变化要精确失效这一本
            @CacheEvict(value = "book", key = "#request.bookId"),
            // 热门榜的排序键是 limit，无法按单品精确失效，整体清空
            @CacheEvict(value = "hotBook", allEntries = true)
    })
    public void create(Long userId, ReviewCreateRequest request) {
        Orders orders = ordersMapper.selectById(request.getOrderId());
        if (orders == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        // 校验 1：只能评价自己的订单
        if (!orders.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权评价他人的订单");
        }
        // 校验 2：只有已完成的订单（确认收货后）才允许评价
        if (OrderStatus.COMPLETED.getCode() != orders.getStatus()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "当前订单状态为「" + OrderStatus.textOf(orders.getStatus())
                            + "」，仅「已完成」的订单可以评价");
        }
        // 校验 3：该订单里必须真的有这本书，避免拿任意订单评价任意图书
        Long itemCount = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orders.getId())
                .eq(OrderItem::getBookId, request.getBookId()));
        if (itemCount == null || itemCount == 0) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "该订单不包含这本图书，无法评价");
        }
        // 校验 4：同一订单内同一本书只能评价一次
        Long existed = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getBookId, request.getBookId())
                .eq(Review::getOrderId, orders.getId()));
        if (existed != null && existed > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "这本书你已经评价过了");
        }

        Review review = new Review();
        review.setBookId(request.getBookId());
        review.setUserId(userId);
        review.setOrderId(orders.getId());
        review.setRating(request.getRating());
        review.setContent(trimToNull(request.getContent()));
        review.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0);
        review.setStatus(1);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 并发场景：两个请求同时通过了上面的检查，唯一键拦住其中一个
            throw new BusinessException(ErrorCode.CONFLICT, "这本书你已经评价过了");
        }
        log.info("评价发表成功 userId={} bookId={} orderNo={} 星级={}",
                userId, request.getBookId(), orders.getOrderNo(), request.getRating());
    }

    @Override
    public PageVO<ReviewVO> myReviews(Long userId, Long pageNum, Long pageSize) {
        long current = (pageNum == null || pageNum < 1) ? 1L : pageNum;
        long size = (pageSize == null || pageSize < 1) ? 10L : Math.min(pageSize, 50L);
        return PageVO.of(reviewMapper.selectMyReviewPage(new Page<>(current, size), userId));
    }

    @Override
    public List<ReviewableItemVO> reviewableItems(Long userId, Long orderId) {
        Orders orders = ordersMapper.selectById(orderId);
        if (orders == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!orders.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看他人的订单");
        }
        List<ReviewableItemVO> items = reviewMapper.selectReviewableItems(userId, orderId);
        if (items == null) {
            return List.of();
        }
        // reviewed 由 reviewId 推导，避免前端再判空两次
        items.forEach(item -> item.setReviewed(item.getReviewId() != null));
        return items;
    }

    @Override
    public Map<Long, BookRatingStatVO> statsByBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BookRatingStatVO> stats = reviewMapper.selectRatingStats(bookIds);
        if (stats == null || stats.isEmpty()) {
            return Collections.emptyMap();
        }
        return stats.stream().collect(Collectors.toMap(
                BookRatingStatVO::getBookId, Function.identity(), (a, b) -> a));
    }

    /** 空白字符串统一存 null，避免出现「有内容但全是空格」的评价 */
    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
