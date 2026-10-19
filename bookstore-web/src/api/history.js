import request from './request'

/**
 * 浏览历史接口（均需登录）。
 *
 * <p>历史是"私有数据"，没有匿名可读的版本：未登录期间产生的浏览记录先落在
 * localStorage，登录后由 {@link mergeHistory} 一次性合并上来。
 */

/** 记录一次浏览 -> {recorded: true} */
export function recordHistory(bookId) {
  return request.post('/api/history', { bookId })
}

/** 我的浏览历史分页 -> PageVO<HistoryItemVO> */
export function pageHistory(params) {
  return request.get('/api/history/page', { params })
}

/**
 * 合并未登录期间的本地记录。
 *
 * @param {number[]} bookIds 按浏览时间倒序（第 0 条最新）
 * @returns {Promise<{added: number, total: number}>} added 只统计真正新插入的行，
 *          不把"刷新了已有记录的时间"算进去 —— 前端直接拿它告诉用户"同步了几条"
 */
export function mergeHistory(bookIds) {
  return request.post('/api/history/merge', { bookIds })
}

/** 删除单条 */
export function removeHistory(bookId) {
  return request.delete('/api/history/' + bookId)
}

/** 清空全部 */
export function clearHistory() {
  return request.delete('/api/history')
}
