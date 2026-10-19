package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Review;
import cn.edu.bookstore.mapper.ReviewMapper;
import cn.edu.bookstore.model.dto.AdminReviewQuery;
import cn.edu.bookstore.model.vo.AdminReviewVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminReviewService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * 管理端评价管理实现。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

    /** 隐藏状态 */
    private static final int STATUS_HIDDEN = 0;

    private final ReviewMapper reviewMapper;

    @Override
    public PageVO<AdminReviewVO> page(AdminReviewQuery query) {
        query.normalize();
        IPage<AdminReviewVO> page = reviewMapper.selectAdminReviewPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                query.getBookId(),
                query.getRating(),
                query.getStatus(),
                StrUtil.trimToNull(query.getKeyword()));
        return PageVO.of(page);
    }

    @Override
    @Caching(evict = {
            // 这里做不到「按图书精确失效」：入参是评价 ID，而缓存 key 是图书 ID，
            // 注解里的 SpEL 无法表达「由评价反查图书」这一步（需要额外查库）。
            // 后台隐藏/删除评价是低频操作，整体清空 book 与 hotBook 完全可接受；
            // 相比之下，把缓存留在错误状态才是真正的问题。
            @CacheEvict(value = "book", allEntries = true),
            @CacheEvict(value = "hotBook", allEntries = true)
    })
    public void updateStatus(Long id, Integer status) {
        Review review = requireReview(id);
        if (status == null || (status != STATUS_HIDDEN && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能是 1（正常）或 0（隐藏）");
        }
        if (status.equals(review.getStatus())) {
            // 幂等：重复点击「隐藏」不应该产生无意义的 UPDATE，也不该反复清缓存
            return;
        }
        Review update = new Review();
        update.setId(id);
        update.setStatus(status);
        reviewMapper.updateById(update);
        log.info("评价状态变更 reviewId={} bookId={} status={}", id, review.getBookId(), status);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "book", allEntries = true),
            @CacheEvict(value = "hotBook", allEntries = true)
    })
    public void delete(Long id) {
        Review review = requireReview(id);
        reviewMapper.deleteById(id);
        log.warn("评价被删除 reviewId={} bookId={} userId={}", id, review.getBookId(), review.getUserId());
    }

    /** 取出评价并校验存在，避免对不存在的 ID 静默成功 */
    private Review requireReview(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评价 ID 不能为空");
        }
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        return review;
    }
}
