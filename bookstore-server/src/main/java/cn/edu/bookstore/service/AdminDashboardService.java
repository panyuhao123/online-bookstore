package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.OverviewVO;

/**
 * 管理端首页数据总览。
 *
 * @author 毕设项目组
 */
public interface AdminDashboardService {

    /** 汇总后台首页需要的统计指标 */
    OverviewVO overview();
}
