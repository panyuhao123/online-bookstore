/**
 * 图书评价接口。
 *
 * 权限分两档（与后端 ReviewController 注释一致）：
 *   - 免登录：某本书的评价列表、评分汇总 —— 看评价是浏览行为，要求先登录会劝退新用户
 *   - 需登录：发表评价、我的评价、订单可评价项 —— 都涉及个人数据
 */
import { get, post } from '../common/request'

/** 某本书的评价分页：{pageNum, pageSize} -> PageVO<ReviewVO>（按时间倒序） */
export const pageBookReviews = (bookId, params, opts = {}) =>
  get('/api/review/book/' + bookId, params, opts)

/**
 * 某本书的评分汇总 -> ReviewSummaryVO：
 * {avgRating, reviewCount, distribution:[{rating,count}]}
 *
 * 无评价时 avgRating 为 null（不是 0），前端要显示「暂无评分」。
 */
export const getReviewSummary = (bookId, opts = {}) =>
  get('/api/review/book/' + bookId + '/summary', null, opts)

/**
 * 发表评价：{orderId, bookId, rating, content?, anonymous?}
 *
 * 评价资格由服务端从订单反查（订单归属 + 已完成 + 含该书 + 未评价过），
 * 客户端只负责收集内容，不做资格判断。
 */
export const createReview = (data) => post('/api/review', data)

/** 我的评价分页 -> PageVO<ReviewVO>（带 bookTitle / bookCoverUrl，可直接跳图书详情） */
export const pageMyReviews = (params) => get('/api/review/my', params)

/** 某笔订单里各商品的评价状态 -> ReviewableItemVO[]（含 reviewed / reviewId / rating） */
export const getReviewableItems = (orderId) => get('/api/review/order/' + orderId + '/items')
