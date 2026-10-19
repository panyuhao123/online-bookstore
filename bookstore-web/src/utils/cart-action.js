import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { saveRedirect } from '@/utils/storage'

/**
 * 统一的加购入口。
 *
 * <p>修复的问题：加购逻辑此前在 BookCard / 图书列表表格 / 检索结果表格 / 书单详情表格 / 对比页
 * 各写一份，口径互不相同 —— 表格视图那几处**既没有登录判断也没有库存判断**，未登录点「加购」
 * 只会静默 401，售罄图书也能加进购物车（到结算才失败）。这里收敛成一处。
 */

function isOutOfStock(book) {
  return typeof book?.stock === 'number' && book.stock <= 0
}

/** 未登录时统一跳登录页并记住回跳地址 */
function requireLogin(router) {
  const userStore = useUserStore()
  if (userStore.isLogin) return true
  ElMessage.warning('请先登录后再加购')
  if (router) {
    saveRedirect(router.currentRoute.value.fullPath)
    router.push('/login')
  }
  return false
}

/**
 * 加入购物车（单本）。
 *
 * @param {object} book 图书对象（需含 id，可选 stock）
 * @param {object} [options]
 * @param {number} [options.quantity=1]
 * @param {object} [options.router] 传入后未登录会跳转登录页
 * @param {boolean} [options.silent=false] 静默模式：不弹「已加入购物车」，供批量调用
 * @returns {Promise<boolean>} 是否真正加入成功
 */
export async function addToCartGuarded(book, options = {}) {
  const { quantity = 1, router = null, silent = false } = options

  if (!book || book.id === undefined || book.id === null) return false
  if (isOutOfStock(book)) {
    if (!silent) ElMessage.warning('该图书已售罄')
    return false
  }
  if (!requireLogin(router)) return false

  const cartStore = useCartStore()
  await cartStore.add(book.id, quantity)
  if (!silent) ElMessage.success('已加入购物车')
  return true
}

/**
 * 批量加入购物车：过滤售罄 → 二次确认 → 逐本加购。
 *
 * @param {Array} books
 * @param {object} [options]
 * @param {object} [options.router]
 * @param {string} [options.confirmText] 自定义确认文案
 * @returns {Promise<boolean>} 是否执行了加购
 */
export async function addBatchToCartGuarded(books, options = {}) {
  const { router = null, confirmText } = options

  const all = books || []
  if (all.length === 0) {
    ElMessage.warning('请先选择图书')
    return false
  }
  if (!requireLogin(router)) return false

  const inStock = all.filter((b) => !isOutOfStock(b))
  if (inStock.length === 0) {
    ElMessage.warning('所选图书均已售罄')
    return false
  }

  const skipped = all.length - inStock.length
  try {
    await ElMessageBox.confirm(
      confirmText || `确认将选中的 ${inStock.length} 本图书加入购物车？`,
      '提示'
    )
  } catch (e) {
    return false /* 用户取消 */
  }

  const cartStore = useCartStore()
  for (const book of inStock) {
    await cartStore.add(book.id, 1)
  }
  ElMessage.success(skipped > 0 ? `已加入购物车（${skipped} 本已售罄已跳过）` : '已加入购物车')
  return true
}
