package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.CategorySaveRequest;
import cn.edu.bookstore.model.vo.CategoryVO;

import java.util.List;

/**
 * 管理端分类业务（Web 后台分类管理）。
 *
 * @author 毕设项目组
 */
public interface AdminCategoryService {

    /** 分类列表（含排序值，后台展示用；APP 端 /api/category/list 可匿名访问） */
    List<CategoryVO> list();

    /** 新增分类，返回分类 ID */
    Long create(CategorySaveRequest request);

    /** 修改分类 */
    void update(Long id, CategorySaveRequest request);

    /** 删除分类；分类下还有图书时禁止删除 */
    void delete(Long id);
}
