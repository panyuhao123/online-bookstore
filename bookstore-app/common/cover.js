/**
 * 图书封面：地址解析 + 渐变占位。
 *
 * 地址有两种来源，页面不必关心书是 mock 数据还是后端数据：
 *   - 演示数据集的封面是 APP 本地静态资源 /static/cover/...，直接用；
 *   - 真实后端的封面是 /files/... 相对路径，需要拼上后端地址。
 *
 * 抓不到封面的书（豆瓣/当当都没有合规结果）coverUrl 为空，
 * 此时渲染「按书名哈希选出的柔和渐变块 + 书名首字」，
 * 既不会出现黑洞，也不会所有占位图长得一模一样。
 */
import { coverSrc } from '@/common/config'

/** 图书封面地址 */
export function bookCover(book) {
  const p = (book && book.coverUrl) || ''
  if (!p) return ''
  if (/^https?:\/\//.test(p)) return p
  if (p.indexOf('/static/') === 0) return p
  return coverSrc(p)
}

/**
 * 兜底渐变：低饱和、高明度，与 UI 规范的浅色系一致。
 * 只做背景用，上面压的图片加载完成后会自然盖住。
 */
const GRADIENTS = [
  ['#f7e8e4', '#eed3cd'],
  ['#e9eef7', '#d5e0f0'],
  ['#ecf2ea', '#dae7d7'],
  ['#f6f0e3', '#ebdfc6'],
  ['#f1ecf6', '#e1d8ee'],
  ['#f4ede7', '#e4d7cb'],
  ['#e7f1f2', '#d1e4e8'],
  ['#f5e9ee', '#e8d4df']
]

/** 按书名取稳定渐变（同一本书永远是同一个色） */
export function coverGradient(title) {
  const s = String(title || '书')
  let h = 0
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) >>> 0
  }
  const pair = GRADIENTS[h % GRADIENTS.length]
  return `linear-gradient(145deg, ${pair[0]} 0%, ${pair[1]} 100%)`
}

/** 折扣文案：0.28 -> '2.8折'；满折不显示 */
export function discountText(discount) {
  const d = Number(discount)
  if (!d || d >= 0.999 || d <= 0) return ''
  const t = Math.round(d * 100) / 10
  return `${t % 1 === 0 ? t.toFixed(0) : t.toFixed(1)}折`
}
