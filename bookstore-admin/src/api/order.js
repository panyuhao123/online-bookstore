import request from './request'

/** 订单管理接口 */

export function pageOrders(params) {
  return request.get('/api/admin/order/page', { params })
}

export function getOrder(id) {
  return request.get('/api/admin/order/' + id)
}

export function shipOrder(id) {
  return request.post('/api/admin/order/' + id + '/ship')
}

/**
 * 手动触发「超时未支付订单自动取消」，与定时任务走同一个服务方法。
 * timeoutMinutes 传 0 表示把所有未支付订单都视为超时（答辩演示用，不必真等 30 分钟）。
 * @returns 本次实际取消的订单数
 */
export function cancelTimeoutOrders(timeoutMinutes) {
  return request.post('/api/admin/order/cancel-timeout', null, { params: { timeoutMinutes } })
}
