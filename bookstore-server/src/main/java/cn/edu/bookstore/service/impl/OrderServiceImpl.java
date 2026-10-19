package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.OrderStatus;
import cn.edu.bookstore.common.observability.BusinessMetrics;
import cn.edu.bookstore.entity.Address;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Cart;
import cn.edu.bookstore.entity.OrderItem;
import cn.edu.bookstore.entity.Orders;
import cn.edu.bookstore.mapper.AddressMapper;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CartMapper;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.model.dto.OrderCreateRequest;
import cn.edu.bookstore.model.dto.OrderSettleRequest;
import cn.edu.bookstore.model.vo.OrderItemVO;
import cn.edu.bookstore.model.vo.OrderSettleVO;
import cn.edu.bookstore.model.vo.OrderVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BookService;
import cn.edu.bookstore.service.FreightService;
import cn.edu.bookstore.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单业务实现 —— 本项目的核心事务逻辑。
 *
 * <p>下单流程（全程同一事务，任一步骤失败整体回滚）：
 * <ol>
 *     <li>校验收货地址归属当前用户；</li>
 *     <li>取出待结算的购物车条目（指定 ID 或全部已勾选）；</li>
 *     <li>校验图书是否上架、库存是否充足，并计算总金额；</li>
 *     <li>写入订单主表与订单明细（明细保存书名/封面/单价快照）；</li>
 *     <li>扣减库存并累加销量 —— 使用带条件的 UPDATE 防止超卖；</li>
 *     <li>删除已下单的购物车条目。</li>
 * </ol>
 *
 * <p>防超卖说明：库存扣减采用
 * {@code UPDATE book SET stock = stock - n WHERE id = ? AND stock >= n}，
 * 由数据库保证原子性；若影响行数为 0 说明库存已被其他请求抢完，直接抛异常回滚。
 *
 * <p>缓存一致性：下单与取消都会改变库存，进而影响图书详情里的 stock 字段，
 * 因此这两个方法执行成功后清空图书详情缓存（book）—— 缓存只加速读取，
 * 绝不允许它让用户看到已经卖完还显示有货的库存。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int STATUS_ON_SALE = 1;
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final AddressMapper addressMapper;
    private final BookMapper bookMapper;
    private final BookService bookService;

    /**
     * 运费规则来源。与结算预览共用同一个实现，保证「预览算出来的应付」
     * 与「下单真正落库的应付」出自同一段逻辑，不会因两边各写一份而漂移。
     */
    private final FreightService freightService;

    /**
     * 用 ObjectProvider 而不是直接注入 CacheManager：
     * bookstore.cache.enabled=false 时缓存配置类整体不加载，容器里没有 CacheManager，
     * 直接注入会导致启动失败。ObjectProvider 允许「有就用、没有就算了」。
     */
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    /** 业务埋点：下单量与支付量是运营最关心的两个指标 */
    private final BusinessMetrics businessMetrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            // 库存与销量变了：图书详情与热门榜整体清空
            @CacheEvict(value = {"book", "hotBook"}, allEntries = true),
            // 下单会删除已结算的购物车条目，故只清当前用户的购物车缓存（按 key 精确失效，不波及他人）
            @CacheEvict(value = "cart", key = "#userId")
    })
    public OrderVO create(Long userId, OrderCreateRequest request) {
        // 1. 收货地址：必须存在且属于当前用户
        Address address = addressMapper.selectById(request.getAddressId());
        if (address == null) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "收货地址不存在，请重新选择");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用他人的收货地址");
        }

        // 2. 待结算的购物车条目
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId);
        if (request.getCartIds() != null && !request.getCartIds().isEmpty()) {
            cartWrapper.in(Cart::getId, request.getCartIds());
        } else {
            cartWrapper.eq(Cart::getChecked, 1);
        }
        List<Cart> carts = cartMapper.selectList(cartWrapper);
        if (carts == null || carts.isEmpty()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "请先选择要购买的商品");
        }

        // 3. 批量取图书，校验并计算金额
        Map<Long, Book> bookMap = bookService.mapByIds(
                carts.stream().map(Cart::getBookId).distinct().toList());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>(carts.size());
        for (Cart cart : carts) {
            Book book = bookMap.get(cart.getBookId());
            if (book == null || !Integer.valueOf(STATUS_ON_SALE).equals(book.getStatus())) {
                throw new BusinessException(ErrorCode.BIZ_ERROR, "购物车中包含已下架的图书，请先移除");
            }
            if (book.getStock() < cart.getQuantity()) {
                throw new BusinessException(ErrorCode.BIZ_ERROR,
                        "《" + book.getTitle() + "》库存不足，仅剩 " + book.getStock() + " 件");
            }
            BigDecimal subtotal = book.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity()));

            OrderItem item = new OrderItem();
            item.setBookId(book.getId());
            item.setBookTitle(book.getTitle());
            item.setBookCover(book.getCoverUrl());
            item.setPrice(book.getPrice());
            item.setQuantity(cart.getQuantity());
            item.setSubtotal(subtotal);
            orderItems.add(item);

            totalAmount = totalAmount.add(subtotal);
        }

        // 4. 运费：与结算预览走同一段逻辑。
        // 注意 total_amount 存的是「商品小计」，不含运费；应付 = total_amount + freight_amount。
        // 存量订单的 freight_amount 默认为 0，语义上正好是「当时包邮」，无需数据迁移。
        BigDecimal freightAmount = freightService.freightOf(totalAmount);

        // 5. 订单主表
        Orders orders = new Orders();
        orders.setOrderNo(generateOrderNo());
        orders.setUserId(userId);
        orders.setTotalAmount(totalAmount);
        orders.setFreightAmount(freightAmount);
        orders.setStatus(OrderStatus.PENDING_PAY.getCode());
        orders.setAddressSnapshot(buildAddressSnapshot(address));
        ordersMapper.insert(orders);

        // 6. 订单明细
        for (OrderItem item : orderItems) {
            item.setOrderId(orders.getId());
            orderItemMapper.insert(item);
        }

        // 7. 扣减库存（条件更新防超卖）
        for (OrderItem item : orderItems) {
            int rows = bookMapper.update(null, new LambdaUpdateWrapper<Book>()
                    .setSql("stock = stock - " + item.getQuantity()
                            + ", sales = sales + " + item.getQuantity())
                    .eq(Book::getId, item.getBookId())
                    .ge(Book::getStock, item.getQuantity()));
            if (rows == 0) {
                // 抛异常触发回滚：订单与已扣库存一并撤销
                throw new BusinessException(ErrorCode.BIZ_ERROR,
                        "《" + item.getBookTitle() + "》库存不足，请调整数量后重试");
            }
        }

        // 8. 清空已下单的购物车条目
        cartMapper.deleteBatchIds(carts.stream().map(Cart::getId).toList());

        log.info("下单成功 userId={} orderNo={} 商品小计={} 运费={} 应付={} 明细数={}",
                userId, orders.getOrderNo(), totalAmount, freightAmount,
                totalAmount.add(freightAmount), orderItems.size());
        // 埋点放在最后一步：走到这里订单、明细、库存、购物车都已处理完毕，
        // 计数才与「真正成功的订单」对得上（中途库存不足等异常会抛在上面，不会计数）。
        businessMetrics.orderCreated();

        OrderVO vo = OrderVO.from(orders);
        vo.setItems(orderItems.stream().map(OrderItemVO::from).toList());
        vo.setTotalQuantity(orderItems.stream().mapToInt(OrderItem::getQuantity).sum());
        return vo;
    }

    @Override
    public OrderSettleVO settlePreview(Long userId, OrderSettleRequest request) {
        // 「取哪些购物车条目」的规则必须与 create 完全一致（传了 cartIds 就只算这些，
        // 否则算所有已勾选的），否则预览金额与下单金额会在「部分勾选」的场景下不一致 ——
        // 而部分勾选恰恰是结算页最常见的使用方式。
        List<Long> cartIds = request == null ? null : request.getCartIds();
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId);
        if (cartIds != null && !cartIds.isEmpty()) {
            cartWrapper.in(Cart::getId, cartIds);
        } else {
            cartWrapper.eq(Cart::getChecked, 1);
        }
        List<Cart> carts = cartMapper.selectList(cartWrapper);

        // 购物车为空不是错误：结算页刚进去、或用户取消了全部勾选，都属于正常状态。
        // 这里返回一个「0 商品 + 按规则算出的运费」，让前端能原样渲染空态，
        // 而不是抛异常让页面弹一个红色的错误提示。
        if (carts == null || carts.isEmpty()) {
            return freightService.settle(BigDecimal.ZERO, 0);
        }

        Map<Long, Book> bookMap = bookService.mapByIds(
                carts.stream().map(Cart::getBookId).distinct().toList());

        BigDecimal goodsAmount = BigDecimal.ZERO;
        int totalQuantity = 0;
        for (Cart cart : carts) {
            Book book = bookMap.get(cart.getBookId());
            if (book == null) {
                // 图书已被删除（购物车条目本应由外键约束清掉，这里只是兜底防御）：
                // 跳过而不是中止，预览是「看一眼要花多少钱」，不该因为一条脏数据整页报错。
                log.warn("结算预览遇到已不存在的图书 cartId={} bookId={}", cart.getId(), cart.getBookId());
                continue;
            }
            goodsAmount = goodsAmount.add(book.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
            totalQuantity += cart.getQuantity();
        }
        return freightService.settle(goodsAmount, totalQuantity);
    }

    @Override
    public void pay(Long userId, Long orderId) {
        Orders orders = requireOwnedOrder(userId, orderId);
        if (OrderStatus.PAID.getCode() == orders.getStatus()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "该订单已支付，请勿重复操作");
        }
        if (OrderStatus.PENDING_PAY.getCode() != orders.getStatus()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "当前订单状态为「" + OrderStatus.textOf(orders.getStatus()) + "」，无法支付");
        }

        // 模拟支付：仅变更状态与支付时间，不调用任何第三方支付接口
        orders.setStatus(OrderStatus.PAID.getCode());
        orders.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(orders);
        log.info("模拟支付成功 userId={} orderNo={}", userId, orders.getOrderNo());
        businessMetrics.orderPaid();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaidByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return false;
        }
        // 条件更新保证幂等：支付渠道重复推送通知时，只有第一次能把状态从「待支付」推进到「已支付」，
        // 后续调用影响行数为 0，直接返回 false，不会抛异常也不会重复写入支付时间。
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, OrderStatus.PAID.getCode())
                .set(Orders::getPayTime, LocalDateTime.now())
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getStatus, OrderStatus.PENDING_PAY.getCode()));
        if (rows > 0) {
            log.info("支付回调入账成功 orderNo={}", orderNo);
            // 只在状态确实发生推进（rows>0）时计数：重复推送的通知不会让支付量虚高，
            // 这也正是「幂等」在监控上的体现。
            businessMetrics.orderPaid();
            return true;
        }
        log.info("支付回调未产生状态变更（订单已处理或不存在）orderNo={}", orderNo);
        return false;
    }

    @Override
    public void confirm(Long userId, Long orderId) {
        Orders orders = requireOwnedOrder(userId, orderId);
        if (OrderStatus.SHIPPED.getCode() != orders.getStatus()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "当前订单状态为「" + OrderStatus.textOf(orders.getStatus()) + "」，仅「已发货」订单可确认收货");
        }

        orders.setStatus(OrderStatus.COMPLETED.getCode());
        orders.setFinishTime(LocalDateTime.now());
        ordersMapper.updateById(orders);
        log.info("确认收货成功 userId={} orderNo={}", userId, orders.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"book", "hotBook"}, allEntries = true)
    public void cancel(Long userId, Long orderId) {
        Orders orders = requireOwnedOrder(userId, orderId);
        if (OrderStatus.PENDING_PAY.getCode() != orders.getStatus()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "当前订单状态为「" + OrderStatus.textOf(orders.getStatus()) + "」，仅「待支付」订单可取消");
        }
        if (!cancelPendingOrder(orderId)) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "订单状态已变更，请刷新后重试");
        }
        log.info("取消订单成功 userId={} orderNo={}", userId, orders.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoCancelTimeout(int timeoutMinutes, int batchSize) {
        // 只取候选 ID：先筛出超时的待支付订单，再逐单走与用户取消完全相同的逻辑。
        // LIMIT 的作用是给批量操作封顶，避免历史积压时开一个超长事务；
        // batchSize 来自配置且已在 AppProperties 里校验为 1~5000 的正整数，不存在注入风险。
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .select(Orders::getId, Orders::getOrderNo, Orders::getCreateTime)
                .eq(Orders::getStatus, OrderStatus.PENDING_PAY.getCode())
                .orderByAsc(Orders::getId);

        // timeoutMinutes <= 0 表示「不按时间过滤，所有待支付订单一律取消」。
        //
        // 为什么不直接用 create_time < now-0：orders.create_time 是 DATETIME（无小数秒），
        // 而应用传入的时间带纳秒，MySQL 写入时会四舍五入到秒 ——
        // 于是刚插入的行，其 create_time 可能比真实插入时刻晚最多 0.5 秒，
        // 用「小于当前时刻」去比就会漏掉这些行。
        // 阈值是 30 分钟时这点误差毫无影响，但阈值取 0 时会导致结果不稳定，
        // 因此这里对「立即取消全部」单独走一条不含时间条件的路径。
        LocalDateTime deadline = null;
        if (timeoutMinutes > 0) {
            deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
            wrapper.lt(Orders::getCreateTime, deadline);
        }
        wrapper.last("LIMIT " + batchSize);

        List<Orders> candidates = ordersMapper.selectList(wrapper);
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        for (Orders candidate : candidates) {
            if (cancelPendingOrder(candidate.getId())) {
                cancelled++;
                log.info("超时订单已自动取消 orderId={} orderNo={} 下单时间={}（判定阈值：{}）",
                        candidate.getId(), candidate.getOrderNo(), candidate.getCreateTime(),
                        deadline == null ? "不限时间（全部待支付）" : deadline);
            }
        }

        // 只有真的取消了订单才清图书缓存。
        // 这里不用 @CacheEvict：该注解无法根据返回值决定是否执行，
        // 而定时任务每 5 分钟跑一次，若无条件清缓存会把图书详情缓存白白冲掉、拉低命中率。
        if (cancelled > 0) {
            CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
            if (cacheManager != null) {
                // 顺序会变化（销量回退了），所以详情缓存与热门榜缓存都要清
                for (String cacheName : List.of("book", "hotBook")) {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                    }
                }
            }
        }
        return cancelled;
    }

    /**
     * 取消一笔「待支付」订单的核心动作：条件更新状态 + 回补库存与销量。
     *
     * <p>用户主动取消与超时自动取消共用这一段，保证两条路径的行为完全一致 ——
     * 不会出现「用户取消会回补库存、定时任务取消却漏了」这类只在某条路径上才暴露的 bug。
     *
     * @return true 表示取消成功；false 表示订单状态已被其他请求抢先改变（并发下属正常）
     */
    private boolean cancelPendingOrder(Long orderId) {
        // 条件更新：只有仍处于「待支付」时才改为「已取消」。
        // 思路与防超卖一致 —— 由数据库保证并发下只有一个请求能成功，
        // 避免用户连点两次、或用户取消与定时任务撞车，导致库存被回补两次。
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, OrderStatus.CANCELLED.getCode())
                .eq(Orders::getId, orderId)
                .eq(Orders::getStatus, OrderStatus.PENDING_PAY.getCode()));
        if (rows == 0) {
            return false;
        }

        // 回补库存并回退销量：下单时「库存 -n、销量 +n」，取消必须对称地反向执行，
        // 否则销量会虚高、库存会凭空减少。GREATEST 兜底避免销量出现负数（例如管理员调过数据）。
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            bookMapper.update(null, new LambdaUpdateWrapper<Book>()
                    .setSql("stock = stock + " + item.getQuantity()
                            + ", sales = GREATEST(sales - " + item.getQuantity() + ", 0)")
                    .eq(Book::getId, item.getBookId()));
        }
        return true;
    }

    @Override
    public PageVO<OrderVO> page(Long userId, Integer status, Long pageNum, Long pageSize) {
        long current = (pageNum == null || pageNum < 1) ? 1L : pageNum;
        long size = (pageSize == null || pageSize < 1) ? 10L : Math.min(pageSize, 50L);

        IPage<Orders> page = ordersMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
                        .eq(status != null, Orders::getStatus, status)
                        .orderByDesc(Orders::getCreateTime));

        List<Orders> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<Long> orderIds = records.stream().map(Orders::getId).toList();
        // 一次性查出本页所有订单的明细与件数，避免逐单查询造成 N+1
        Map<Long, Integer> quantityMap = loadQuantityMap(orderIds);
        Map<Long, List<OrderItemVO>> itemMap = loadItemMap(orderIds);

        return PageVO.of(page, orders -> {
            OrderVO vo = OrderVO.from(orders);
            vo.setTotalQuantity(quantityMap.getOrDefault(orders.getId(), 0));
            // 列表页同样返回明细，APP 端可直接渲染商品封面与书名
            vo.setItems(itemMap.getOrDefault(orders.getId(), List.of()));
            return vo;
        });
    }

    @Override
    public OrderVO detail(Long userId, Long orderId) {
        Orders orders = requireOwnedOrder(userId, orderId);
        OrderVO vo = OrderVO.from(orders);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        vo.setItems(items.stream().map(OrderItemVO::from).toList());
        vo.setTotalQuantity(items.stream().mapToInt(OrderItem::getQuantity).sum());
        return vo;
    }

    /** 校验订单存在且属于当前用户（防止越权查看他人订单） */
    private Orders requireOwnedOrder(Long userId, Long orderId) {
        Orders orders = ordersMapper.selectById(orderId);
        if (orders == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!orders.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看他人的订单");
        }
        return orders;
    }

    /** 统计每个订单的商品件数：orderId -> 总件数 */
    private Map<Long, Integer> loadQuantityMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        return items.stream().collect(Collectors.groupingBy(
                OrderItem::getOrderId,
                Collectors.summingInt(OrderItem::getQuantity)));
    }

    /** 批量取本页订单的明细快照，按 orderId 分组（一次查询，避免 N+1） */
    private Map<Long, List<OrderItemVO>> loadItemMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds)
                        .orderByAsc(OrderItem::getId));
        return items.stream().collect(Collectors.groupingBy(
                OrderItem::getOrderId,
                Collectors.mapping(OrderItemVO::from, Collectors.toList())));
    }

    /** 地址快照：收件人 / 电话 / 完整地址 拼成一行，用户后续改地址不影响历史订单 */
    private String buildAddressSnapshot(Address address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.getReceiver()).append(' ').append(address.getPhone()).append(' ');
        if (address.getProvince() != null) {
            sb.append(address.getProvince());
        }
        if (address.getCity() != null) {
            sb.append(address.getCity());
        }
        if (address.getDistrict() != null) {
            sb.append(address.getDistrict());
        }
        sb.append(address.getDetail());
        return sb.toString();
    }

    /** 订单号：BK + 时间戳 + 4 位随机数 */
    private String generateOrderNo() {
        return "BK" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
