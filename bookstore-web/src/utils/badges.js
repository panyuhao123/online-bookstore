/**
 * 图书徽标（N5）。
 *
 * <p>徽标是"扫一眼就知道要不要点进去"的东西，所以每条都必须有数据支撑，
 * 不能为了好看硬凑：
 * <ul>
 *   <li>已售罄 / 仅剩 N 件 —— 直接影响能不能买，优先级最高；</li>
 *   <li>热销 —— 用销量，不是运营手工挂的标签；</li>
 *   <li>好评 —— 评分 ≥ 4.5 <b>且</b>评价数 ≥ 3，只有一条五星好评的书不该挂"好评"；</li>
 *   <li>包邮 —— 由当前运费门槛算出来，门槛被后台改了徽标会跟着变。</li>
 * </ul>
 *
 * <p>刻意没有「新品」：`BookVO` 不下发 `createTime`，没有时间字段就只能靠猜，
 * 与其挂一个假的"新品"，不如不挂。
 */

/** 库存低于该值提示"仅剩 N 件"（和用户的紧迫感阈值对齐，不必可配） */
const LOW_STOCK = 5

/** 判定"热销"的销量线 */
const HOT_SALES = 100

/** 判定"好评"的最低评分与最少评价数 */
const GOOD_RATING = 4.5
const GOOD_REVIEW_MIN = 3

/**
 * 生成图书徽标。
 *
 * @param {object} book 图书对象（BookVO）
 * @param {object} [options]
 * @param {boolean} [options.freeEnabled=false] 运费规则是否启用
 * @param {number}  [options.freeThreshold=0] 免运费门槛
 * @param {number}  [options.limit=2] 最多返回几个（卡片位置有限，默认只留最要紧的两个）
 * @returns {Array<{key:string,text:string,type:string}>} type 对应 el-tag 的 type
 */
export function bookBadges(book, options = {}) {
  const { freeEnabled = false, freeThreshold = 0, limit = 2 } = options
  const out = []
  if (!book) return out

  const stock = Number(book.stock ?? 0)
  if (stock <= 0) {
    // 售罄时别的徽标都没意义了：一本买不到的书，"包邮""热销"只会误导。
    // 这里直接返回，售罄信息不会被 limit 挤掉。
    return [{ key: 'soldout', text: '已售罄', type: 'info' }]
  }

  if (stock <= LOW_STOCK) {
    out.push({ key: 'low', text: `仅剩 ${stock} 件`, type: 'warning' })
  }

  if (Number(book.sales || 0) >= HOT_SALES) {
    out.push({ key: 'hot', text: '热销', type: 'danger' })
  }

  if (Number(book.avgRating || 0) >= GOOD_RATING && Number(book.reviewCount || 0) >= GOOD_REVIEW_MIN) {
    out.push({ key: 'good', text: '好评', type: 'success' })
  }

  if (freeEnabled && Number(book.price || 0) >= Number(freeThreshold || 0)) {
    out.push({ key: 'free', text: '包邮', type: 'primary' })
  }

  return out.slice(0, limit)
}
