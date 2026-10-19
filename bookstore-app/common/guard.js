/**
 * 登录守卫（H5 与 APP 通用）。
 *
 * 为什么需要它：H5 可以直接在地址栏输入 #/pages/order/detail?id=5 打开任意页面，
 * 而 APP 只能靠页面内跳转到达。未登录时两者原本都是「先渲染页面 → 接口返回 401 → 才跳登录」，
 * 在浏览器上会看到白屏闪一下再跳走的中间态。这里把判断提前到进入页面时。
 *
 * 用法（受保护页 onLoad 首行）：
 *   onLoad((options) => {
 *     if (!requireLogin()) return
 *     ...
 *   })
 */
import { isLogin } from '@/common/auth'

/**
 * 需要登录才能进入的页面（与 pages.json 的 path 一致，不带前导斜杠）。
 *
 * 未列出的页面允许匿名访问：首页/分类/书单/详情是浏览页；
 * 购物车未登录时展示「本机购物车」，是本项目的既有设计。
 */
const GUARDED = [
  'pages/order/list',
  'pages/order/detail',
  'pages/order/confirm',
  'pages/address/list',
  'pages/address/edit',
  'pages/review/edit'
]

/** 当前页面路径（含查询参数，不带前导斜杠）；APP 端无 URL 概念，返回空串 */
export function currentPath() {
  // #ifdef H5
  return String(location.hash || '').replace(/^#\/?/, '')
  // #endif
  // #ifndef H5
  return ''
  // #endif
}

/**
 * 只放行站内页面路径。
 * redirect 可能来自地址栏，不校验的话 ?redirect=http://... 会被拿去跳转。
 */
export function safeRedirect(path) {
  const p = String(path || '')
  return /^pages\/[A-Za-z0-9_\-/]+(\?[^\s]*)?$/.test(p) ? p : ''
}

/**
 * 登录后要回跳的页面，存在本地而不是挂在 URL 上。
 *
 * 试过放在 URL 里：`/pages/login/login?redirect=pages%2Forder%2Flist` 经 uni-app
 * 路由处理后 % 会被再编码一层（最终变成 %252F），取出来解码一次对不上；
 * 而带 & 的路径（如 review/edit?orderId=1&bookId=2）又会被查询串截断。
 * 存本地没有这两个问题，也不会把内部页面路径暴露在地址栏。
 */
const REDIRECT_KEY = 'bookstore_redirect'

/** 10 分钟：超过就当作过期，避免隔天登录被跳到上次没登进去的页面 */
const REDIRECT_TTL = 10 * 60 * 1000

export function saveRedirect(path) {
  const target = safeRedirect(path || currentPath())
  if (!target) return
  try {
    uni.setStorageSync(REDIRECT_KEY, JSON.stringify({ path: target, at: Date.now() }))
  } catch (e) {}
}

/** 取出待回跳的页面（不清除：登录页跳注册页后再回来还要用） */
export function takeRedirect() {
  try {
    const raw = uni.getStorageSync(REDIRECT_KEY)
    if (!raw) return ''
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!data || Date.now() - (data.at || 0) > REDIRECT_TTL) return ''
    return safeRedirect(data.path)
  } catch (e) {
    return ''
  }
}

export function clearRedirect() {
  try {
    uni.removeStorageSync(REDIRECT_KEY)
  } catch (e) {}
}

/** 已登录返回 true；未登录跳登录页并返回 false（调用方应就此 return） */
export function requireLogin() {
  if (isLogin()) return true
  saveRedirect()
  uni.reLaunch({ url: '/pages/login/login' })
  return false
}

/** 该页面是否需要登录（供调试与后续扩展使用） */
export function isGuarded(path) {
  const p = String(path || '').split('?')[0]
  return GUARDED.includes(p)
}
