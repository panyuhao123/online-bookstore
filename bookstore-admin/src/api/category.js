import request from './request'

/** 分类管理接口 */

export function listCategories() {
  return request.get('/api/admin/category/list')
}

export function createCategory(data) {
  return request.post('/api/admin/category', data)
}

export function updateCategory(id, data) {
  return request.put('/api/admin/category/' + id, data)
}

export function deleteCategory(id) {
  return request.delete('/api/admin/category/' + id)
}
