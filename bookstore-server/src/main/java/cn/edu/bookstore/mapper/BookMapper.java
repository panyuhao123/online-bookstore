package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.model.vo.AdminBookSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

/**
 * 图书数据访问层。
 *
 * @author 毕设项目组
 */
public interface BookMapper extends BaseMapper<Book> {

    /**
     * 图书管理页的汇总指标：一条 SQL 出齐，避免前端为四个数字发四次请求。
     *
     * <p>口径说明：{@code on_sale + off_sale = total}；{@code low_stock} 只算上架图
     * （下架书断货不影响售卖，算进来会让「该补货」的数字虚高）；
     * {@code total_stock} 同样只算上架，回答的是「真实可卖的库存」。
     */
    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(status = 1), 0) AS on_sale, "
            + "COALESCE(SUM(status = 0), 0) AS off_sale, "
            + "COALESCE(SUM(status = 1 AND stock <= 5), 0) AS low_stock, "
            + "COALESCE(SUM(CASE WHEN status = 1 THEN stock ELSE 0 END), 0) AS total_stock "
            + "FROM book")
    AdminBookSummaryVO selectAdminSummary();
}
