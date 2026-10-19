import { beforeEach, describe, expect, it, vi } from 'vitest'

// element-plus 的 ElMessage 会尝试挂载 DOM，测试里只需断言「有没有弹、弹了什么」
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() }
}))

import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { getToken, setToken } from '@/utils/auth'

/**
 * axios 封装的响应拦截器 —— 管理端与后端契约的唯一边界。
 *
 * 被测的不是 axios 本身，而是本项目在拦截器里做的那几件事：
 * 1. HTTP 恒 200，业务成败只看 body.code —— 因此 code=200 必须**剥离一层**只把 data 交给页面；
 * 2. code=401 必须清登录态（否则会带着废令牌反复请求），并给出可读提示；
 * 3. 非 200 的业务码要弹提示**并且**把错误抛出去，让调用方能选择「不继续处理」；
 * 4. 网络层异常（后端没起、超时、HTTP 非 2xx）要有各自可区分的文案 ——
 *    这一条直接决定排查效率：曾经把「后端没启动」显示成泛泛的「请求失败」，白白多查一轮。
 */
describe('api/request —— 响应拦截器与鉴权头', () => {
  beforeEach(() => {
    localStorage.clear()
    // hash 置为登录页，使 401 分支内部的「跳回登录页」不触发（jsdom 不实现页面导航）
    window.location.hash = '#/login'
  })

  /** 用一个假 adapter 替代真实网络：直接返回给定的响应体，走完整个拦截器链路 */
  function stubBody(body, status = 200) {
    request.defaults.adapter = async (config) => ({
      data: body, status, statusText: 'OK', headers: {}, config
    })
  }

  /** 模拟网络层异常：抛出带 response 或 code 的错误对象 */
  function stubThrown(error) {
    request.defaults.adapter = async () => {
      throw error
    }
  }

  it('code=200 时剥离信封，只把 data 交给调用方', async () => {
    stubBody({ code: 200, message: '操作成功', data: { list: [1, 2], total: 2 } })

    await expect(request.get('/book/page')).resolves.toEqual({ list: [1, 2], total: 2 })
  })

  it('code=200 且 data 为空时返回 null，不报错', async () => {
    stubBody({ code: 200, message: '操作成功', data: null })

    await expect(request.post('/admin/book/delete')).resolves.toBeNull()
  })

  it('已登录时自动附带 Bearer 令牌', async () => {
    setToken('jwt-token-abc')
    let authorization = null
    request.defaults.adapter = async (config) => {
      authorization = config.headers.Authorization ?? config.headers.get?.('Authorization')
      return { data: { code: 200, data: null }, status: 200, statusText: 'OK', headers: {}, config }
    }

    await request.get('/admin/me')

    expect(authorization).toBe('Bearer jwt-token-abc')
  })

  it('未登录时不写 Authorization 头', async () => {
    let headers = null
    request.defaults.adapter = async (config) => {
      headers = config.headers
      return { data: { code: 200, data: null }, status: 200, statusText: 'OK', headers: {}, config }
    }

    await request.get('/book/page')

    const authorization = headers.Authorization ?? headers.get?.('Authorization')
    expect(authorization).toBeFalsy()
  })

  it('业务码非 200：弹提示并把错误抛给调用方', async () => {
    stubBody({ code: 422, message: '库存不足' })

    await expect(request.put('/admin/book/1')).rejects.toThrow('库存不足')
    expect(ElMessage.error).toHaveBeenCalledWith('库存不足')
  })

  it('业务码 401：清理本地登录态 + 提示 + 抛错（避免带着废令牌反复请求）', async () => {
    setToken('expired-token')
    stubBody({ code: 401, message: '登录已过期，请重新登录' })

    await expect(request.get('/admin/me')).rejects.toThrow('登录已过期，请重新登录')

    expect(getToken()).toBe('')
    expect(ElMessage.error).toHaveBeenCalledWith('登录已过期，请重新登录')
  })

  it('响应体缺少 message 时使用兜底文案', async () => {
    stubBody({ code: 500 })

    await expect(request.get('/x')).rejects.toThrow('请求失败，请稍后重试')
  })

  it('网络异常（后端未启动）：文案直指后端服务，便于排查', async () => {
    stubThrown(new Error('Network Error'))

    await expect(request.get('/x')).rejects.toThrow('Network Error')
    expect(ElMessage.error).toHaveBeenCalledWith('网络异常，请检查后端服务是否已启动')
  })

  it('HTTP 非 2xx：文案带上真实状态码', async () => {
    stubThrown(Object.assign(new Error('boom'), { response: { status: 500 } }))

    await expect(request.get('/x')).rejects.toThrow('boom')
    expect(ElMessage.error).toHaveBeenCalledWith('请求失败（HTTP 500）')
  })

  it('请求超时：文案与网络异常区分开', async () => {
    stubThrown(Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }))

    await expect(request.get('/x')).rejects.toThrow('timeout')
    expect(ElMessage.error).toHaveBeenCalledWith('请求超时，请稍后重试')
  })
})
