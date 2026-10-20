import request from './request'

/**
 * 书单治理接口（N10）。
 *
 * 只有「查 / 改状态 / 删」，没有「增 / 改内容」：书单是用户的东西，
 * 管理员不是代运营，平台需要的只是能看到全部并处置违规内容。
 */

export function pageBooklists(params) {
  return request.get('/api/admin/booklist/page', { params })
}

/** 详情，含条目；已下架图书照常返回，便于判断内容是否违规 */
export function getBooklist(id) {
  return request.get('/api/admin/booklist/' + id)
}

/** status：1-正常 0-已隐藏 */
export function updateBooklistStatus(id, status) {
  return request.put('/api/admin/booklist/' + id + '/status', null, { params: { status } })
}

export function deleteBooklist(id) {
  return request.delete('/api/admin/booklist/' + id)
}
