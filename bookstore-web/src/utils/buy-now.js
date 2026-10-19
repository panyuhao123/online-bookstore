/**
 * 「立即购买」标记。
 *
 * <p>PC 端的立即购买是「先加购再跳结算页」，而后端加购默认把条目置为已勾选，
 * 结算页又是按「已勾选」取商品 —— 于是购物车里原本勾着的其它书会被一起结算，
 * 用户点的是「立即购买这一本」，付的却是一堆。
 *
 * <p>解决办法：下单前把这次要结算的 cartId 记在 sessionStorage（只影响本次会话、
 * 关掉标签页即失效），结算页优先用它；没有标记时才回落到「按勾选结算」。
 */

const KEY = 'bookstore_web_buy_now'

export function setBuyNow(cartIds) {
  const ids = (cartIds || []).filter((id) => id !== undefined && id !== null)
  if (ids.length === 0) {
    sessionStorage.removeItem(KEY)
    return
  }
  sessionStorage.setItem(KEY, JSON.stringify(ids))
}

/** 读取但不消费（结算页可能会重复渲染） */
export function peekBuyNow() {
  try {
    const raw = sessionStorage.getItem(KEY)
    const ids = raw ? JSON.parse(raw) : []
    return Array.isArray(ids) ? ids : []
  } catch (e) {
    return []
  }
}

export function clearBuyNow() {
  sessionStorage.removeItem(KEY)
}
