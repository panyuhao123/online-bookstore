import { beforeEach, describe, expect, it } from 'vitest'
import { setBaseUrl } from '@/utils/auth'
import { fileUrl } from '@/utils/url'

/**
 * 封面地址拼接。
 *
 * 规则看似简单，但两种情况都必须正确，否则后台列表会整片显示裂图：
 * - 后端上传接口返回的**相对路径**（/files/cover/xxx.jpg）要拼上后端地址；
 * - 已经带 http(s):// 的**完整地址**（历史数据 / 外部图床）必须原样返回，
 *   再拼一次就会变成 http://hosthttp://host/... 。
 */
describe('utils/url —— fileUrl 封面地址拼接', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('空值返回空串（前端据此显示占位图，而不是拼出无意义的地址）', () => {
    expect(fileUrl('')).toBe('')
    expect(fileUrl(null)).toBe('')
    expect(fileUrl(undefined)).toBe('')
  })

  it('以 / 开头的相对路径拼上默认后端地址', () => {
    expect(fileUrl('/files/cover/1.jpg')).toBe('http://127.0.0.1:8080/files/cover/1.jpg')
  })

  it('缺少前导 / 的相对路径也能正确拼接', () => {
    expect(fileUrl('files/cover/1.jpg')).toBe('http://127.0.0.1:8080/files/cover/1.jpg')
  })

  it('已是完整地址时原样返回，不重复拼接', () => {
    expect(fileUrl('http://cdn.example.com/a.jpg')).toBe('http://cdn.example.com/a.jpg')
    expect(fileUrl('https://cdn.example.com/a.jpg')).toBe('https://cdn.example.com/a.jpg')
  })

  it('切换后端地址后，相对路径按新地址拼接', () => {
    setBaseUrl('http://192.168.1.20:8080')

    expect(fileUrl('/files/cover/1.jpg')).toBe('http://192.168.1.20:8080/files/cover/1.jpg')
  })
})
