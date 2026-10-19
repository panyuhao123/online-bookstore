package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.ReviewCreateRequest;
import cn.edu.bookstore.model.vo.BookRatingStatVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.ReviewSummaryVO;
import cn.edu.bookstore.model.vo.ReviewVO;
import cn.edu.bookstore.model.vo.ReviewableItemVO;

import java.util.List;
import java.util.Map;

/**
 * 图书评价业务接口。
 *
 * @author 毕设项目组
 */
public interface ReviewService {

    /**
     * 某本书的评价分页（APP 图书详情页，匿名可访问）。
     */
    PageVO<ReviewVO> pageByBook(Long bookId, Long pageNum, Long pageSize);

    /**
     * 某本书的评价汇总：平均分、评价数、各星级分布。
     */
    ReviewSummaryVO summary(Long bookId);

    /**
     * 发表评价。
     *
     * <p>四道校验缺一不可：
     * <ol>
     *     <li>订单存在且属于当前用户（防止评价他人订单里的书）；</li>
     *     <li>订单状态为「已完成」—— 只有收到货才谈得上评价；</li>
     *     <li>订单明细里确实有这本书（防止拿一笔无关订单来评价任意图书）；</li>
     *     <li>该订单内这本书尚未评价过（防止重复评价刷分）。</li>
     * </ol>
     *
     * @param userId  当前登录用户
     * @param request 评价内容
     */
    void create(Long userId, ReviewCreateRequest request);

    /**
     * 我的评价分页。
     */
    PageVO<ReviewVO> myReviews(Long userId, Long pageNum, Long pageSize);

    /**
     * 某笔订单里各商品的评价状态（是否已评价）。
     *
     * @param userId  当前登录用户（校验订单归属）
     * @param orderId 订单 ID
     */
    List<ReviewableItemVO> reviewableItems(Long userId, Long orderId);

    /**
     * 批量取多本图书的评分与评价数。
     *
     * <p>供图书列表 / 详情 / 热门榜回填评分，一次查询避免 N+1。
     *
     * @param bookIds 图书 ID 列表
     * @return bookId -> 评分统计；无评价的图书不在 Map 中
     */
    Map<Long, BookRatingStatVO> statsByBookIds(List<Long> bookIds);
}
