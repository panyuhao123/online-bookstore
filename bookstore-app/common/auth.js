/**
 * 登录态管理：token 与用户信息的本地存取。
 *
 * 注意：key 避开 uni_/dcloud_ 等系统保留前缀（官方 storage 文档要求）。
 */
const TOKEN_KEY = 'bookstore_token'
const USER_KEY = 'bookstore_user'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token || '')
}

export function getUser() {
  const u = uni.getStorageSync(USER_KEY)
  return u || null
}

export function setUser(user) {
  uni.setStorageSync(USER_KEY, user || '')
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

export function isLogin() {
  return !!getToken()
}
