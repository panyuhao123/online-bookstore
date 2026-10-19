import request from './request'

/** 管理端接口统一配置（Web 后台）。 */

export function login(data) {
  return request.post('/api/admin/login', data)
}

/**
 * 退出登录。
 *
 * 服务端会把当前令牌写入吊销黑名单，使其立即失效 —— 管理端令牌能操作商品、
 * 订单与用户，只清掉浏览器里的 localStorage 不足以防止令牌被复制后继续使用。
 * 注意：请求拦截器会自动带上 Authorization 头，令牌就是从这里取出来吊销的。
 */
export function logout() {
  return request.post('/api/admin/logout', {})
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
