package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.model.dto.AdminLogQuery;
import cn.edu.bookstore.model.vo.OperationLogVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端操作日志接口。
 *
 * <p>只读：日志由 AOP 切面自动写入，不提供手工新增或修改接口 ——
 * 审计数据一旦可被改写就失去了意义。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    /** 日志分页：GET /api/admin/log/page?module=&adminName=&success=&pageNum=&pageSize= */
    @GetMapping("/page")
    public Result<PageVO<OperationLogVO>> page(@ModelAttribute AdminLogQuery query) {
        return Result.success(adminLogService.page(query));
    }

    /** 模块列表（筛选下拉框数据源） */
    @GetMapping("/modules")
    public Result<List<String>> modules() {
        return Result.success(adminLogService.modules());
    }
}
