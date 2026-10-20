import request from './request'

/**
 * 到货提醒看板接口（N7）。
 *
 * 口径是「按图书聚合的等待人数」而不是原始订阅流水：运营打开页面要回答的是
 * 「先补哪本书」，这个问题只有聚合后才能回答。
 */

export function pageStockNoticeSummary(params) {
  return request.get('/api/admin/stock-notice/summary', { params })
}

/** 把该书的待通知订阅全部标记为已通知，返回 { notified: n }；幂等，重复点击返回 0 */
export function markStockNoticeNotified(bookId) {
  return request.put('/api/admin/stock-notice/' + bookId + '/notify')
}
