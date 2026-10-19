package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.HistoryItemVO;
import cn.edu.bookstore.model.vo.PageVO;

import java.util.List;

/**
 * 浏览历史业务接口（全部需要登录）。
 *
 * <p>未登录时的浏览记录仍由前端存在 localStorage —— 要求「看一本书先登录」
 * 会直接劝退新用户。等用户登录后，前端调用 {@link #merge} 把本地记录一次性带上来，
 * 这段「本地先记、登录后合并」的流程与既有前端实现（stores/history.js）保持一致。
 *
 * @author 毕设项目组
 */
public interface BrowseHistoryService {

    /**
     * 记录一次浏览（同一本书重复浏览只更新时间，不新增行）。
     *
     * @return 是否记录成功（图书不存在时返回 false，不抛异常 —— 埋点性质的操作不该中断浏览）
     */
    boolean record(Long userId, Long bookId);

    /** 我的浏览历史（按最近浏览时间倒序） */
    PageVO<HistoryItemVO> page(Long userId, Long pageNum, Long pageSize);

    /**
     * 合并未登录期间的本地浏览记录。
     *
     * @param bookIds 按浏览时间倒序（新的在前）
     * @return 实际新增的条数（已存在且更「新」的记录不会被旧记录覆盖）
     */
    int merge(Long userId, List<Long> bookIds);

    /** 删除单条浏览记录 */
    void removeOne(Long userId, Long bookId);

    /** 清空浏览历史 */
    void clear(Long userId);
}
