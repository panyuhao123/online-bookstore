/**
 * 网络请求封装（基于 uni.request）。
 *
 * 与后端的统一约定（见后端 GlobalExceptionHandler / AuthInterceptor）：
 * - HTTP 状态码恒为 200，业务成败只看 body.code：
 *   200 成功 / 400 参数 / 401 未登录或过期 / 403 无权限 / 409 冲突 / 422 业务失败 / 500 异常
 * - 鉴权头：Authorization: Bearer <token>
 * - code === 401 时：清空本地登录态并跳转登录页
 *
 * 用法：
 *   import { get, post } from '@/common/request'
 *   const data = await get('/api/book/page', { pageNum: 1 })   // data 即 body.data
 */
import { getBaseUrl } from './config'
import { getToken, clearAuth } from './auth'
import { saveRedirect } from './guard'

/** 防止并发 401 时重复跳转/弹提示 */
let jumpingToLogin = false

function toLoginPage() {
  if (jumpingToLogin) return
  jumpingToLogin = true
  clearAuth()
  uni.showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => {
    jumpingToLogin = false
    // 记下来源页：H5 支持 URL 直达，登录后要回到用户原本想看的那一页
    saveRedirect()
    uni.reLaunch({ url: '/pages/login/login' })
  }, 600)
}

/**
 * @param {Object} options
 * @param {String} options.url    接口路径（不含 BASE_URL）
 * @param {String} [options.method]  GET/POST/PUT/DELETE
 * @param {Object} [options.data]    请求参数（GET 自动拼 query，POST 走 JSON）
 * @param {Boolean} [options.silent] true 时不弹错误提示，由调用方自行处理
 * @returns {Promise<any>} resolve body.data
 */
export function request(options) {
  const { url, method = 'GET', data, silent = false } = options
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.request({
      // 每次请求实时取地址：启动探测或手动修改后可立即生效
      url: getBaseUrl() + url,
      method,
      data,
      timeout: 15000,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {})
      },
      success: (res) => {
        const body = res.data
        // 非 200 响应或非统一结构：按服务异常处理
        if (res.statusCode !== 200 || !body || typeof body.code === 'undefined') {
          if (!silent) uni.showToast({ title: '服务异常，请稍后重试', icon: 'none' })
          return reject(new Error('bad response'))
        }
        if (body.code === 200) {
          return resolve(body.data)
        }
        if (body.code === 401) {
          toLoginPage()
          return reject(new Error(body.message || '未登录'))
        }
        if (!silent) {
          uni.showToast({ title: body.message || '操作失败', icon: 'none' })
        }
        // 带上业务码：页面需要区分「无权访问」与「加载失败」（如越权打开他人订单）
        const err = new Error(body.message || 'business error')
        err.code = body.code
        reject(err)
      },
      fail: (err) => {
        // 真机最常见的两类原因：电脑 IP 变了（WiFi 重连后 DHCP 重新分配）、后端没启动
        // 排查信息只进控制台，Toast 面向用户，不出现「后端」「IP」这类字眼
        console.error('[request] 请求失败', getBaseUrl() + url, err)
        if (!silent) {
          uni.showToast({
            title: '网络连接失败，请检查网络后重试',
            icon: 'none',
            duration: 3000
          })
        }
        reject(err)
      }
    })
  })
}

export const get = (url, data, opts = {}) => request({ url, method: 'GET', data, ...opts })
export const post = (url, data, opts = {}) => request({ url, method: 'POST', data, ...opts })
export const put = (url, data, opts = {}) => request({ url, method: 'PUT', data, ...opts })
export const del = (url, data, opts = {}) => request({ url, method: 'DELETE', data, ...opts })
