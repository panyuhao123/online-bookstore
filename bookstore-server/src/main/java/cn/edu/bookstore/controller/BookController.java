package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.model.dto.BookQuery;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图书接口（APP 首页、分类筛选、搜索、详情页使用）。
 *
 * <p>允许匿名访问：需求文档 3.1.1 只限制购物车、下单、个人中心需登录，
 * 图书浏览对未登录用户开放，降低首次使用门槛。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /**
     * 分页查询图书。
     *
     * <p>示例：GET /api/book/page?keyword=Java&categoryId=1&pageNum=1&pageSize=10
     */
    @GetMapping("/page")
    public Result<PageVO<BookVO>> page(@ModelAttribute BookQuery query) {
        return Result.success(bookService.page(query));
    }

    /** 图书详情 */
    @GetMapping("/{id}")
    public Result<BookVO> detail(@PathVariable Long id) {
        return Result.success(bookService.detail(id));
    }

    /**
     * 热门图书榜（首页「热销榜」用）。
     *
     * <p>示例：GET /api/book/hot?limit=6
     *
     * <p>注意：本方法声明在 {@code /{id}} 之前不是必须的 ——
     * Spring MVC 的路径匹配会优先选择字面量路径 {@code /hot} 而不是变量路径 {@code /{id}}，
     * 因此 /api/book/hot 不会被当成 id=hot 而报参数类型错误。
     */
    @GetMapping("/hot")
    public Result<List<BookVO>> hot(@RequestParam(defaultValue = "6") int limit) {
        return Result.success(bookService.hotBooks(limit));
    }
}
