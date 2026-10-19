import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken } from '@/utils/storage'

/**
 * Axios 封装。
 *
 * 与后端约定保持一致：
 * 1. HTTP 状态码恒为 200，业务成败只看响应体的 code；
 * 2. 401 表示未登录或令牌过期，清掉本地令牌跳回登录页；
 * 3. 其余非 200 业务码弹提示，并 reject 带 code 的 Error。
 */
const request = axios.create({
  baseURL: '/',
  timeout: 20000
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 200) {
      return body.data
    }
    const message = (body && body.message) || '请求失败，请稍后重试'
    const code = body && body.code
    if (code === 401) {
      clearToken()
      ElMessage.error('登录已失效，请重新登录')
      const redirect = encodeURIComponent(window.location.hash.slice(1) || '/')
      window.location.hash = '#/login?redirect=' + redirect
      return Promise.reject(new Error(message))
    }
    ElMessage.error(message)
    const err = new Error(message)
    err.code = code
    return Promise.reject(err)
  },
  (error) => {
    let message = '网络异常，请检查后端服务是否已启动'
    if (error.response) {
      message = '请求失败（HTTP ' + error.response.status + '）'
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
