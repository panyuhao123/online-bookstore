import request from './request'

/** 分类列表 -> [{id, name, sort, bookCount}] */
export function getCategories() {
  return request.get('/api/category/list')
}
