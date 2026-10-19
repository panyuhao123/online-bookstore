import request from './request'

/** 图书管理接口 */

export function pageBooks(params) {
  return request.get('/api/admin/book/page', { params })
}

export function getBook(id) {
  return request.get('/api/admin/book/' + id)
}

export function createBook(data) {
  return request.post('/api/admin/book', data)
}

export function updateBook(id, data) {
  return request.put('/api/admin/book/' + id, data)
}

/** 上下架：status 1-上架 0-下架 */
export function updateBookStatus(id, status) {
  return request.put('/api/admin/book/' + id + '/status', null, { params: { status } })
}

export function deleteBook(id) {
  return request.delete('/api/admin/book/' + id)
}

/** 封面上传：返回可访问的封面 URL */
export function uploadCover(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/api/admin/upload', form)
}
