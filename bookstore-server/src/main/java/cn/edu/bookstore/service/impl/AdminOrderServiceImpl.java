package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.entity.OrderItem;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.AdminOrderQuery;
import cn.edu.bookstore.model.vo.AdminOrderVO;
import cn.edu.bookstore.model.vo.BatchShipResultVO;
import cn.edu.bookstore.model.vo.OrderItemVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminOrderService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端订单业务实现。
 *
 * <p>性能约定：列表页需要展示「下单用户」与「商品件数」，这两项数据都不在 orders 表中，
 * 统一采用「先查本页订单 → 再按 ID 批量查关联表 → 内存组装」的方式，避免 N+1 查询。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    /** 单次导出上限：超过这个量的导出应当按日期区间拆分，而不是一次拉全表 */
    private static final int EXPORT_LIMIT = 5000;

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;

    @Override
    public PageVO<AdminOrderVO> page(AdminOrderQuery query) {
        query.normalize();
        checkDateRange(query);

        IPage<Orders> page = ordersMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query));

        List<Orders> records = page.getRecords() == null ? List.of() : page.getRecords();
        Map<Long, AdminOrderVO> voMap = toVoMap(records);
        return PageVO.of(page, order -> voMap.get(order.getId()));
    }

    /**
     * 组装查询条件（分页与导出共用）。
     *
     * <p>抽出来是为了让「列表看到的」与「导出的」永远是同一套条件 ——
     * 两处各写一遍，早晚会出现「页面筛出 12 条、导出 14 条」这种无法解释的差异。
     */
    private LambdaQueryWrapper<Orders> buildWrapper(AdminOrderQuery query) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(query.getStatus() != null, Orders::getStatus, query.getStatus())
                .eq(query.getUserId() != null, Orders::getUserId, query.getUserId())
                .like(StrUtil.isNotBlank(query.getOrderNo()), Orders::getOrderNo, query.getOrderNo())
                // 区间闭合到「天」：起始取 00:00:00，结束取 23:59:59，
                // 否则用户选 10-01 ~ 10-01 会得到 0 条（只比了那一刻）
                .ge(query.getCreateTimeStart() != null, Orders::getCreateTime,
                        query.getCreateTimeStart() == null ? null : query.getCreateTimeStart().atStartOfDay())
                .le(query.getCreateTimeEnd() != null, Orders::getCreateTime,
                        query.getCreateTimeEnd() == null ? null : query.getCreateTimeEnd().atTime(LocalTime.MAX))
                .orderByDesc(Orders::getCreateTime);

        applyKeyword(wrapper, query.getKeyword());
        return wrapper;
    }

    /**
     * 关键词同时命中「订单号」与「下单用户手机号」。
     *
     * <p>先生成手机号命中的用户 ID 列表，再用 IN 过滤订单：
     * 如果直接写子查询或拼字符串，一是 keyword 会进入 SQL 文本（注入风险），
     * 二是 orders.user_id 上的索引在函数/子查询下无法稳定命中。
     *
     * <p>手机号命中数上限 200：手机号是强条件，正常只会命中 1 个用户，
     * 上限只用于兜住「输入 13 后一堆人匹配」的模糊场景。
     */
    private void applyKeyword(LambdaQueryWrapper<Orders> wrapper, String keyword) {
        String kw = StrUtil.trimToNull(keyword);
        if (kw == null) {
            return;
        }
        List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .like(User::getPhone, kw)
                        .last("LIMIT 200"))
                .stream().map(User::getId).toList();

        wrapper.and(q -> {
            q.like(Orders::getOrderNo, kw);
            if (!userIds.isEmpty()) {
                q.or().in(Orders::getUserId, userIds);
            }
        });
    }

    /** 订单列表 -> VO 映射表（批量补用户与件数，避免 N+1） */
    private Map<Long, AdminOrderVO> toVoMap(List<Orders> records) {
        Map<Long, User> userMap = loadUserMap(records.stream().map(Orders::getUserId).distinct().toList());
        Map<Long, Integer> quantityMap = loadQuantityMap(records.stream().map(Orders::getId).toList());
        Map<Long, AdminOrderVO> voMap = new HashMap<>();
        for (Orders order : records) {
            AdminOrderVO vo = AdminOrderVO.from(order, userMap.get(order.getUserId()));
            vo.setTotalQuantity(quantityMap.getOrDefault(order.getId(), 0));
            voMap.put(order.getId(), vo);
        }
        return voMap;
    }

    /**
     * 校验日期区间。
     *
     * <p>不自动交换起止日期：静默纠正会让操作员以为自己筛的是另一个区间，
     * 直接报错比偷偷改条件更安全。
     */
    private void checkDateRange(AdminOrderQuery query) {
        if (query.getCreateTimeStart() != null && query.getCreateTimeEnd() != null
                && query.getCreateTimeStart().isAfter(query.getCreateTimeEnd())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "起始日期不能晚于结束日期");
        }
    }

    @Override
    public AdminOrderVO detail(Long id) {
        Orders orders = requireOrder(id);
        AdminOrderVO vo = AdminOrderVO.from(orders, userMapper.selectById(orders.getUserId()));

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, id)
                        .orderByAsc(OrderItem::getId));
        vo.setItems(items.stream().map(OrderItemVO::from).toList());
        vo.setTotalQuantity(items.stream().mapToInt(OrderItem::getQuantity).sum());
        return vo;
    }

    @Override
    public void ship(Long id) {
        Orders orders = requireOrder(id);
        if (OrderStatus.PAID.getCode() != orders.getStatus()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "当前订单状态为「" + OrderStatus.textOf(orders.getStatus()) + "」，仅「已支付」订单可发货");
        }

        Orders update = new Orders();
        update.setId(id);
        update.setStatus(OrderStatus.SHIPPED.getCode());
        // 发货时间必须与状态在同一次 UPDATE 里落库：拆成两次写会出现「状态已发货、时间为空」
        // 的中间态，而这个中间态正好会把订单时间线渲染成一个空缺节点。
        update.setShipTime(LocalDateTime.now());
        ordersMapper.updateById(update);
        log.info("管理端发货成功 orderId={} orderNo={}", id, orders.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchShipResultVO batchShip(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少选择一笔订单");
        }

        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Orders> orderMap = ordersMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Orders::getId, Function.identity(), (a, b) -> a));

        BatchShipResultVO result = new BatchShipResultVO();
        int shipped = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Long id : distinctIds) {
            Orders order = orderMap.get(id);
            if (order == null) {
                result.getSkipped().add(new BatchShipResultVO.Skipped(id, null, "订单不存在"));
                continue;
            }
            // 条件更新而不是「先查状态再更新」：两个管理员同时点批量发货时，
            // 判状态与写状态之间会留出竞态窗口，affected=0 才是真正的「这次没轮到我」
            int affected = ordersMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                    .set(Orders::getStatus, OrderStatus.SHIPPED.getCode())
                    .set(Orders::getShipTime, now)
                    .eq(Orders::getId, id)
                    .eq(Orders::getStatus, OrderStatus.PAID.getCode()));
            if (affected > 0) {
                shipped++;
            } else {
                result.getSkipped().add(new BatchShipResultVO.Skipped(id, order.getOrderNo(),
                        "当前状态为「" + OrderStatus.textOf(order.getStatus()) + "」，仅已支付可发货"));
            }
        }

        result.setShipped(shipped);
        log.info("管理端批量发货 请求={} 成功={} 跳过={}", distinctIds.size(), shipped, result.getSkipped().size());
        return result;
    }

    @Override
    public List<AdminOrderVO> exportList(AdminOrderQuery query) {
        checkDateRange(query);
        // 导出上限：单次最多 5000 行。再大的量应当按日期区间拆分导出，
        // 一次性拉全表既撑爆内存，也会把 Excel 卡死。
        List<Orders> records = ordersMapper.selectList(buildWrapper(query).last("LIMIT " + EXPORT_LIMIT));
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, AdminOrderVO> voMap = toVoMap(records);
        // 按查询顺序输出，保证导出行序与列表页一致（下单时间倒序）
        return records.stream().map(order -> voMap.get(order.getId())).toList();
    }

    /** 查询订单，不存在即抛业务异常 */
    private Orders requireOrder(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单 ID 不能为空");
        }
        Orders orders = ordersMapper.selectById(id);
        if (orders == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return orders;
    }

    /** 下单用户 ID -> 用户信息，批量查询避免逐单查用户表 */
    private Map<Long, User> loadUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().in(User::getId, userIds));
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    /** 订单 ID -> 商品总件数 */
    private Map<Long, Integer> loadQuantityMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        Map<Long, Integer> map = new HashMap<>();
        for (OrderItem item : items) {
            map.merge(item.getOrderId(), item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        return map;
    }
}
