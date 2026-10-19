/**
 * 全局配置：后端地址（可运行时切换）与 URL 工具。
 *
 * 真机联调注意事项（uni-app 官方文档明确说明）：
 * - localhost / 127.0.0.1 在手机上指向手机自身，真机必须访问电脑的局域网 IP；
 * - 手机需与电脑连接同一 WiFi；
 * - 后端 Spring Boot 默认监听 0.0.0.0，无需额外配置。
 *
 * 电脑 IP 会随 WiFi 重连被 DHCP 重新分配，因此这里做了三层兜底：
 *   1. 上次连接成功的地址（存本地缓存，启动时优先用）
 *   2. 启动时自动探测候选地址，成功即切换
 *   3. 调试页（「我的」页连点头像 5 次进入）可手动填写并测速保存
 * 三层都失败时仍回落到下面的默认地址，保证逻辑不中断。
 */

// #ifdef H5
export const DEFAULT_BASE_URL = 'http://127.0.0.1:8080'
// #endif

// #ifndef H5
// 真机（APP）端必须填电脑的局域网 IP。
// 查询当前 IP 请取 WLAN / 以太网那一行，排除 VMware 虚拟网卡（192.168.98.x / 192.168.109.x）。
export const DEFAULT_BASE_URL = 'http://10.180.61.51:8080'
// #endif

/** 本地缓存键：避开 uni_ / dcloud_ 保留前缀 */
const URL_STORAGE_KEY = 'bookstore_base_url'

/** 运行时地址内存缓存，避免每次请求都读本地缓存 */
let runtimeUrl = ''

/** 去掉结尾多余斜杠，避免拼接出 //api */
function normalize(url) {
  return String(url || '').trim().replace(/\/+$/, '')
}

/** 获取当前生效的后端地址 */
export function getBaseUrl() {
  if (runtimeUrl) return runtimeUrl
  let saved = ''
  try {
    saved = uni.getStorageSync(URL_STORAGE_KEY) || ''
  } catch (e) {
    saved = ''
  }
  runtimeUrl = normalize(saved) || DEFAULT_BASE_URL
  return runtimeUrl
}

/** 设置并持久化后端地址（手动填写、自动探测命中都会走这里） */
export function setBaseUrl(url) {
  const next = normalize(url)
  if (!next) return false
  runtimeUrl = next
  try {
    uni.setStorageSync(URL_STORAGE_KEY, next)
  } catch (e) {}
  return true
}

/** 恢复为打包时写入的默认地址 */
export function resetBaseUrl() {
  runtimeUrl = DEFAULT_BASE_URL
  try {
    uni.removeStorageSync(URL_STORAGE_KEY)
  } catch (e) {}
}

/** 候选地址列表：上次成功的优先，其次是打包默认值 */
export function candidateUrls() {
  let saved = ''
  try {
    saved = uni.getStorageSync(URL_STORAGE_KEY) || ''
  } catch (e) {
    saved = ''
  }
  saved = normalize(saved)
  const list = []
  if (saved) list.push(saved)
  if (!list.includes(DEFAULT_BASE_URL)) list.push(DEFAULT_BASE_URL)
  return list
}

/**
 * 探测某个地址是否可用（请求后端 /health）。
 * 只关心成败，不抛异常，便于并发探测。
 */
export function probe(url, timeout = 2500) {
  return new Promise((resolve) => {
    uni.request({
      url: normalize(url) + '/health',
      method: 'GET',
      timeout,
      success: (res) => {
        resolve(
          res.statusCode === 200 &&
            !!res.data &&
            res.data.code === 200
        )
      },
      fail: () => resolve(false)
    })
  })
}

/**
 * 并发探测候选地址，返回第一个可用地址；全部不可用返回 null。
 * APP 启动时调用，解决电脑 IP 变化导致的「网络异常」。
 */
export async function autoDetectBaseUrl() {
  const list = candidateUrls()
  const results = await Promise.all(list.map(async (u) => ({ url: u, ok: await probe(u) })))
  const hit = results.find((r) => r.ok)
  if (hit) {
    setBaseUrl(hit.url)
    return hit.url
  }
  return null
}

/** 相对路径转完整 URL（后端返回的封面是 /files/... 相对路径） */
export function fullUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  return getBaseUrl() + path
}

/** 封面地址：为空时回退到后端兜底封面 */
export function coverSrc(path) {
  return fullUrl(path || '/files/cover/default.jpg')
}
