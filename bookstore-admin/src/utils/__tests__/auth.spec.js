import { beforeEach, describe, expect, it } from 'vitest'
import {
  DEFAULT_BASE_URL,
  clearToken,
  getAdmin,
  getBaseUrl,
  getToken,
  isLogin,
  setAdmin,
  setBaseUrl,
  setToken
} from '@/utils/auth'

/**
 * 管理端登录态与后端地址存取。
 *
 * 这些函数的共同点是「读写 localStorage」，看起来无需测试，但有两处真实踩过的坑：
 * 1. 后端地址允许手改（换机器联调），一旦取不到就必须回落到默认值，
 *    否则 axios 的 baseURL 会变成 undefined，所有请求打到错误的地方；
 * 2. admin 信息曾写入过非法 JSON，读取时若直接 JSON.parse 会抛异常导致整个后台白屏 ——
 *    因此「脏数据要能被容错并清理」是一条必须守住的约定。
 */
describe('utils/auth —— 登录态与后端地址存取', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('未登录时 token 为空串、isLogin 为 false', () => {
    expect(getToken()).toBe('')
    expect(isLogin()).toBe(false)
  })

  it('写入令牌后可读回，isLogin 转为 true', () => {
    setToken('jwt-token-abc')

    expect(getToken()).toBe('jwt-token-abc')
    expect(isLogin()).toBe(true)
  })

  it('清除登录态时同时清掉令牌与管理员信息（不能只清一半）', () => {
    setToken('t')
    setAdmin({ id: 1, username: 'admin' })

    clearToken()

    expect(getToken()).toBe('')
    expect(getAdmin()).toBeNull()
    expect(isLogin()).toBe(false)
  })

  it('管理员信息按 JSON 往返', () => {
    setAdmin({ id: 1, username: 'admin', nickname: '超级管理员' })

    expect(getAdmin()).toEqual({ id: 1, username: 'admin', nickname: '超级管理员' })
  })

  it('管理员信息为脏数据时返回 null 并顺手清理，避免后台白屏', () => {
    localStorage.setItem('bookstore_admin_info', 'undefined')

    expect(getAdmin()).toBeNull()
    expect(localStorage.getItem('bookstore_admin_info')).toBeNull()
  })

  it('后端地址未配置时回落到默认值', () => {
    expect(getBaseUrl()).toBe(DEFAULT_BASE_URL)
  })

  it('后端地址可覆写并在下次读取时生效', () => {
    setBaseUrl('http://192.168.1.20:8080')

    expect(getBaseUrl()).toBe('http://192.168.1.20:8080')
  })
})
