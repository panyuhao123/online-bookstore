import request from './request'

/**
 * 到货提醒接口（均需登录）。
 *
 * <p>只在「已上架且库存为 0」时可订阅；补货后由管理端批量标记已通知。
 */

/** 订阅到货提醒 -> StockNoticeVO */
export function subscribeStockNotice(bookId) {
  return request.post('/api/stock-notice', { bookId })
}

/** 我的订阅分页 -> PageVO<StockNoticeVO> */
export function pageMyStockNotices(params) {
  return request.get('/api/stock-notice/my', { params })
}

/** 取消订阅 */
export function unsubscribeStockNotice(bookId) {
  return request.delete('/api/stock-notice/' + bookId)
}
