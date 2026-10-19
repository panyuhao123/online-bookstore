package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeSummaryVO;

/**
 * 到货提醒管理接口（管理端）。
 *
 * <p>这里提供的是「补货优先级清单」而不是原始的订阅流水：
 * 运营真正需要回答的问题是「先补哪本书」，而这个问题只有把订阅按图书聚合之后才答得上来。
 *
 * @author 毕设项目组
 */
public interface AdminStockNoticeService {

    /**
     * 缺货图书等待人数分页（按待通知人数倒序）。
     *
     * @param keyword     图书标题关键词，可为空
     * @param onlyWaiting true-只看还有用户等待的图书（默认，这才是补货清单）；
     *                    false-包含全部有订阅记录的图书（含已通知完的，用于回顾）
     */
    PageVO<StockNoticeSummaryVO> summary(String keyword, Boolean onlyWaiting, Long pageNum, Long pageSize);

    /**
     * 把某本书的待通知订阅全部标记为「已通知」。
     *
     * @return 实际标记的条数
     */
    int markNotified(Long bookId);
}
