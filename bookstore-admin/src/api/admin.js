import request from './request'

/** 管理端接口统一配置（Web 后台）。 */

export function login(data) {
  return request.post('/api/admin/login', data)
}

export function getInfo() {
  return request.get('/api/admin/info')
}

export function overview() {
  return request.get('/api/admin/dashboard')
}

/** 销售趋势：单日订单数与销售额，供看板折线图使用 */
export function salesTrend(days) {
  return request.get('/api/admin/stats/sales-trend', { params: { days } })
}

/** 图书销量排行 */
export function topBooks(limit) {
  return request.get('/api/admin/stats/top-books', { params: { limit } })
}
