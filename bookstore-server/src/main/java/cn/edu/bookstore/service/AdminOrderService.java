package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminOrderQuery;
import cn.edu.bookstore.model.vo.AdminOrderVO;
import cn.edu.bookstore.model.vo.BatchShipResultVO;
import cn.edu.bookstore.model.vo.PageVO;

import java.util.List;

/**
 * 管理端订单业务接口。
 *
 * <p>与 APP 端订单服务的关键差异：不绑定当前用户，管理员可查看并操作全部订单。
 *
 * @author 毕设项目组
 */
public interface AdminOrderService {

    /** 后台订单分页：支持订单号 / 手机号关键词、状态、下单用户、下单日期区间筛选 */
    PageVO<AdminOrderVO> page(AdminOrderQuery query);

    /** 订单详情（含明细与下单用户信息） */
    AdminOrderVO detail(Long id);

    /**
     * 发货：订单状态由「已支付」流转为「已发货」。
     *
     * <p>这是唯一由管理员驱动的状态流转 —— 待支付改由用户支付、已发货改由用户确认收货。
     */
    void ship(Long id);

    /**
     * 批量发货：逐单条件更新，只发「已支付」的单，其余记录跳过原因。
     *
     * @return 成功笔数与跳过明细
     */
    BatchShipResultVO batchShip(List<Long> ids);

    /**
     * 按当前筛选条件取导出用的订单列表（不分页，上限由实现控制）。
     *
     * <p>导出走独立方法而不是复用 {@link #page}：分页会把 pageSize 收敛到 50，
     * 而导出要的是「当前筛选下的全部」。
     */
    List<AdminOrderVO> exportList(AdminOrderQuery query);
}
