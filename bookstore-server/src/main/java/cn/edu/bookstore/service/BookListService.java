package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.BookListRequest;
import cn.edu.bookstore.model.vo.BookListDetailVO;
import cn.edu.bookstore.model.vo.BookListVO;
import cn.edu.bookstore.model.vo.PageVO;

import java.util.List;

/**
 * 书单业务接口。
 *
 * <p>访问控制沿用项目既有约定「浏览开放、个人数据需登录」：
 * <ul>
 *     <li><b>匿名可读</b>：书单分页、书单详情 —— 公开书单是内容，鼓励被看到；
 *         私密书单仅创建者本人可见（服务端校验，不依赖前端隐藏入口）；</li>
 *     <li><b>必须登录</b>：新建 / 改名 / 删除 / 加减图书 —— 都涉及个人数据。</li>
 * </ul>
 *
 * <p>所有写操作都会校验「书单归属」，越权操作返回 403 而不是静默失败 ——
 * 静默失败会让前端以为操作成功，用户看到数据没变只会反复点击。
 *
 * @author 毕设项目组
 */
public interface BookListService {

    /**
     * 书单广场（公开书单，按创建时间倒序）。
     *
     * @param currentUserId 当前登录用户（可为 null）；用于标记「我的书单」并让本人看到自己的私密书单
     * @param keyword       名称或简介关键词，可为空
     */
    PageVO<BookListVO> page(Long currentUserId, Long pageNum, Long pageSize, String keyword);

    /** 我的书单（含私密），按创建时间倒序 */
    PageVO<BookListVO> myLists(Long userId, Long pageNum, Long pageSize);

    /** 书单详情（含图书明细）；私密书单对非创建者返回 403 */
    BookListDetailVO detail(Long currentUserId, Long listId);

    /** 新建书单 */
    BookListVO create(Long userId, BookListRequest request);

    /** 编辑书单（校验归属） */
    BookListVO update(Long userId, Long listId, BookListRequest request);

    /** 删除书单（连同条目一起删除） */
    void remove(Long userId, Long listId);

    /**
     * 批量加入图书。
     *
     * @return 实际新增的条数（已存在的图书会被跳过，不计入）
     */
    int addBooks(Long userId, Long listId, List<Long> bookIds);

    /** 从书单移出某本图书 */
    void removeBook(Long userId, Long listId, Long bookId);
}
