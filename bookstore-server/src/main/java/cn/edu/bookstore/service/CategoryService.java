package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.CategoryVO;

import java.util.List;

/**
 * 图书分类业务接口。
 *
 * @author 毕设项目组
 */
public interface CategoryService {

    /**
     * 分类列表（按排序值倒序）。
     *
     * @return 全部分类
     */
    List<CategoryVO> list();
}
