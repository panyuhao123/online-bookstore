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

/**
 * 批量发货。
 *
 * 后端的语义是「逐单条件更新」：只有 status=2 的单会真的变成已发货，
 * 其余在 skipped 里带原因返回。因此这个接口不会因为混入已取消的单而整体失败。
 * @param {number[]} ids 订单 ID 列表（上限 100）
 * @returns {{shipped: number, skipped: {id: number, orderNo: string, reason: string}[]}}
 */
export function batchShipOrders(ids) {
  return request.post('/api/admin/order/batch-ship', { ids })
}

/**
 * 导出当前筛选条件下的订单 CSV。
 *
 * 与其它接口的差异：responseType 为 blob，返回的是整个 axios 响应（不是 body.data），
 * 由调用方负责落盘 —— 二进制流没有 code/data 结构，交给通用拦截器解析只会报「请求失败」。
 */
export function exportOrders(params) {
  return request.get('/api/admin/order/export', { params, responseType: 'blob' })
}
