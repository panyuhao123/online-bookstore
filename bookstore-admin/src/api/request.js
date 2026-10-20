import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken, getBaseUrl } from '@/utils/auth'

/**
 * axios 封装。
 *
 * 与后端约定保持一致：
 * 1. HTTP 状态码恒为 200，业务成败只看响应体的 code；
 * 2. 401 表示未登录或令牌过期（含账号被后台禁用），此时清理本地令牌跳回登录页；
 * 3. 其余非 200 的业务码统一弹提示，并把错误抛给调用方决定是否继续处理。
 */
const request = axios.create({
  baseURL: getBaseUrl(),
  timeout: 20000
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    // 文件下载（responseType: 'blob'）直接放行：二进制流没有 {code, data} 结构，
    // 走下面的业务码判断会被当成「请求失败」
    if (response.config && response.config.responseType === 'blob') {
      return response
    }
    const body = response.data
    // 后端保证 HTTP 恒 200，因此这里只处理业务逻辑
    if (body && body.code === 200) {
      return body.data
    }
    const message = (body && body.message) || '请求失败，请稍后重试'
    const code = body && body.code
    if (code === 401) {
      // 未登录 / 令牌过期 / 账号被禁用：清掉登录态后统一回到登录页
      clearToken()
      ElMessage.error(message)
      redirectToLogin()
      return Promise.reject(new Error(message))
    }
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    // 网络异常（后端未启动、断网）与 HTTP 非 2xx 才会走到这里
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

/**
 * 运行时切换后端地址。
 *
 * 说明：axios 实例创建时会固化 baseURL，后台新增了「系统设置」可改后端地址，
 * 因此这里提供显式切换入口，避免必须重启 dev server 才生效。
 */
export function setRequestBaseUrl(url) {
  request.defaults.baseURL = url
}

/** 跳回登录页：整页重载可确保内存中的路由状态一并清空 */
function redirectToLogin() {
  if (!window.location.hash.startsWith('#/login')) {
    window.location.replace('#/login')
    window.location.reload()
  }
}

export default request
