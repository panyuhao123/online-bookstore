package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.AdminUserQuery;
import cn.edu.bookstore.model.vo.AdminUserSummaryVO;
import cn.edu.bookstore.model.vo.AdminUserVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.UserStatVO;
import cn.edu.bookstore.service.AdminUserService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端用户业务实现。
 *
 * <p>安全约定：禁用用户不会删除其历史订单与地址，且因为 Spring Security 每次请求都会
 * 复核用户状态，被禁用的用户即使拿着未过期的令牌也无法继续操作（401）。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** 正常 */
    private static final int STATUS_ENABLED = 1;

    /** 禁用 */
    private static final int STATUS_DISABLED = 0;

    private final UserMapper userMapper;
    private final OrdersMapper ordersMapper;

    @Override
    public AdminUserSummaryVO summary() {
        AdminUserSummaryVO summary = userMapper.selectAdminSummary();
        if (summary == null) {
            summary = new AdminUserSummaryVO();
            summary.setTotal(0L);
            summary.setActive(0L);
            summary.setDisabled(0L);
            summary.setNewThisMonth(0L);
        }
        return summary;
    }

    @Override
    public PageVO<AdminUserVO> page(AdminUserQuery query) {
        query.normalize();

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(User::getPhone, query.getKeyword())
                        .or()
                        .like(User::getNickname, query.getKeyword()))
                .orderByDesc(User::getId);

        IPage<User> page = userMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        List<User> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<Long> userIds = records.stream().map(User::getId).toList();
        Map<Long, UserStatVO> statMap = loadStatMap(userIds);

        return PageVO.of(page, user -> {
            AdminUserVO vo = AdminUserVO.from(user);
            UserStatVO stat = statMap.get(user.getId());
            vo.setOrderCount(stat == null ? 0L : stat.getOrderCount());
            vo.setTotalAmount(stat == null ? BigDecimal.ZERO : stat.getTotalAmount());
            return vo;
        });
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = requireUser(id);
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态值不合法，仅支持 1-启用 / 0-禁用");
        }

        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        log.info("管理端修改用户状态 userId={} status={} oldStatus={}", id, status, user.getStatus());
    }

    /** 查询用户，不存在即抛业务异常 */
    private User requireUser(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户 ID 不能为空");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /** 本页用户的订单聚合：一次分组查询替代逐用户统计 */
    private Map<Long, UserStatVO> loadStatMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserStatVO> stats = ordersMapper.selectUserStats(userIds);
        return stats == null ? Collections.emptyMap()
                : stats.stream().collect(Collectors.toMap(UserStatVO::getUserId, s -> s, (a, b) -> a));
    }
}
