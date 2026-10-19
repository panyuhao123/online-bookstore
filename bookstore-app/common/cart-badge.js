/**
 * 购物车角标（全局单例）。
 *
 * 背景：底部导航改成自绘之后，购物车图标上的角标要在每个页面都保持正确，
 * 而角标的数据来源有两个：
 *   - 已登录：服务端购物车 /api/cart/list
 *   - 未登录：本机购物车（common/cart-local.js）
 * 这里把两种来源收敛成一个响应式数字 + 一个刷新函数，TabBar 只管渲染。
 *
 * 事件名统一为 bookstore_cart_changed；注意 uni.$off 必须带 handler 引用，
 * 不带回调的 $off 会把该事件名下的全部监听一起移除（历史 bug，勿回退）。
 */
import { ref } from 'vue'
import { getCartList } from '@/api/cart'
import { isLogin } from '@/common/auth'
import { localCartCount } from '@/common/cart-local'

const CART_CHANGED_EVENT = 'bookstore_cart_changed'

/** 购物车总件数（响应式，TabBar 直接渲染） */
export const cartCount = ref(0)

/** 刷新中标记：避免页面 onShow 与事件通知并发打同一个接口 */
let refreshing = false

/**
 * 重新计算角标。
 *
 * 失败时（后端未启动 / 真机连不上）静默回落到本机数量，
 * 角标不是主流程，绝不因为取不到就弹错或阻塞页面。
 */
export async function refreshCartCount() {
  if (refreshing) return cartCount.value
  refreshing = true
  try {
    if (isLogin()) {
      const data = await getCartList()
      const list = (data && data.list) || []
      cartCount.value = list.reduce((sum, i) => sum + (Number(i.quantity) || 0), 0)
    } else {
      cartCount.value = localCartCount()
    }
  } catch (e) {
    cartCount.value = localCartCount()
  } finally {
    refreshing = false
  }
  return cartCount.value
}

/** 购物车发生变更后调用：刷新数字并广播，让所有页面的 TabBar 同步 */
export function notifyCartChanged() {
  refreshCartCount()
  uni.$emit(CART_CHANGED_EVENT)
}

export function onCartChanged(handler) {
  uni.$on(CART_CHANGED_EVENT, handler)
}

export function offCartChanged(handler) {
  uni.$off(CART_CHANGED_EVENT, handler)
}
