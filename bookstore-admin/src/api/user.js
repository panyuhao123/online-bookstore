import request from './request'

/** 用户管理接口 */

export function pageUsers(params) {
  return request.get('/api/admin/user/page', { params })
}

/** 启停：status 1-启用 0-禁用 */
export function updateUserStatus(id, status) {
  return request.put('/api/admin/user/' + id + '/status', null, { params: { status } })
}

/** 用户汇总指标：总数 / 正常 / 禁用 / 本月新增 */
export function userSummary() {
  return request.get('/api/admin/user/summary')
}
