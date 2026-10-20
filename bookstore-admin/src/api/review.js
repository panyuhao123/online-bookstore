import request from './request'

/** 评价管理接口 */

export function pageReviews(params) {
  return request.get('/api/admin/review/page', { params })
}

/** 显示 / 隐藏：status 1-正常 0-已隐藏（违规评价优先隐藏而非删除） */
export function updateReviewStatus(id, status) {
  return request.put('/api/admin/review/' + id + '/status', null, { params: { status } })
}

/** 彻底删除：仅用于垃圾数据清理 */
export function deleteReview(id) {
  return request.delete('/api/admin/review/' + id)
}

/** 评价汇总指标：总数 / 正常 / 隐藏 / 平均分（平均分只算正常评价） */
export function reviewSummary() {
  return request.get('/api/admin/review/summary')
}
