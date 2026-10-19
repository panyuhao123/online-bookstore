package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Category;
import lombok.Data;

/**
 * 分类视图对象。
 *
 * @author 毕设项目组
 */
@Data
public class CategoryVO {

    private Long id;

    private String name;

    private Integer sort;

    /** 该分类下的图书数量；仅管理端列表会填充，APP 端分类接口为 null */
    private Long bookCount;

    public static CategoryVO from(Category category) {
        if (category == null) {
            return null;
        }
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        return vo;
    }
}
