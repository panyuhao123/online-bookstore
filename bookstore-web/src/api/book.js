import request from './request'

/** 图书分页：{keyword?, categoryId?, minPrice?, maxPrice?, sort?, pageNum, pageSize} */
export function pageBooks(params) {
  return request.get('/api/book/page', { params })
}

/** 图书详情 -> BookVO */
export function getBook(id) {
  return request.get('/api/book/' + id)
}

/** 热门图书榜（limit 默认 6） */
export function getHotBooks(limit = 6) {
  return request.get('/api/book/hot', { params: { limit } })
}
