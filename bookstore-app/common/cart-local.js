/**
 * 本地购物车（未登录时使用）。
 *
 * 背景：原实现里「加入购物车」必须登录，用户在浏览阶段被硬生生打断。
 * 现在未登录也能加购，数据先存在手机本地，登录/注册成功后自动合并到服务端账号 ——
 * 即「先逛后登录，购物车不丢」。
 *
 * 数据存放在 uni storage 的 bookstore_local_cart 键下，结构尽量与后端 CartVO 对齐
 * （bookId / title / coverUrl / price / stock / quantity / checked），
 * 这样购物车页面可以用同一套模板渲染本地与服务端两种数据。
 */
import { mergeCart } from '@/api/cart'

const CART_KEY = 'bookstore_local_cart'

/** 单品数量上限，与后端购物车规则保持一致 */
const MAX_QUANTITY = 99

/** 读取本地购物车（永远是数组，避免页面到处判空） */
export function getLocalCart() {
  const raw = uni.getStorageSync(CART_KEY)
  return Array.isArray(raw) ? raw : []
}

function saveLocalCart(list) {
  uni.setStorageSync(CART_KEY, list)
}

/** 本地购物车总件数 */
export function localCartCount() {
  return getLocalCart().reduce((sum, item) => sum + (item.quantity || 0), 0)
}

/**
 * 加入本地购物车：同一本书累加数量，数量同时受库存与单品上限约束。
 *
 * @param {object} book 图书详情（来自图书接口）
 * @param {number} quantity 数量，默认 1
 * @returns {number} 加入后的总件数
 */
export function addLocalCart(book, quantity = 1) {
  const list = getLocalCart()
  const limit = Math.min(book.stock || MAX_QUANTITY, MAX_QUANTITY)
  const found = list.find((item) => item.bookId === book.id)

  if (found) {
    found.quantity = Math.min(found.quantity + quantity, Math.max(limit, 1))
    // 顺手刷新书名/价格/库存等快照，避免本地数据长期停留在旧值
    found.title = book.title
    found.coverUrl = book.coverUrl
    found.price = book.price
    found.stock = book.stock
  } else {
    list.unshift({
      bookId: book.id,
      title: book.title,
      coverUrl: book.coverUrl,
      price: book.price,
      stock: book.stock,
      quantity: Math.min(quantity, Math.max(limit, 1)),
      checked: 1
    })
  }
  saveLocalCart(list)
  return localCartCount()
}

/** 修改数量 */
export function updateLocalQuantity(bookId, quantity) {
  const list = getLocalCart()
  const item = list.find((i) => i.bookId === bookId)
  if (item) {
    item.quantity = quantity
    saveLocalCart(list)
  }
}

/** 切换勾选 */
export function toggleLocalChecked(bookId) {
  const list = getLocalCart()
  const item = list.find((i) => i.bookId === bookId)
  if (item) {
    item.checked = item.checked === 1 ? 0 : 1
    saveLocalCart(list)
  }
}

/** 删除单条 */
export function removeLocalCart(bookId) {
  saveLocalCart(getLocalCart().filter((item) => item.bookId !== bookId))
}

/** 清空本地购物车 */
function clearLocalCart() {
  uni.removeStorageSync(CART_KEY)
}

/** 转成合并接口需要的入参格式 */
function toMergeItems() {
  return getLocalCart().map((item) => ({ bookId: item.bookId, quantity: item.quantity }))
}

/**
 * 登录成功后把本地购物车合并到服务端账号。
 *
 * 容错设计：合并失败（网络异常等）时保留本地数据不清空，下次登录会再试一次，
 * 且绝不因为合并失败而影响登录本身的结果。
 *
 * @returns {Promise<object|null>} 合并结果；本地无数据或合并失败返回 null
 */
export async function mergeLocalCartAfterLogin() {
  const items = toMergeItems()
  if (!items.length) return null
  try {
    const result = await mergeCart(items)
    clearLocalCart()
    return result
  } catch (e) {
    return null
  }
}
