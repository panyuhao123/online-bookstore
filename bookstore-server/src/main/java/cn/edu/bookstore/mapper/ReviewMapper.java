package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.Review;
import cn.edu.bookstore.model.vo.AdminReviewVO;
import cn.edu.bookstore.model.vo.BookRatingStatVO;
import cn.edu.bookstore.model.vo.ReviewSummaryVO;
import cn.edu.bookstore.model.vo.ReviewVO;
import cn.edu.bookstore.model.vo.ReviewableItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 图书评价数据访问层。
 *
 * <p>聚合查询都放在数据库侧完成：评价表的行数会随业务增长，
 * 把「求平均分 / 分组计数」拉到内存里做是典型的性能陷阱。
 *
 * @author 毕设项目组
 */
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 批量统计多本图书的评分与评价数。
     *
     * <p>供列表页使用：一次查询解决整页图书的评分回填，避免逐本查询的 N+1。
     * 只统计 status=1（未隐藏）的评价，后台隐藏违规评价后平均分会立即恢复正确。
     *
     * @param bookIds 图书 ID 列表
     * @return 每个有评价的图书一行；无评价的图书不在结果中，调用方需用默认值兜底
     */
    @Select("<script>"
            + "SELECT book_id AS bookId, "
            + "ROUND(AVG(rating), 1) AS avgRating, "
            + "COUNT(*) AS reviewCount "
            + "FROM review "
            + "WHERE status = 1 AND book_id IN "
            + "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY book_id"
            + "</script>")
    List<BookRatingStatVO> selectRatingStats(@Param("bookIds") List<Long> bookIds);

    /**
     * 某本书各星级的评价数量（详情页评分分布条）。
     *
     * @param bookId 图书 ID
     * @return 只返回有数据的星级，缺失的星级由 Service 补 0
     */
    @Select("SELECT rating AS rating, COUNT(*) AS count FROM review "
            + "WHERE status = 1 AND book_id = #{bookId} "
            + "GROUP BY rating ORDER BY rating DESC")
    List<ReviewSummaryVO.RatingDistributionVO> selectRatingDistribution(@Param("bookId") Long bookId);

    /**
     * 某本书的评价分页（APP 图书详情页）。
     *
     * <p>用 JOIN 一次把评价人昵称取出来，避免「先查评价再逐条查用户」的 N+1；
     * 匿名替换放在 SQL 里完成，保证匿名用户的昵称不会被传到客户端。
     *
     * @param page   分页参数（MyBatis-Plus 分页插件会自动改写 SQL 加 LIMIT）
     * @param bookId 图书 ID
     */
    @Select("SELECT r.id AS id, r.book_id AS bookId, r.rating AS rating, r.content AS content, "
            + "r.anonymous AS anonymous, r.create_time AS createTime, "
            + "CASE WHEN r.anonymous = 1 THEN '匿名用户' ELSE IFNULL(u.nickname, '书友') END AS nickname "
            + "FROM review r LEFT JOIN `user` u ON u.id = r.user_id "
            + "WHERE r.status = 1 AND r.book_id = #{bookId} "
            + "ORDER BY r.create_time DESC, r.id DESC")
    IPage<ReviewVO> selectBookReviewPage(IPage<ReviewVO> page, @Param("bookId") Long bookId);

    /**
     * 管理端评价分页（可按图书标题 / 评价内容搜索、按星级与状态筛选）。
     *
     * <p>后台不做匿名处理：管理场景必须能看到真实评价人。
     */
    @Select("<script>"
            + "SELECT r.id AS id, r.book_id AS bookId, b.title AS bookTitle, "
            + "r.user_id AS userId, u.nickname AS userNickname, "
            + "r.order_id AS orderId, o.order_no AS orderNo, "
            + "r.rating AS rating, r.content AS content, r.anonymous AS anonymous, "
            + "r.status AS status, r.create_time AS createTime "
            + "FROM review r "
            + "LEFT JOIN book b ON b.id = r.book_id "
            + "LEFT JOIN `user` u ON u.id = r.user_id "
            + "LEFT JOIN orders o ON o.id = r.order_id "
            + "<where>"
            + "  <if test='bookId != null'> AND r.book_id = #{bookId} </if>"
            + "  <if test='rating != null'> AND r.rating = #{rating} </if>"
            + "  <if test='status != null'> AND r.status = #{status} </if>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND (b.title LIKE CONCAT('%', #{keyword}, '%') "
            + "         OR r.content LIKE CONCAT('%', #{keyword}, '%'))"
            + "  </if>"
            + "</where>"
            + "ORDER BY r.create_time DESC, r.id DESC"
            + "</script>")
    IPage<AdminReviewVO> selectAdminReviewPage(IPage<AdminReviewVO> page,
                                              @Param("bookId") Long bookId,
                                              @Param("rating") Integer rating,
                                              @Param("status") Integer status,
                                              @Param("keyword") String keyword);

    /**
     * 查询某笔订单里各商品的评价状态（APP 订单详情页的「去评价 / 查看评价」按钮数据源）。
     *
     * <p>用 LEFT JOIN review 判断是否已评价：已评价则带出 reviewId 与星级，
     * 未评价为 null。一次查询拿到整单状态，避免逐条判断。
     */
    @Select("SELECT oi.order_id AS orderId, oi.book_id AS bookId, oi.book_title AS bookTitle, "
            + "oi.book_cover AS bookCoverUrl, oi.price AS price, oi.quantity AS quantity, "
            + "r.id AS reviewId, r.rating AS rating "
            + "FROM order_item oi "
            + "LEFT JOIN review r ON r.order_id = oi.order_id AND r.book_id = oi.book_id "
            + "AND r.user_id = #{userId} "
            + "WHERE oi.order_id = #{orderId} "
            + "ORDER BY oi.id")
    List<ReviewableItemVO> selectReviewableItems(@Param("userId") Long userId,
                                                @Param("orderId") Long orderId);

    /**
     * 「我的评价」分页：带出图书标题与封面，便于直接渲染列表。
     */
    @Select("SELECT r.id AS id, r.book_id AS bookId, r.rating AS rating, r.content AS content, "
            + "r.anonymous AS anonymous, r.create_time AS createTime, "
            + "b.title AS bookTitle, b.cover_url AS bookCoverUrl "
            + "FROM review r LEFT JOIN book b ON b.id = r.book_id "
            + "WHERE r.user_id = #{userId} "
            + "ORDER BY r.create_time DESC, r.id DESC")
    IPage<ReviewVO> selectMyReviewPage(IPage<ReviewVO> page, @Param("userId") Long userId);
}
