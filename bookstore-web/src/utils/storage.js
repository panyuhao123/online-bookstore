/**
 * 本地存储与登录态存取。
 *
 * Token 前缀用 bookstore_web_*，与管理端(bookstore_admin_*)、APP H5(bookstore_*)严格隔离，
 * 同浏览器多端登录互不覆盖。
 */

const TOKEN_KEY = 'bookstore_web_token'
const USER_KEY = 'bookstore_web_user'
const REDIRECT_KEY = 'bookstore_web_redirect'
const HISTORY_KEY = 'bookstore_web_history'
const COMPARE_KEY = 'bookstore_web_compare'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function isLogin() {
  return !!getToken()
}

/** 登录回跳目标（存本地，不挂 URL，避免特殊字符被编码破坏） */
export function saveRedirect(path) {
  localStorage.setItem(REDIRECT_KEY, path)
}

export function takeRedirect() {
  const path = localStorage.getItem(REDIRECT_KEY) || '/'
  localStorage.removeItem(REDIRECT_KEY)
  return path
}

/** 浏览历史（localStorage 模拟，后续可对接后端） */
export function getHistory() {
  const raw = localStorage.getItem(HISTORY_KEY)
  if (!raw) return []
  try {
    return JSON.parse(raw)
  } catch (e) {
    return []
  }
}

export function setHistory(list) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list))
}

export function clearHistory() {
  localStorage.removeItem(HISTORY_KEY)
}

/** 图书对比栏（localStorage 模拟，最多 4 本） */
export function getCompareList() {
  const raw = localStorage.getItem(COMPARE_KEY)
  if (!raw) return []
  try {
    return JSON.parse(raw)
  } catch (e) {
    return []
  }
}

export function setCompareList(list) {
  localStorage.setItem(COMPARE_KEY, JSON.stringify(list))
}
