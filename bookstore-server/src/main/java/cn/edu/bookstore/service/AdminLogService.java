package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminLogQuery;
import cn.edu.bookstore.model.vo.OperationLogVO;
import cn.edu.bookstore.model.vo.PageVO;

import java.util.List;

/**
 * 管理端操作日志服务。
 *
 * @author 毕设项目组
 */
public interface AdminLogService {

    /**
     * 操作日志分页查询（按操作时间倒序）。
     *
     * @param query 筛选条件：模块 / 操作人 / 结果
     * @return 分页结果
     */
    PageVO<OperationLogVO> page(AdminLogQuery query);

    /**
     * 当前日志中出现过的模块列表，供前端筛选下拉框使用。
     *
     * <p>用 GROUP BY 从数据里取，而不是在前端硬编码模块名 ——
     * 新增管理模块后筛选框会自动出现新选项，不用改前端。
     *
     * @return 去重后的模块名列表
     */
    List<String> modules();
}
