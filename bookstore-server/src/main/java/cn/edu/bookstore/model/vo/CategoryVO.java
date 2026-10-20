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

    /**
     * 该分类下的图书数量。
     *
     * <p>口径按端区分，刻意不统一：
     * <ul>
     *     <li>前台 {@code /api/category/list}：只算<b>上架</b>图书（用户点进去能买到的数量）；</li>
     *     <li>管理端 {@code /api/admin/category/list}：算<b>全部</b>图书（含下架，用于「有书就不许删分类」）。</li>
     * </ul>
     * 两个数字在同一分类上不一致属于设计如此，排查时勿当作缺陷。
     */
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
