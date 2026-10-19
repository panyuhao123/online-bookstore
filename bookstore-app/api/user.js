/** 用户模块接口 */
import { get, post } from '../common/request'

/** 注册：{phone, password, nickname?} -> UserVO */
export const register = (data) => post('/api/user/register', data)

/** 登录：{phone, password} -> {token, tokenType, expireHours, user} */
export const login = (data) => post('/api/user/login', data)

/** 退出登录 */
export const logout = () => post('/api/user/logout', {}, { silent: true })

/** 当前用户信息 -> UserVO */
export const getUserInfo = () => get('/api/user/info')
