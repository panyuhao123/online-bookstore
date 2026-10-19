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
import cn.edu.bookstore.model.vo.OrderItemVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminOrderService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;

    @Override
    public PageVO<AdminOrderVO> page(AdminOrderQuery query) {
        query.normalize();

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(query.getStatus() != null, Orders::getStatus, query.getStatus())
                .eq(query.getUserId() != null, Orders::getUserId, query.getUserId())
                .like(StrUtil.isNotBlank(query.getOrderNo()), Orders::getOrderNo, query.getOrderNo())
                .orderByDesc(Orders::getCreateTime);

        IPage<Orders> page = ordersMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        List<Orders> records = page.getRecords() == null ? List.of() : page.getRecords();
        Map<Long, User> userMap = loadUserMap(records.stream().map(Orders::getUserId).distinct().toList());
        Map<Long, Integer> quantityMap = loadQuantityMap(records.stream().map(Orders::getId).toList());

        return PageVO.of(page, orders -> {
            AdminOrderVO vo = AdminOrderVO.from(orders, userMap.get(orders.getUserId()));
            vo.setTotalQuantity(quantityMap.getOrDefault(orders.getId(), 0));
            return vo;
        });
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
