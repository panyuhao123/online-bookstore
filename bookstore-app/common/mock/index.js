/**
 * 模拟接口层（开发环境数据源）。
 *
 * 数据来自 tools/mock_catalog.py 定义、由 tools/build_mock_data.py 生成的脱敏真实数据集，
 * 与后端接口同构：返回值沿用 PageVO {list,total,pageNum,pageSize,pages}，
 * 因此页面从 mock 切回真实后端时，只需把 import 从 './mock' 改成 '@/api/book'。
 *
 * 与真实接口的差异（仅此两条，已在下方注释标明）：
 *   1) 带 200ms 网络延迟，用于把骨架屏与下拉刷新的交互完整演示出来；
 *   2) sort 额外支持 new / publish / discount / rating 四档演示排序，
 *      真实后端的白名单是 default / sales / price_asc / price_desc。
 */
// 显式带 .js：uni-app 的打包器不强求，但 tools/check_mock_api.mjs 用 Node 原生 ESM
// 直接 import 这一层做校验，Node 不认省略扩展名的相对路径
import { CATEGORIES } from './categories.js'
import { BOOKS } from './books.js'
import { BOOKLIST_TABS, BOOKLISTS } from './booklists.js'

/** mock 图书主键起始值：id >= 此值即为演示数据，不会与真实库 id 冲突 */
export const MOCK_ID_BASE = 10001

/** 是否为演示数据主键。加购/下单据此判断能否提交到真实后端。 */
export function isMockId(id) {
  return Number(id) >= MOCK_ID_BASE
}

/** 模拟网络往返：让骨架屏、下拉刷新等状态可被真实观察到 */
function delay(ms = 220) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 深拷贝，避免页面直接改到数据集本身（筛选/排序会污染源数据） */
function clone(v) {
  return JSON.parse(JSON.stringify(v))
}

/** 分类列表 */
export async function getCategories() {
  await delay(120)
  return clone(CATEGORIES)
}

const SORTERS = {
  // 与后端白名单一致的三档
  default: null,
  sales: (a, b) => b.sales - a.sales,
  price_asc: (a, b) => a.price - b.price || b.id - a.id,
  price_desc: (a, b) => b.price - a.price || b.id - a.id,
  // 演示页专用：新书按入库/出版时间，低价页按折扣，书单按评分
  new: (a, b) => (a.stockDate < b.stockDate ? 1 : a.stockDate > b.stockDate ? -1 : b.id - a.id),
  publish: (a, b) => (a.publishDate < b.publishDate ? 1 : a.publishDate > b.publishDate ? -1 : b.id - a.id),
  rating: (a, b) => (Number(b.avgRating) || 0) - (Number(a.avgRating) || 0) || b.reviewCount - a.reviewCount,
  discount: (a, b) => a.discount - b.discount || a.price - b.price
}

/**
 * 图书分页。
 *
 * @param {Object} params keyword / categoryId / subCategoryId / minPrice / maxPrice
 *                        / maxDiscount / isNew / pageNum / pageSize / sort
 * @returns {Promise<{list:Array,total:number,pageNum:number,pageSize:number,pages:number}>}
 */
export async function pageBooks(params) {
  await delay()
  const p = params || {}
  const pageNum = Number(p.pageNum) || 1
  const pageSize = Number(p.pageSize) || 10

  let list = BOOKS.filter((b) => b.status === 1)

  if (p.categoryId) list = list.filter((b) => String(b.categoryId) === String(p.categoryId))
  if (p.subCategoryId) list = list.filter((b) => String(b.subCategoryId) === String(p.subCategoryId))
  if (p.keyword) {
    const kw = String(p.keyword).trim().toLowerCase()
    list = list.filter(
      (b) =>
        b.title.toLowerCase().indexOf(kw) >= 0 ||
        (b.author || '').toLowerCase().indexOf(kw) >= 0 ||
        (b.publisher || '').toLowerCase().indexOf(kw) >= 0
    )
  }
  if (p.minPrice != null) list = list.filter((b) => b.price >= Number(p.minPrice))
  if (p.maxPrice != null) list = list.filter((b) => b.price <= Number(p.maxPrice))
  if (p.maxDiscount != null) list = list.filter((b) => b.discount <= Number(p.maxDiscount))
  // 新书页筛选：latest 全量按入库倒序；week 近 7 天；month 近 30 天
  if (p.range === 'week') list = list.filter((b) => withinDays(b.stockDate, 7))
  else if (p.range === 'month') list = list.filter((b) => withinDays(b.stockDate, 30))

  const sorter = SORTERS[p.sort]
  if (sorter) list = list.slice().sort(sorter)

  const total = list.length
  const start = (pageNum - 1) * pageSize
  return {
    list: clone(list.slice(start, start + pageSize)),
    total,
    pageNum,
    pageSize,
    pages: Math.max(1, Math.ceil(total / pageSize))
  }
}

/** 距基准日是否已超过 n 天（基准日与生成脚本保持一致） */
const BASE_DATE = '2026-10-17'

function daysAgo(dateText) {
  const t = new Date(dateText + 'T00:00:00').getTime()
  if (isNaN(t)) return 9999
  return Math.floor((new Date(BASE_DATE + 'T00:00:00').getTime() - t) / 86400000)
}

function withinDays(dateText, n) {
  const d = daysAgo(dateText)
  return d >= 0 && d <= n
}

/** 图书详情 */
export async function getBook(id) {
  await delay(160)
  const hit = BOOKS.find((b) => String(b.id) === String(id))
  if (!hit) throw new Error('not found')
  return clone(hit)
}

/** 按 id 批量取书（书单详情用，保持传入顺序） */
export async function getBooksByIds(ids) {
  await delay(180)
  const index = {}
  BOOKS.forEach((b) => { index[String(b.id)] = b })
  return (ids || []).map((i) => index[String(i)]).filter(Boolean).map(clone)
}

/** 书单频道 Tab */
export async function getBooklistTabs() {
  await delay(80)
  return clone(BOOKLIST_TABS)
}

/**
 * 书单分页。
 * @param {Object} params tabId / pageNum / pageSize
 */
export async function pageBooklists(params) {
  await delay()
  const p = params || {}
  const pageNum = Number(p.pageNum) || 1
  const pageSize = Number(p.pageSize) || 6
  let list = BOOKLISTS
  if (p.tabId && p.tabId !== 'all') list = list.filter((l) => l.tabId === p.tabId)

  // 卡片上的四宫格拼接封面需要图书对象（书名 + 封面），
  // 这里一次带出，页面就不必为每张卡片再发一次批量查询
  const index = {}
  BOOKS.forEach((b) => { index[String(b.id)] = b })
  const withPreview = list.map((l) => ({
    ...l,
    preview: l.coverIds.map((i) => index[String(i)]).filter(Boolean).map(clone)
  }))

  const total = withPreview.length
  const start = (pageNum - 1) * pageSize
  return {
    list: withPreview.slice(start, start + pageSize),
    total,
    pageNum,
    pageSize,
    pages: Math.max(1, Math.ceil(total / pageSize))
  }
}

/** 书单详情（含书单内全部图书） */
export async function getBooklistDetail(id) {
  await delay(200)
  const item = BOOKLISTS.find((l) => String(l.id) === String(id))
  if (!item) throw new Error('not found')
  const index = {}
  BOOKS.forEach((b) => { index[String(b.id)] = b })
  const books = item.bookIds.map((i) => index[String(i)]).filter(Boolean).map(clone)
  return { ...clone(item), books }
}

export { CATEGORIES, BOOKS, BOOKLIST_TABS, BOOKLISTS }
