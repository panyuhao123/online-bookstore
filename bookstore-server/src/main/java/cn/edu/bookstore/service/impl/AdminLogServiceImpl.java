package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.OperationLog;
import cn.edu.bookstore.mapper.OperationLogMapper;
import cn.edu.bookstore.model.dto.AdminLogQuery;
import cn.edu.bookstore.model.vo.OperationLogVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminLogService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 管理端操作日志实现。
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class AdminLogServiceImpl implements AdminLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public PageVO<OperationLogVO> page(AdminLogQuery query) {
        query.normalize();

        // 按操作时间倒序：看日志永远是「先看最近发生了什么」，
        // create_time 上有 idx_oplog_create_time 索引支撑。
        IPage<OperationLog> page = operationLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<OperationLog>()
                        .like(StrUtil.isNotBlank(query.getModule()), OperationLog::getModule, query.getModule())
                        .like(StrUtil.isNotBlank(query.getAdminName()),
                                OperationLog::getAdminName, query.getAdminName())
                        .eq(query.getSuccess() != null, OperationLog::getSuccess, query.getSuccess())
                        .orderByDesc(OperationLog::getCreateTime)
                        .orderByDesc(OperationLog::getId));

        return PageVO.of(page, OperationLogVO::from);
    }

    @Override
    public List<String> modules() {
        return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .select(OperationLog::getModule)
                        .groupBy(OperationLog::getModule))
                .stream()
                .map(OperationLog::getModule)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }
}
