package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.OrderCreateRequest;
import cn.edu.bookstore.model.vo.OrderVO;
import cn.edu.bookstore.model.vo.PageVO;

/**
 * 订单业务接口。
 *
 * @author 毕设项目组
 */
public interface OrderService {

    /**
     * 提交订单（模拟下单）：校验购物车 → 生成订单与明细 → 扣减库存 → 清空已下单的购物车条目。
     * 全过程在同一事务内，任一步失败整体回滚。
     *
     * @param userId  当前登录用户
     * @param request 下单参数
     * @return 生成的订单（含明细）
     */
    OrderVO create(Long userId, OrderCreateRequest request);

    /**
     * 模拟支付：仅把订单状态从「待支付」改为「已支付」，不对接任何第三方支付。
     *
     * <p>保留此方法是为了兼容 APP 端的「直接支付」交互；
     * 走支付渠道（如支付宝）时请用 {@link PayService}，
     * 其结果最终也通过 {@link #markPaidByOrderNo(String)} 落到订单状态上。
     *
     * @param userId  当前登录用户
     * @param orderId 订单 ID
     */
    void pay(Long userId, Long orderId);

    /**
     * 按订单号把订单标记为已支付（供支付渠道回调调用）。
     *
     * <p>与 {@link #pay(Long, Long)} 的区别：回调来自支付渠道，没有当前登录用户，
     * 因此不做归属校验 —— 安全性由「回调验签」保证，而不是由用户身份保证。
     *
     * <p>**必须是幂等的**：支付渠道会重复推送通知，重复调用不能造成异常或状态回退。
     * 实现用条件更新 {@code WHERE status = 待支付} 达到这一效果。
     *
     * @param orderNo 商户订单号
     * @return true 表示本次调用真正完成了「待支付 → 已支付」的变更；
     *         false 表示订单不存在、或已经支付过（重复通知，属正常情况）
     */
    boolean markPaidByOrderNo(String orderNo);

    /**
     * 确认收货：订单状态从「已发货」改为「已完成」。
     *
     * <p>至此形成完整闭环：下单 → 支付 → 后台发货 → 用户确认收货。
     *
     * @param userId  当前登录用户
     * @param orderId 订单 ID
     */
    void confirm(Long userId, Long orderId);

    /**
     * 取消订单：仅「待支付」可取消，取消后自动回补库存并回退销量。
     *
     * <p>库存回补与下单扣减是一对逆操作，必须在同一事务内完成：
     * 否则可能出现「订单已取消但库存没加回来」的不一致。
     *
     * @param userId  当前登录用户
     * @param orderId 订单 ID
     */
    void cancel(Long userId, Long orderId);

    /**
     * 自动取消超时未支付的订单（由定时任务调用，无当前用户上下文）。
     *
     * <p>与用户主动取消复用同一套取消逻辑（条件更新 + 库存回补 + 销量回退），
     * 区别只是「取消谁」由超时时间决定，而不是由用户点按钮决定。
     *
     * <p>幂等性由条件更新 {@code WHERE status = 待支付} 保证：
     * 多实例并发触发、或与用户手动取消撞在一起时，只有一方能改成功。
     *
     * @param timeoutMinutes 超时时长（分钟）。**传 0 或负数表示不按时间过滤，
     *                       所有待支付订单一律取消**（用于管理员一键清理 / 演示）
     * @param batchSize      单次最多处理多少单
     * @return 实际取消的订单数
     */
    int autoCancelTimeout(int timeoutMinutes, int batchSize);

    /**
     * 我的订单分页列表。
     *
     * @param userId   当前登录用户
     * @param status   订单状态筛选，为空表示全部
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageVO<OrderVO> page(Long userId, Integer status, Long pageNum, Long pageSize);

    /**
     * 订单详情（含明细，校验订单归属）。
     */
    OrderVO detail(Long userId, Long orderId);
}
