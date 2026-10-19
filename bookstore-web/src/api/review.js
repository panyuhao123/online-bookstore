import request from './request'

/** 某本书的评价分页：{pageNum, pageSize} -> PageVO<ReviewVO> */
export function pageBookReviews(bookId, params) {
  return request.get('/api/review/book/' + bookId, { params })
}

/** 某本书的评分汇总 -> {avgRating, reviewCount, distribution:[{rating,count}]} */
export function getReviewSummary(bookId) {
  return request.get('/api/review/book/' + bookId + '/summary')
}

/** 发表评价：{orderId, bookId, rating, content?, anonymous?} */
export function createReview(data) {
  return request.post('/api/review', data)
}

/** 我的评价分页 -> PageVO<ReviewVO> */
export function pageMyReviews(params) {
  return request.get('/api/review/my', { params })
}

/** 某笔订单各商品的评价状态 -> ReviewableItemVO[] */
export function getReviewableItems(orderId) {
  return request.get('/api/review/order/' + orderId + '/items')
}
