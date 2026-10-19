/**
 * 登录态与后端地址存取。
 *
 * 说明：token 存在 localStorage，后台是多标签的单页应用，刷新后需要恢复登录态；
 * 服务端地址允许手动配置，与 APP 端「服务器地址」页保持同一思路 —— 换机器
 * 联调时不必改代码重新打包。
 */

const TOKEN_KEY = 'bookstore_admin_token'
const ADMIN_KEY = 'bookstore_admin_info'
const BASE_URL_KEY = 'bookstore_admin_base_url'

/** 默认后端地址：本地联调即 SpringBoot 的 8080 */
export const DEFAULT_BASE_URL = 'http://127.0.0.1:8080'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ADMIN_KEY)
}

export function getAdmin() {
  const raw = localStorage.getItem(ADMIN_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    // 历史脏数据（曾写入过 "undefined" 之类的字符串）不能让整个后台白屏
    localStorage.removeItem(ADMIN_KEY)
    return null
  }
}

export function setAdmin(admin) {
  localStorage.setItem(ADMIN_KEY, JSON.stringify(admin))
}

export function getBaseUrl() {
  return localStorage.getItem(BASE_URL_KEY) || DEFAULT_BASE_URL
}

export function setBaseUrl(url) {
  localStorage.setItem(BASE_URL_KEY, url)
}

/** 是否处于登录态：以是否持有令牌为准 */
export function isLogin() {
  return !!getToken()
}
