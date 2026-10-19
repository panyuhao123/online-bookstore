/** 购物车模块接口（需登录） */
import { get, post, put, del } from '../common/request'

/** 加入购物车：{bookId, quantity}；同书重复加入会累加数量 */
export const addToCart = (data) => post('/api/cart/add', data)

/** 购物车列表 -> {list, checkedAmount, checkedCount} */
export const getCartList = () => get('/api/cart/list')

/** 修改数量/勾选：{quantity?, checked?} */
export const updateCart = (cartId, data) => put('/api/cart/' + cartId, data)

/** 删除单条 */
export const removeCart = (cartId) => del('/api/cart/' + cartId)

/** 批量删除：body 为 cartId 数组 */
export const removeCartBatch = (cartIds) => post('/api/cart/removeBatch', cartIds)

/**
 * 合并未登录期间的本地购物车：入参 [{bookId, quantity}]
 * 返回 {mergedCount, totalQuantity, skipped:[{bookId, title, reason}]}
 */
export const mergeCart = (items) => post('/api/cart/merge', { items })
