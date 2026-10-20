import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 管理端新增页面的接口契约（W3：轮播图 / 书单治理 / 系统配置 / 到货提醒）。
 *
 * 为什么单独给 api 层写用例：本批几乎全是纯前端改动，页面能不能跑起来
 * 由「路径写错、方法写错、参数形态写错」这三类问题决定，而它们
 * **构建期一律不报错**（vite build 只检查能不能打包）。把路径固化在用例里，
 * 后端一改路由就会在这里立刻亮红灯。
 *
 * 参数形态也要一起断言：`updateBannerStatus` 的 status 走 query 而不是 body，
 * `updateConfig` 的值包在 { configValue } 里 —— 这两处都是「看着能跑、实际 400」的典型。
 */
const mock = vi.hoisted(() => {
  const calls = []
  const record = (method) => (...args) => {
    calls.push({ method, args })
    return Promise.resolve(null)
  }
  return {
    calls,
    request: {
      get: record('get'),
      post: record('post'),
      put: record('put'),
      delete: record('delete')
    }
  }
})

vi.mock('../request', () => ({ default: mock.request }))

import { listBanners, createBanner, updateBanner, updateBannerStatus, deleteBanner } from '../banner'
import { pageBooklists, getBooklist, updateBooklistStatus, deleteBooklist } from '../booklist'
import { listConfigs, updateConfig } from '../config'
import { pageStockNoticeSummary, markStockNoticeNotified } from '../stock-notice'
import { pageOrders, batchShipOrders, exportOrders } from '../order'
import { bookSummary } from '../book'
import { userSummary } from '../user'
import { reviewSummary } from '../review'
import { overview } from '../admin'

/** 取最后一次调用的 { method, args } */
function lastCall() {
  return mock.calls[mock.calls.length - 1]
}

describe('api/banner —— 轮播图管理', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('列表：GET /api/admin/banner/list（含已下架，后台要看全量）', async () => {
    await listBanners()
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/banner/list'] })
  })

  it('新增：POST /api/admin/banner，整条记录作为请求体', async () => {
    const payload = { title: '新书上架', bgColor: '#fff', sort: 1, status: 1 }
    await createBanner(payload)
    expect(lastCall()).toEqual({ method: 'post', args: ['/api/admin/banner', payload] })
  })

  it('编辑：PUT /api/admin/banner/{id}', async () => {
    const payload = { title: '改名' }
    await updateBanner(3, payload)
    expect(lastCall()).toEqual({ method: 'put', args: ['/api/admin/banner/3', payload] })
  })

  it('上下架：PUT /api/admin/banner/{id}/status，status 走 query 而非 body', async () => {
    await updateBannerStatus(3, 0)
    expect(lastCall()).toEqual({
      method: 'put',
      args: ['/api/admin/banner/3/status', null, { params: { status: 0 } }]
    })
  })

  it('删除：DELETE /api/admin/banner/{id}', async () => {
    await deleteBanner(3)
    expect(lastCall()).toEqual({ method: 'delete', args: ['/api/admin/banner/3'] })
  })
})

describe('api/booklist —— 书单治理', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('分页：GET /api/admin/booklist/page，筛选条件走 params', async () => {
    const params = { keyword: '算法', status: 0, isPublic: 1, pageNum: 1, pageSize: 10 }
    await pageBooklists(params)
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/booklist/page', { params }] })
  })

  it('详情：GET /api/admin/booklist/{id}（与用户端 /detail/{id} 路径不同，勿混用）', async () => {
    await getBooklist(5)
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/booklist/5'] })
  })

  it('隐藏 / 恢复：PUT /api/admin/booklist/{id}/status，status 走 query', async () => {
    await updateBooklistStatus(5, 1)
    expect(lastCall()).toEqual({
      method: 'put',
      args: ['/api/admin/booklist/5/status', null, { params: { status: 1 } }]
    })
  })

  it('删除：DELETE /api/admin/booklist/{id}', async () => {
    await deleteBooklist(5)
    expect(lastCall()).toEqual({ method: 'delete', args: ['/api/admin/booklist/5'] })
  })
})

