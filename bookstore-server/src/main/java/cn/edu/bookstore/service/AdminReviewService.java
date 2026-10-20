package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminReviewQuery;
import cn.edu.bookstore.model.vo.AdminReviewSummaryVO;
import cn.edu.bookstore.model.vo.AdminReviewVO;
import cn.edu.bookstore.model.vo.PageVO;

/**
 * 管理端评价管理接口。
 *
 * @author 毕设项目组
 */
public interface AdminReviewService {

    /**
     * 评价分页：支持按图书 / 星级 / 状态筛选与关键词搜索。
     */
    PageVO<AdminReviewVO> page(AdminReviewQuery query);

    /**
     * 评价汇总指标（总数 / 正常 / 隐藏 / 平均分）。
     *
     * <p>平均分只统计正常评价，隐藏操作会立刻反映到这里。
     */
    AdminReviewSummaryVO summary();

    /**
     * 显示 / 隐藏评价。
     *
     * <p>用「隐藏」而不是「删除」优先处理违规评价：隐藏后该评价不再计入平均分、
     * 也不再出现在前台，但数据仍在，便于事后追溯与申诉。
     *
     * @param id     评价 ID
     * @param status 1-正常 0-隐藏
     */
    void updateStatus(Long id, Integer status);

    /**
     * 彻底删除评价（仅用于垃圾数据清理）。
     */
    void delete(Long id);
}
