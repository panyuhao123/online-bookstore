package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.BookIdRequest;
import cn.edu.bookstore.model.dto.HistoryMergeRequest;
import cn.edu.bookstore.model.vo.HistoryItemVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BrowseHistoryService;
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

import java.util.Map;

/**
 * 浏览历史接口（全部需要登录）。
 *
 * <p>接口布局对应前端的两段生命周期：
 * <ul>
 *     <li>已登录浏览：打开商品详情时调 {@code POST /api/history}；</li>
 *     <li>未登录浏览：前端仍存在 localStorage（要求「先登录再看书」会直接劝退新用户），
 *         登录成功后调 {@code POST /api/history/merge} 一次性带上服务端。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    /**
     * 记录一次浏览（重复浏览同一本书只更新时间，不新增行）。
     *
     * <p>刻意返回 {@code recorded} 而不是空结果：记录浏览属「埋点」性质，
     * 图书不存在时不应该抛异常打断用户浏览，但前端（以及排障时的我们自己）
     * 需要知道这一笔到底记上没有。
     */
    @PostMapping
    public Result<Map<String, Boolean>> record(@Valid @RequestBody BookIdRequest request) {
        boolean recorded = browseHistoryService.record(LoginUserHolder.getUserId(), request.getBookId());
        return Result.success(Map.of("recorded", recorded));
    }

    /** 我的浏览历史（按最近浏览时间倒序） */
    @GetMapping("/page")
    public Result<PageVO<HistoryItemVO>> page(@RequestParam(required = false) Long pageNum,
                                             @RequestParam(required = false) Long pageSize) {
        return Result.success(browseHistoryService.page(LoginUserHolder.getUserId(), pageNum, pageSize));
    }

    /**
     * 合并未登录期间的本地浏览记录。
     *
     * <p>返回实际新增条数。已存在的记录不会被本地旧记录覆盖 ——
     * 合并的语义是「补充遗漏」，不是「用本地状态覆盖服务端」，
     * 否则用户在多设备上浏览的记录会被其中一台的旧快照冲掉。
     */
    @PostMapping("/merge")
    public Result<Map<String, Integer>> merge(@Valid @RequestBody HistoryMergeRequest request) {
        int added = browseHistoryService.merge(LoginUserHolder.getUserId(), request.getBookIds());
        return Result.success(Map.of("added", added, "total", request.getBookIds().size()));
    }

    /** 删除单条浏览记录 */
    @DeleteMapping("/{bookId}")
    public Result<Void> removeOne(@PathVariable Long bookId) {
        browseHistoryService.removeOne(LoginUserHolder.getUserId(), bookId);
        return Result.success();
    }

    /** 清空浏览历史 */
    @DeleteMapping
    public Result<Void> clear() {
        browseHistoryService.clear(LoginUserHolder.getUserId());
        return Result.success();
    }
}
