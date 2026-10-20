import request from './request'

/**
 * 首页轮播图管理接口（N9）。
 *
 * 上架 / 下架单独一个接口而不是走「整条编辑」：下架是运营最高频的动作，
 * 让前端把整条记录回传一次，很容易顺带把标题、图片改坏。
 */

export function listBanners() {
  return request.get('/api/admin/banner/list')
}

export function createBanner(data) {
  return request.post('/api/admin/banner', data)
}

export function updateBanner(id, data) {
  return request.put('/api/admin/banner/' + id, data)
}

/** status：1-上架 0-下架 */
export function updateBannerStatus(id, status) {
  return request.put('/api/admin/banner/' + id + '/status', null, { params: { status } })
}

export function deleteBanner(id) {
  return request.delete('/api/admin/banner/' + id)
}
