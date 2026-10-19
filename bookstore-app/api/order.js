/** 订单模块接口（需登录） */
import { get, post } from '../common/request'

/**
 * 提交订单：{addressId, cartIds?}
 * 不传 cartIds 时按购物车全部已勾选条目下单，成功后对应购物车条目被清除
 */
export const createOrder = (data) => post('/api/order/create', data)

/** 模拟支付 */
export const payOrder = (orderId) => post('/api/order/' + orderId + '/pay')

/** 确认收货：订单由「已发货」流转为「已完成」，仅已发货订单可调用 */
export const confirmOrder = (orderId) => post('/api/order/' + orderId + '/confirm')

/** 订单分页：{status?, pageNum, pageSize} -> PageVO<OrderVO> */
export const pageOrders = (params) => get('/api/order/page', params)

/** 订单详情 -> OrderVO（含 items 明细快照） */
export const getOrder = (orderId) => get('/api/order/' + orderId)