describe('api/config —— 系统配置（运费规则）', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('列表：GET /api/admin/config/list', async () => {
    await listConfigs()
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/config/list'] })
  })

  it('改值：PUT /api/admin/config/{key}，值包在 { configValue } 里', async () => {
    await updateConfig('freight.free-threshold', '59.00')
    expect(lastCall()).toEqual({
      method: 'put',
      args: ['/api/admin/config/freight.free-threshold', { configValue: '59.00' }]
    })
  })
})

describe('api/stock-notice —— 到货提醒看板', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('汇总：GET /api/admin/stock-notice/summary，onlyWaiting 走 params', async () => {
    const params = { keyword: '算法', onlyWaiting: true, pageNum: 1, pageSize: 10 }
    await pageStockNoticeSummary(params)
    expect(lastCall()).toEqual({
      method: 'get',
      args: ['/api/admin/stock-notice/summary', { params }]
    })
  })

  it('标记已通知：PUT /api/admin/stock-notice/{bookId}/notify（幂等）', async () => {
    await markStockNoticeNotified(12)
    expect(lastCall()).toEqual({ method: 'put', args: ['/api/admin/stock-notice/12/notify'] })
  })
})

describe('api/order —— 订单筛选参数形态', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('分页：日期区间走 createTimeStart / createTimeEnd（后端按 LocalDate 接收）', async () => {
    const params = {
      orderNo: '202610',
      status: 2,
      pageNum: 1,
      pageSize: 10,
      createTimeStart: '2026-09-20',
      createTimeEnd: '2026-10-20'
    }
    await pageOrders(params)
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/order/page', { params }] })
  })

  it('清空筛选时不传空字符串：undefined 的键不会出现在 query 里', async () => {
    const params = {
      orderNo: undefined,
      status: undefined,
      pageNum: 1,
      pageSize: 10,
      createTimeStart: undefined,
      createTimeEnd: undefined
    }
    await pageOrders(params)
    const sent = lastCall().args[1].params
    expect(Object.keys(sent).filter((k) => sent[k] === undefined)).toEqual([
      'orderNo',
      'status',
      'createTimeStart',
      'createTimeEnd'
    ])
  })
})

describe('api/admin —— 工作台状态分布字段', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('概览：GET /api/admin/dashboard（statusCounts 由后端算好，前端不再二次聚合）', async () => {
    await overview()
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/dashboard'] })
  })
})

describe('api/order —— 批量发货与导出', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('批量发货：POST /api/admin/order/batch-ship，ids 放 body 而不是 query', async () => {
    await batchShipOrders([1, 2, 3])
    expect(lastCall()).toEqual({
      method: 'post',
      args: ['/api/admin/order/batch-ship', { ids: [1, 2, 3] }]
    })
  })

  it('导出：GET /api/admin/order/export 且必须带 responseType=blob', async () => {
    const params = { status: 2, createTimeStart: '2026-10-01' }
    await exportOrders(params)
    expect(lastCall()).toEqual({
      method: 'get',
      args: ['/api/admin/order/export', { params, responseType: 'blob' }]
    })
  })
})

describe('api/列表页汇总 —— 四个列表页的汇总条数据源', () => {
  beforeEach(() => {
    mock.calls.length = 0
  })

  it('图书：GET /api/admin/book/summary', async () => {
    await bookSummary()
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/book/summary'] })
  })

  it('用户：GET /api/admin/user/summary', async () => {
    await userSummary()
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/user/summary'] })
  })

  it('评价：GET /api/admin/review/summary', async () => {
    await reviewSummary()
    expect(lastCall()).toEqual({ method: 'get', args: ['/api/admin/review/summary'] })
  })
})
