package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.model.vo.CategoryBookCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分类数据访问层。
 *
 * @author 毕设项目组
 */
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 按分类统计<b>上架</b>图书数量。
     *
     * <p>口径说明：只算 {@code status = 1}。前台的「该分类 N 本」必须是用户点进去能买到的数量，
     * 若把已下架的也算进去，页签写着 12 本、列表只有 9 本，属于对用户撒谎。
     * 管理端需要的是另一个口径（含下架，用于「有书就不许删分类」的删除保护），
     * 由 {@code AdminCategoryServiceImpl} 单独统计，两者刻意不合并。
     *
     * @return 每个有上架图书的分类一行；空分类不出现在结果中，调用方用 0 兜底
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS bookCount "
            + "FROM book WHERE status = 1 GROUP BY category_id")
    List<CategoryBookCountVO> selectOnSaleBookCounts();
}
