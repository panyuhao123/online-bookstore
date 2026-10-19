import request from './request'

/**
 * 书单接口。
 *
 * <p>路径上有个必须遵守的细节：公开读是 `/page` 与 `/detail/{id}`，而不是 `/{id}`。
 * 后端白名单按路径前缀放行，若详情写成 `/api/booklist/{id}`，Spring Security 的
 * 路径变量会连 `/api/booklist/my` 一起匹配上，结果是任何人不带令牌就能拉到别人
 * 的私密书单。前端的调用路径必须与这个设计一致，不能"顺手"改成 `/{id}`。
 */

/** 书单广场：公开书单分页；带令牌时自己的私密书单也会返回并带 mine=true */
export function pageBooklists(params) {
  return request.get('/api/booklist/page', { params })
}

/** 书单详情（含图书明细）-> BookListDetailVO */
export function getBooklist(id) {
  return request.get('/api/booklist/detail/' + id)
}

/** 我的书单（含私密）；已隐藏的也会返回并带 status=0 */
export function getMyBooklists(params) {
  return request.get('/api/booklist/my', { params })
}

/** 新建书单：{name, description?, coverUrl?, isPublic} */
export function createBooklist(data) {
  return request.post('/api/booklist', data)
}

/** 编辑书单 */
export function updateBooklist(id, data) {
  return request.put('/api/booklist/' + id, data)
}

/** 删除书单（连同条目，不影响图书本身） */
export function deleteBooklist(id) {
  return request.delete('/api/booklist/' + id)
}

/** 批量加书 -> {added, total}；added 是实际新增条数 */
export function addBooksToBooklist(id, bookIds) {
  return request.post('/api/booklist/' + id + '/books', { bookIds })
}

/** 从书单移出某本图书 */
export function removeBookFromBooklist(id, bookId) {
  return request.delete('/api/booklist/' + id + '/books/' + bookId)
}
