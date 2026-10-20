import request from './request'

/** 加入购物车：{bookId, quantity} */
export function addToCart(data) {
  return request.post('/api/cart/add', data)
}

/** 购物车列表 -> {list, checkedAmount, checkedCount} */
export function getCartList() {
  return request.get('/api/cart/list')
}

/** 修改数量/勾选：{quantity?, checked?} */
export function updateCart(cartId, data) {
  return request.put('/api/cart/' + cartId, data)
}

/** 删除单条 */
export function removeCart(cartId) {
  return request.delete('/api/cart/' + cartId)
}

/** 批量删除：body 为 cartId 数组 */
export function removeCartBatch(cartIds) {
  return request.post('/api/cart/removeBatch', cartIds)
}
