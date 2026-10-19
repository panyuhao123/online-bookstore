import request from './request'

/** 提交订单：{addressId, cartIds?}；不传 cartIds 按购物车已勾选下单 */
export function createOrder(data) {
  return request.post('/api/order/create', data)
}

/** 模拟支付 -> PrepayVO {paid, payUrl, ...} */
export function payOrder(orderId) {
  return request.post('/api/order/' + orderId + '/pay')
}

/** 取消订单：仅待支付可取消 */
export function cancelOrder(orderId) {
  return request.post('/api/order/' + orderId + '/cancel')
}

/** 确认收货：仅已发货可调用 */
export function confirmOrder(orderId) {
  return request.post('/api/order/' + orderId + '/confirm')
}

/** 订单分页：{status?, pageNum, pageSize} -> PageVO<OrderVO> */
export function pageOrders(params) {
  return request.get('/api/order/page', { params })
}

/** 订单详情 -> OrderVO（含 items 明细） */
export function getOrder(orderId) {
  return request.get('/api/order/' + orderId)
}
