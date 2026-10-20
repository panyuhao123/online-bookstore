import request from './request'

/**
 * 系统配置接口（当前主要是运费规则）。
 *
 * 只有「列出」与「改值」：配置键由迁移脚本与代码共同定义，后台不能新增或删除 ——
 * 新增一个没人读的键只是脏数据，删掉一个正在被读的键则会让业务静默回落到默认值。
 */

export function listConfigs() {
  return request.get('/api/admin/config/list')
}

/** 键不存在返回 404、只读键返回 422；前端不做「不存在就创建」的兜底 */
export function updateConfig(configKey, configValue) {
  return request.put('/api/admin/config/' + configKey, { configValue })
}
