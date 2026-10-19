import request from './request'

/** 操作日志接口（只读：日志由后端 AOP 切面写入，不提供新增与修改） */

export function pageLogs(params) {
  return request.get('/api/admin/log/page', { params })
}

/** 模块列表：筛选下拉框数据源 */
export function logModules() {
  return request.get('/api/admin/log/modules')
}
