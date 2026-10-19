/**
 * 官方书单定义与解析。
 *
 * <p>设计取舍：**不硬编码图书 id**，而是把书单定义为「规则」，打开时再经既有
 * 图书分页接口解析成真实图书。理由是演示库（`db/mock_dataset.sql`）与
 * `db/seed.sql` 的图书 id 段位不同（10001+ / 1~24），写死 id 在换数据源后
 * 会整单失效；按分类名 + 排序、或按书名检索解析，换数据源也能自适应。
 *
 * <p>规则两种形态：
 * <ul>
 *   <li>`{ categoryName, sort, limit }` —— 按分类名（而非 id）聚合，如「畅销小说精选」；</li>
 *   <li>`{ picks: [...], limit }` —— 人工挑书名，逐个检索后按序合并，如「心理学入门」。</li>
 * </ul>
 *
 * <p>排序值只能是后端白名单内的 `default / sales / price_asc / price_desc`
 * （见 `BookQuery.ALLOWED_SORTS`），传其它值会被后端静默回落成 default。
 */
import { pageBooks } from '@/api/book'
import { getCategories } from '@/api/category'

export const OFFICIAL_BOOKLISTS = [
  {
    id: 'official-1',
    name: '计算机经典必读',
    description: '程序员成长路上绕不开的经典著作',
    tag: '计算机',
    rule: {
      limit: 10,
      picks: [
        '深入理解计算机系统',
        '算法导论',
        '人月神话',
        '重构',
        '计算机网络',
        '现代操作系统',
        '数据密集型应用系统设计',
        'Java 编程思想',
        'Redis设计与实现',
        '高性能MySQL'
      ]
    }
  },
  {
    id: 'official-2',
    name: '畅销小说精选',
    description: '口碑与销量双丰收的当代小说',
    tag: '小说',
    rule: { categoryName: '小说', sort: 'sales', limit: 10 }
  },
  {
    id: 'official-3',
    name: '人文社科通识',
    description: '拓宽视野的人文社科入门书单',
    tag: '历史',
    rule: { categoryName: '历史', sort: 'default', limit: 10 }
  },
  {
    id: 'official-4',
    name: '心理学入门',
    description: '认识自己、理解他人的必读书目',
    tag: '心理学',
    rule: {
      limit: 8,
      picks: ['心理学与生活', '蛤蟆先生', '被讨厌的勇气', '快与慢', '自控力', '亲密关系']
    }
  }
]

/** 是否为内置官方书单 id（形如 `official-1`） */
export function isOfficialId(id) {
  return String(id ?? '').startsWith('official-')
}

/** 按 id 取官方书单定义 */
export function getOfficialBooklist(id) {
  return OFFICIAL_BOOKLISTS.find((l) => l.id === String(id)) || null
}

/** 分类名 → 分类 id（取不到返回 null，调用方按空书单处理） */
async function resolveCategoryId(name) {
  const categories = await getCategories()
  const hit = (categories || []).find((c) => c.name === name)
  return hit ? hit.id : null
}

/** 单个关键词检索，失败不抛（少一本书好过整单报错） */
async function searchByKeyword(keyword) {
  try {
    const res = await pageBooks({ keyword, pageNum: 1, pageSize: 5 })
    return res?.list || []
  } catch (e) {
    return []
  }
}

/**
 * 把书单规则解析成图书数组。
 * @param {object} def OFFICIAL_BOOKLISTS 中的一项
 * @returns {Promise<Array>} 图书列表（解析不到就是空数组）
 */
export async function fetchOfficialBooks(def) {
  const rule = def?.rule
  if (!rule) return []

  if (rule.categoryName) {
    const categoryId = await resolveCategoryId(rule.categoryName)
    if (!categoryId) return []
    const res = await pageBooks({
      categoryId,
      sort: rule.sort || 'default',
      pageNum: 1,
      pageSize: rule.limit || 12
    })
    return res?.list || []
  }

  if (rule.picks?.length) {
    // 逐个书名检索后合并：保留了「人工挑选」的意图，又不依赖具体 id。
    // 检索结果里优先取书名完全相等的，没有就退而取第一条。
    const batches = await Promise.all(rule.picks.map((kw) => searchByKeyword(kw)))
    const seen = new Set()
    const out = []
    batches.forEach((books, idx) => {
      if (books.length === 0) return
      const kw = rule.picks[idx]
      const best = books.find((b) => b.title === kw) || books[0]
      if (!best || seen.has(best.id)) return
      seen.add(best.id)
      out.push(best)
    })
    return out.slice(0, rule.limit || out.length)
  }

  return []
}
