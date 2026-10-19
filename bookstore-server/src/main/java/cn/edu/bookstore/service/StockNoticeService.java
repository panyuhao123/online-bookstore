package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeVO;

/**
 * 到货提醒业务接口（用户侧，全部需要登录）。
 *
 * @author 毕设项目组
 */
public interface StockNoticeService {

    /**
     * 订阅某本书的到货提醒。
     *
     * <p>规则：
     * <ul>
     *     <li>图书不存在 → 404；</li>
     *     <li>图书当前有货（stock &gt; 0）→ 422 并提示可直接下单 ——
     *         让用户订阅一本现在就能买的书没有意义，属于无效订阅；</li>
     *     <li>已在待通知状态 → 409；</li>
     *     <li>此前已取消或已通知过 → 复用原记录重新置为「待通知」。</li>
     * </ul>
     */
    StockNoticeVO subscribe(Long userId, Long bookId);

    /** 我的订阅列表（不含已取消），按订阅时间倒序 */
    PageVO<StockNoticeVO> myNotices(Long userId, Long pageNum, Long pageSize);

    /** 取消订阅（保留记录，状态置为「已取消」） */
    void unsubscribe(Long userId, Long bookId);
}
