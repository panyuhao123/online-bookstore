package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.BookIdRequest;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeVO;
import cn.edu.bookstore.service.StockNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 到货提醒接口（用户侧，全部需要登录）。
 *
 * <p>订阅关系必须挂在用户身上，因此没有「匿名订阅」这种形态 ——
 * 未登录用户看到缺货按钮时，前端先引导登录再发起订阅。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/stock-notice")
@RequiredArgsConstructor
public class StockNoticeController {

    private final StockNoticeService stockNoticeService;

    /**
     * 订阅到货提醒。
     *
     * <p>几个刻意的拒绝语义（详见 {@link StockNoticeService#subscribe}）：
     * 图书不存在 404；图书当前有货 422（现在就能买，订阅没有意义）；
     * 已在待通知状态 409（重复订阅）；已取消或已通知过则复用原记录重新置为待通知。
     */
    @PostMapping
    public Result<StockNoticeVO> subscribe(@Valid @RequestBody BookIdRequest request) {
        return Result.success(stockNoticeService.subscribe(LoginUserHolder.getUserId(), request.getBookId()));
    }

    /** 我的订阅列表（不含已取消），按订阅时间倒序 */
    @GetMapping("/my")
    public Result<PageVO<StockNoticeVO>> my(@RequestParam(required = false) Long pageNum,
                                           @RequestParam(required = false) Long pageSize) {
        return Result.success(stockNoticeService.myNotices(LoginUserHolder.getUserId(), pageNum, pageSize));
    }

    /**
     * 取消订阅。
     *
     * <p>实现是「置为已取消」而非物理删除：保留记录才能回答
     * 「这本书明明有 30 人想要，为什么补货后只卖出去 3 本」——
     * 其中 27 人可能已经取消了订阅，这个数字对运营有直接价值。
     * 接口本身是幂等的，重复取消不报错。
     */
    @DeleteMapping("/{bookId}")
    public Result<Void> unsubscribe(@PathVariable Long bookId) {
        stockNoticeService.unsubscribe(LoginUserHolder.getUserId(), bookId);
        return Result.success();
    }
}
