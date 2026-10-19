package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.dto.CategorySaveRequest;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.service.AdminCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端分类接口（Web 后台分类管理）。
 *
 * <p>与 APP 端 /api/category/list 区分：该前缀下全部接口需管理员身份，
 * 且返回列表包含排序值，便于后台表格展示。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    /** 分类列表 */
    @GetMapping("/list")
    public Result<List<CategoryVO>> list() {
        return Result.success(adminCategoryService.list());
    }

    /** 新增分类 */
    @OpLog(module = "分类管理", action = "新增分类")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategorySaveRequest request) {
        return Result.success(adminCategoryService.create(request));
    }

    /** 修改分类 */
    @OpLog(module = "分类管理", action = "修改分类")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        adminCategoryService.update(id, request);
        return Result.success();
    }

    /** 删除分类 */
    @OpLog(module = "分类管理", action = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminCategoryService.delete(id);
        return Result.success();
    }
}
