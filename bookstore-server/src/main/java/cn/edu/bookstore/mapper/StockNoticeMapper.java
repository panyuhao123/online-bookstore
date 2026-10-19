package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.StockNotice;
import cn.edu.bookstore.model.vo.StockNoticeSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 缺货到货提醒数据访问层。
 *
 * <p>管理端看板需要在**数据库里**完成分组聚合与分页，而不是把全部订阅行捞进内存再算：
 * 聚合结果的行数取决于「有多少本缺货书被订阅」，与订阅总量无关，
 * 用 SQL 一次算完既省传输也省内存，且能直接支持按等待人数排序。
 *
 * <p>两个查询必须共用同一套 WHERE / HAVING 条件（{@code pageSummary} 取当页、
 * {@code countSummary} 取总数），否则会出现「翻到第 3 页是空的，但总数显示还有 20 条」
 * 这类分页错位。因此条件表达式在下面两段 SQL 里逐字保持一致。
 *
 * @author 毕设项目组
 */
public interface StockNoticeMapper extends BaseMapper<StockNotice> {

    /**
     * 缺货图书「等待人数」分页（按待通知人数倒序）。
     *
     * @param keyword     图书标题关键词，可为空
     * @param onlyWaiting true-只看还有待通知用户的图书；false-包含全部
     */
    @Select("""
            SELECT n.book_id                  AS bookId,
                   b.title                    AS bookTitle,
                   b.cover_url                AS bookCoverUrl,
                   b.stock                    AS stock,
                   b.status                   AS bookStatus,
                   b.price                    AS price,
                   SUM(CASE WHEN n.status = 0 THEN 1 ELSE 0 END) AS waitingCount,
                   SUM(CASE WHEN n.status = 1 THEN 1 ELSE 0 END) AS notifiedCount,
                   MAX(n.create_time)         AS lastSubscribeTime
            FROM stock_notice n
                     JOIN book b ON b.id = n.book_id
            WHERE (#{keyword} IS NULL OR b.title LIKE CONCAT('%', #{keyword}, '%'))
            GROUP BY n.book_id, b.title, b.cover_url, b.stock, b.status, b.price
            HAVING (#{onlyWaiting} = 0 OR SUM(CASE WHEN n.status = 0 THEN 1 ELSE 0 END) > 0)
            ORDER BY waitingCount DESC, n.book_id ASC
            LIMIT #{offset}, #{limit}
            """)
    List<StockNoticeSummaryVO> pageSummary(@Param("keyword") String keyword,
                                          @Param("onlyWaiting") boolean onlyWaiting,
                                          @Param("offset") long offset,
                                          @Param("limit") long limit);

    /** 与 {@link #pageSummary} 完全同条件的总数（分组后的组数） */
    @Select("""
            SELECT COUNT(*)
            FROM (SELECT n.book_id
                  FROM stock_notice n
                           JOIN book b ON b.id = n.book_id
                  WHERE (#{keyword} IS NULL OR b.title LIKE CONCAT('%', #{keyword}, '%'))
                  GROUP BY n.book_id, b.title, b.cover_url, b.stock, b.status, b.price
                  HAVING (#{onlyWaiting} = 0 OR SUM(CASE WHEN n.status = 0 THEN 1 ELSE 0 END) > 0)) t
            """)
    long countSummary(@Param("keyword") String keyword,
                      @Param("onlyWaiting") boolean onlyWaiting);
}
