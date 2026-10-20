package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.dto.AdminBookQuery;
import cn.edu.bookstore.model.dto.BookSaveRequest;
import cn.edu.bookstore.model.vo.AdminBookSummaryVO;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端图书接口（Web 后台：图书列表 / 新增 / 编辑 / 上下架 / 删除）。
 *
 * <p>说明：路径前缀为 /api/admin/book，不与 APP 端 /api/book/** 的匿名白名单冲突，
 * 因此全部接口都由 Spring Security 强制校验管理员身份。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/book")
@RequiredArgsConstructor
public class AdminBookController {

    private final AdminBookService adminBookService;

    /** 后台图书分页：GET /api/admin/book/page?keyword=&categoryId=&status=&pageNum=&pageSize= */
    @GetMapping("/page")
    public Result<PageVO<BookVO>> page(@ModelAttribute AdminBookQuery query) {
        return Result.success(adminBookService.page(query));
    }

    /** 后台图书详情（含下架图书） */
    @GetMapping("/{id}")
    public Result<BookVO> detail(@PathVariable Long id) {
        return Result.success(adminBookService.detail(id));
    }

    /**
     * 图书汇总指标：GET /api/admin/book/summary
     *
     * <p>放在 /{id} 之后声明也不影响匹配：Spring MVC 的路径匹配是「字面量优先于模板变量」，
     * /summary 不会被当成 id="summary" 解析。
     */
    @GetMapping("/summary")
    public Result<AdminBookSummaryVO> summary() {
        return Result.success(adminBookService.summary());
    }

    /** 新增图书 */
    @OpLog(module = "图书管理", action = "新增图书")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BookSaveRequest request) {
        return Result.success(adminBookService.create(request));
    }

    /** 修改图书 */
    @OpLog(module = "图书管理", action = "修改图书")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookSaveRequest request) {
        adminBookService.update(id, request);
        return Result.success();
    }

    /** 上下架：PUT /api/admin/book/{id}/status?status=1 */
    @OpLog(module = "图书管理", action = "上下架图书")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminBookService.updateStatus(id, status);
        return Result.success();
    }

    /** 删除图书（已产生订单的图书改为下架，不可删除） */
    @OpLog(module = "图书管理", action = "删除图书")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminBookService.delete(id);
        return Result.success();
    }
}
