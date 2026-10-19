package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminOrderQuery;
import cn.edu.bookstore.model.vo.AdminOrderVO;
import cn.edu.bookstore.model.vo.PageVO;

/**
 * 管理端订单业务接口。
 *
 * <p>与 APP 端订单服务的关键差异：不绑定当前用户，管理员可查看并操作全部订单。
 *
 * @author 毕设项目组
 */
public interface AdminOrderService {

    /** 后台订单分页：支持订单号、状态、下单用户筛选 */
    PageVO<AdminOrderVO> page(AdminOrderQuery query);

    /** 订单详情（含明细与下单用户信息） */
    AdminOrderVO detail(Long id);

    /**
     * 发货：订单状态由「已支付」流转为「已发货」。
     *
     * <p>这是唯一由管理员驱动的状态流转 —— 待支付改由用户支付、已发货改由用户确认收货。
     */
    void ship(Long id);
}
