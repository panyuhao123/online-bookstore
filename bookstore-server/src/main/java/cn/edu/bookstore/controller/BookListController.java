package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.web.LoginUserHolder;
import cn.edu.bookstore.model.dto.BookListAddBooksRequest;
import cn.edu.bookstore.model.dto.BookListRequest;
import cn.edu.bookstore.model.vo.BookListDetailVO;
import cn.edu.bookstore.model.vo.BookListVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BookListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 书单接口。
 *
 * <p><b>读写分开授权</b>：书单广场与书单详情是内容，匿名可访问（与图书详情一致）；
 * 其余接口都涉及个人数据，必须登录 —— 这条边界由 SecurityConfig 的白名单划定，
 * 而判断的不是 Controller 上的注解，而是「这个接口到底在改谁的数据」。
 *
 * <p>路径设计上刻意让「公开读」与「我的书单」前缀不同（{@code /page}、{@code /detail/{id}}
 * 对 {@code /my}），而不是写成 {@code /{id}}：如果详情接口是 {@code /api/booklist/{id}}，
 * 那么白名单里的 {@code /api/booklist/{id}} 会把 {@code /api/booklist/my} 一并放行
 * （Spring Security 的路径变量匹配任意单段），
 * 结果就是任何人都能匿名拉到别人的私密书单列表。多写一层 detail 换来白名单的精确性，是划算的。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/booklist")
@RequiredArgsConstructor
public class BookListController {

    private final BookListService bookListService;

    /**
     * 书单广场（公开书单分页）。
     *
     * <p>示例：GET /api/booklist/page?keyword=高分&pageNum=1&pageSize=12
     *
     * <p>不带令牌也能访问；带了令牌时会把本人创建的私密书单也一并返回并标记 {@code mine=true}，
     * 这样「广场」与「我的」两个入口在视觉上不会互相打架。
     */
    @GetMapping("/page")
    public Result<PageVO<BookListVO>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long pageNum,
                                          @RequestParam(required = false) Long pageSize) {
        return Result.success(bookListService.page(LoginUserHolder.getUserId(), pageNum, pageSize, keyword));
    }

    /**
     * 书单详情（含图书明细）。
     *
     * <p>私密书单对非创建者返回 403；匿名访问私密书单同样 403 ——
     * 直接答「不存在」会让人怀疑是链接失效，而 403 让语义准确：这本是别人的私密内容。
     */
    @GetMapping("/detail/{id}")
    public Result<BookListDetailVO> detail(@PathVariable Long id) {
        return Result.success(bookListService.detail(LoginUserHolder.getUserId(), id));
    }

    /** 我的书单（含私密） */
    @GetMapping("/my")
    public Result<PageVO<BookListVO>> my(@RequestParam(required = false) Long pageNum,
                                        @RequestParam(required = false) Long pageSize) {
        return Result.success(bookListService.myLists(LoginUserHolder.getUserId(), pageNum, pageSize));
    }

    /** 新建书单 */
    @PostMapping
    public Result<BookListVO> create(@Valid @RequestBody BookListRequest request) {
        return Result.success(bookListService.create(LoginUserHolder.getUserId(), request));
    }

    /** 编辑书单（改名 / 改简介 / 改封面 / 切换公开状态） */
    @PutMapping("/{id}")
    public Result<BookListVO> update(@PathVariable Long id, @Valid @RequestBody BookListRequest request) {
        return Result.success(bookListService.update(LoginUserHolder.getUserId(), id, request));
    }

    /** 删除书单（连同书单里的条目一起删除，不影响图书本身） */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        bookListService.remove(LoginUserHolder.getUserId(), id);
        return Result.success();
    }

    /**
     * 批量加入图书。
     *
     * <p>返回 {@code added} 是「实际新增条数」而非请求条数：已存在于书单中的图书会被跳过。
     * 让前端能区分「全部已在书单里」与「真的加进去了」，从而给出准确提示 ——
     * 只回一个空结果的话，用户加了 5 本、实际新增 0 本，页面却没有任何反馈。
     */
    @PostMapping("/{id}/books")
    public Result<Map<String, Integer>> addBooks(@PathVariable Long id,
                                                @Valid @RequestBody BookListAddBooksRequest request) {
        int added = bookListService.addBooks(LoginUserHolder.getUserId(), id, request.getBookIds());
        return Result.success(Map.of("added", added, "total", request.getBookIds().size()));
    }

    /** 从书单移出某本图书 */
    @DeleteMapping("/{id}/books/{bookId}")
    public Result<Void> removeBook(@PathVariable Long id, @PathVariable Long bookId) {
        bookListService.removeBook(LoginUserHolder.getUserId(), id, bookId);
        return Result.success();
    }
}
