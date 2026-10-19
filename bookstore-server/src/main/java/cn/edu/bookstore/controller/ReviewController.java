package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.ReviewCreateRequest;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.ReviewSummaryVO;
import cn.edu.bookstore.model.vo.ReviewVO;
import cn.edu.bookstore.model.vo.ReviewableItemVO;
import cn.edu.bookstore.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图书评价接口（APP 端）。
 *
 * <p>访问控制分两档，与「图书浏览开放、个人数据需登录」的既有约定保持一致：
 * <ul>
 *     <li><b>匿名可读</b>：{@code /api/review/book/**} —— 看别人的评价是浏览行为，
 *         要求先登录会劝退新用户；</li>
 *     <li><b>必须登录</b>：发表评价、我的评价、订单可评价项 —— 都涉及个人数据。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 某本书的评价分页（图书详情页「用户评价」列表）。
     *
     * <p>示例：GET /api/review/book/1?pageNum=1&pageSize=10
     */
    @GetMapping("/book/{bookId}")
    public Result<PageVO<ReviewVO>> pageByBook(@PathVariable Long bookId,
                                              @RequestParam(required = false) Long pageNum,
                                              @RequestParam(required = false) Long pageSize) {
        return Result.success(reviewService.pageByBook(bookId, pageNum, pageSize));
    }

    /**
     * 某本书的评分汇总（平均分 + 评价数 + 5~1 星分布）。
     *
     * <p>示例：GET /api/review/book/1/summary
     *
     * <p>与分页接口分开而不是塞进图书详情：评分区块常被单独刷新
     * （用户发表评价后只更新这一块），拆开可以少传一次图书详情。
     */
    @GetMapping("/book/{bookId}/summary")
    public Result<ReviewSummaryVO> summary(@PathVariable Long bookId) {
        return Result.success(reviewService.summary(bookId));
    }

    /**
     * 发表评价。
     *
     * <p>评价资格由服务端从订单反查（订单归属 + 已完成 + 含该书 + 未评价过），
     * 客户端只提交「订单 ID + 图书 ID + 星级 + 内容」。
     */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ReviewCreateRequest request) {
        reviewService.create(LoginUserHolder.getUserId(), request);
        return Result.success();
    }

    /** 我的评价分页：GET /api/review/my?pageNum=1&pageSize=10 */
    @GetMapping("/my")
    public Result<PageVO<ReviewVO>> myReviews(@RequestParam(required = false) Long pageNum,
                                             @RequestParam(required = false) Long pageSize) {
        return Result.success(reviewService.myReviews(LoginUserHolder.getUserId(), pageNum, pageSize));
    }

    /**
     * 某笔订单里各商品的评价状态。
     *
     * <p>示例：GET /api/review/order/12/items
     *
     * <p>APP 端订单详情页据此渲染每本书的「去评价 / 查看评价」按钮。
     */
    @GetMapping("/order/{orderId}/items")
    public Result<List<ReviewableItemVO>> reviewableItems(@PathVariable Long orderId) {
        return Result.success(reviewService.reviewableItems(LoginUserHolder.getUserId(), orderId));
    }
}
