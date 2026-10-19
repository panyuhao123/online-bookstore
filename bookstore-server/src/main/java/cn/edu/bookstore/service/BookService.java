package cn.edu.bookstore.service;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.model.dto.BookQuery;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;

import java.util.List;
import java.util.Map;

/**
 * 图书业务接口。
 *
 * @author 毕设项目组
 */
public interface BookService {

    /**
     * 分页查询图书（支持关键词搜索与分类筛选）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageVO<BookVO> page(BookQuery query);

    /**
     * 图书详情。
     *
     * @param id 图书 ID
     * @return 图书信息
     */
    BookVO detail(Long id);

    /**
     * 按 ID 批量查询图书，返回 ID -> 图书的映射。
     *
     * <p>供购物车、订单等场景一次性取图书快照，避免循环查库造成 N+1。
     *
     * @param ids 图书 ID 列表
     * @return ID -> 图书映射
     */
    Map<Long, Book> mapByIds(List<Long> ids);

    /**
     * 热门图书榜：按累计销量倒序取前 N 本（仅上架）。
     *
     * <p>这是全站访问最集中的读接口（首页一进来就调），因此结果走 Redis 缓存，
     * 并且复用 {@code idx_book_sales} 索引避免排序时产生临时表。
     *
     * <p>缓存会在图书信息变更、下单、取消订单时被清理 ——
     * 销量变了榜单就要跟着变，不能一直展示旧的排名。
     *
     * @param limit 需要的条数，服务端会收敛到 1~20，避免被传入超大值拖库
     * @return 按销量倒序的图书列表，可能不足 limit 条
     */
    List<BookVO> hotBooks(int limit);
}
