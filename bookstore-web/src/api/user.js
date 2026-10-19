import request from './request'

/** 注册：{phone, password, nickname?} -> UserVO */
export function register(data) {
  return request.post('/api/user/register', data)
}

/** 登录：{phone, password} -> {token, tokenType, expireHours, user} */
export function login(data) {
  return request.post('/api/user/login', data)
}

/** 退出登录 */
export function logout() {
  return request.post('/api/user/logout', {})
}

/** 当前用户信息 -> UserVO */
export function getUserInfo() {
  return request.get('/api/user/info')
}
