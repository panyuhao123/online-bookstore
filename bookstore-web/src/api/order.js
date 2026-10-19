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

/** 订单详情 -> OrderVO（含 items 明细与 freightAmount / payableAmount / shipTime / finishTime） */
export function getOrder(orderId) {
  return request.get('/api/order/' + orderId)
}

/**
 * 运费规则（免登录）。
 *
 * @returns {Promise<{enabled, freeThreshold, baseFee, tip}>}
 */
export function getFreightRule() {
  return request.get('/api/order/freight-rule')
}

/**
 * 结算预览（不落库）：算商品小计 / 运费 / 应付。
 *
 * <p>为什么不前端自己算：运费规则（门槛、费率）随时可能被后台调整，前端算的
 * 一旦和服务端不一致，用户看到"包邮"、提交后却多出一笔运费，是典型的信任崩塌点。
 * 这里只求"看到的就是要付的"。
 *
 * @param {object} [data] {cartIds?}；不传表示按购物车已勾选条目
 * @returns {Promise<{goodsAmount, freightAmount, payableAmount, freeShipping,
 *                    freeThreshold, amountToFreeShipping, totalQuantity}>}
 */
export function settlePreview(data) {
  return request.post('/api/order/settle-preview', data || {})
}
