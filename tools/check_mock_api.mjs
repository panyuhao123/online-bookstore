/**
 * 演示数据集接口层校验（tools/check_mock_api.mjs）
 *
 * common/mock/index.js 不依赖任何 uni API，因此可以直接在 Node 里跑真实断言，
 * 不必起 HBuilderX 或后端。四个演示页的筛选、排序、分页逻辑全在这里，
 * 改了数据或排序规则后跑一遍就能确认没有把某一档筛成空集。
 *
 * 用法：node tools/check_mock_api.mjs
 */
import {
  MOCK_ID_BASE,
  isMockId,
  getCategories,
  getBook,
  getBooklistDetail,
  getBooklistTabs,
  pageBooklists,
  pageBooks
} from '../bookstore-app/common/mock/index.js'

let pass = 0
let fail = 0

function ok(name, cond, extra = '') {
  if (cond) {
    pass++
    console.log(`  [OK]   ${name}${extra ? '  ' + extra : ''}`)
  } else {
    fail++
    console.log(`  [FAIL] ${name}${extra ? '  ' + extra : ''}`)
  }
}

function asc(arr) {
  return arr.every((v, i) => i === 0 || arr[i - 1] <= v)
}

function desc(arr) {
  return arr.every((v, i) => i === 0 || arr[i - 1] >= v)
}

console.log('\n=== ① 分类 ===')
const cats = await getCategories()
ok('一级分类 9 个', cats.length === 9, `实际 ${cats.length}`)
const subCount = cats.reduce((s, c) => s + c.children.length, 0)
ok('二级分类 48 个', subCount === 48, `实际 ${subCount}`)
ok('每个一级分类都有二级', cats.every((c) => c.children.length > 0))
ok('一级/二级 id 不冲突', cats.every((c) => c.children.every((s) => s.id > c.id)))
ok('分类名与需求一致',
  ['小说', '文学', '少儿', '历史', '经管', '计算机', '教辅', '艺术', '生活']
    .every((n) => cats.some((c) => c.name === n)))

console.log('\n=== ② 图书分页与筛选 ===')
const all = await pageBooks({ pageNum: 1, pageSize: 1000 })
ok('图书总数 102', all.total === 102, `实际 ${all.total}`)

const novel = cats.find((c) => c.name === '小说')
const byCat = await pageBooks({ categoryId: novel.id, pageNum: 1, pageSize: 1000 })
ok('按一级分类筛选有结果', byCat.total > 0, `小说 ${byCat.total} 本`)
ok('分类筛选结果都属于该分类', byCat.list.every((b) => b.categoryId === novel.id))

const sub = novel.children[0]
const bySub = await pageBooks({ subCategoryId: sub.id, pageNum: 1, pageSize: 1000 })
ok('按二级分类筛选有结果', bySub.total > 0, `${sub.name} ${bySub.total} 本`)
ok('二级筛选结果都属于该子类', bySub.list.every((b) => b.subCategoryId === sub.id))

const kw = await pageBooks({ keyword: '余华', pageNum: 1, pageSize: 1000 })
ok('关键词命中作者', kw.total >= 2, `余华 ${kw.total} 本`)

const catKw = await pageBooks({ categoryId: novel.id, keyword: '活着', pageNum: 1, pageSize: 1000 })
ok('分类内检索受分类约束', catKw.total === 1 && catKw.list[0].title === '活着')

console.log('\n=== ③ 新书上架（筛选 + 排序） ===')
for (const [range, min] of [['latest', 102], ['week', 20], ['month', 45]]) {
  const r = await pageBooks({ range, sort: 'new', pageNum: 1, pageSize: 1000 })
  ok(`${range} 有足量新书`, r.total >= min, `${r.total} 本`)
}
const byNew = await pageBooks({ sort: 'new', pageNum: 1, pageSize: 1000 })
ok('入库时间倒序', desc(byNew.list.map((b) => b.stockDate)))
const byPub = await pageBooks({ sort: 'publish', pageNum: 1, pageSize: 1000 })
ok('出版时间倒序', desc(byPub.list.map((b) => b.publishDate)))
const bySales = await pageBooks({ sort: 'sales', pageNum: 1, pageSize: 1000 })
ok('热度（销量）倒序', desc(bySales.list.map((b) => b.sales)))

console.log('\n=== ④ 低价优选（折扣 + 价格 + 排序） ===')
const d5 = await pageBooks({ maxDiscount: 0.5, pageNum: 1, pageSize: 1000 })
ok('5 折及以下有足量书目', d5.total >= 20, `${d5.total} 本`)
ok('5 折结果折扣都 <= 0.5', d5.list.every((b) => b.discount <= 0.5))
const d3 = await pageBooks({ maxDiscount: 0.3, pageNum: 1, pageSize: 1000 })
ok('3 折专区有足量书目', d3.total >= 6, `${d3.total} 本`)
ok('3 折结果折扣都 <= 0.3', d3.list.every((b) => b.discount <= 0.3))
const cheapAsc = await pageBooks({ maxDiscount: 0.5, sort: 'price_asc', pageNum: 1, pageSize: 1000 })
ok('价格升序', asc(cheapAsc.list.map((b) => b.price)))
const cheapDisc = await pageBooks({ maxDiscount: 0.5, sort: 'discount', pageNum: 1, pageSize: 1000 })
ok('折扣升序', asc(cheapDisc.list.map((b) => b.discount)))
for (const [label, min, max, expectMin] of [
  ['30 以下', null, 30, 5],
  ['30-50', 30, 50, 10],
  ['50-100', 50, 100, 10],
  ['100 以上', 100, null, 3]
]) {
  const r = await pageBooks({ minPrice: min, maxPrice: max, pageNum: 1, pageSize: 1000 })
  ok(`价格区间「${label}」有结果`, r.total >= expectMin, `${r.total} 本`)
}
const combo = await pageBooks({
  maxDiscount: 0.5, minPrice: 30, maxPrice: 50, sort: 'price_asc', pageNum: 1, pageSize: 1000
})
ok('折扣 + 价格区间可叠加', combo.total > 0, `${combo.total} 本`)
ok('叠加结果同时满足两个条件',
  combo.list.every((b) => b.discount <= 0.5 && b.price >= 30 && b.price <= 50))

console.log('\n=== ⑤ 分页 ===')
const p1 = await pageBooks({ pageNum: 1, pageSize: 10 })
const p2 = await pageBooks({ pageNum: 2, pageSize: 10 })
ok('首页返回 10 条', p1.list.length === 10)
ok('第二页返回 10 条', p2.list.length === 10)
ok('两页无重复', !p1.list.some((a) => p2.list.some((b) => b.id === a.id)))
ok('pages 计算正确', p1.pages === Math.ceil(102 / 10), `pages=${p1.pages}`)
const last = await pageBooks({ pageNum: 11, pageSize: 10 })
ok('末页返回剩余 2 条', last.list.length === 2, `实际 ${last.list.length}`)

console.log('\n=== ⑥ 图书详情与主键隔离 ===')
const b1 = await getBook(MOCK_ID_BASE)
ok('按 id 取详情', b1 && b1.title.length > 0, b1.title)
ok('演示数据主键被识别', isMockId(MOCK_ID_BASE) === true)
ok('真实库主键不被误判', isMockId(5) === false)
let threw = false
try {
  await getBook(1)
} catch (e) {
  threw = true
}
ok('不存在的 id 抛错', threw)
ok('字段兼容后端 BookVO',
  ['id', 'title', 'author', 'publisher', 'price', 'stock', 'coverUrl', 'description', 'sales', 'status']
    .every((k) => k in b1))
ok('演示扩展字段齐全',
  ['originalPrice', 'discount', 'subCategoryId', 'publishDate', 'stockDate', 'isNew', 'promoTags']
    .every((k) => k in b1))
ok('定价高于售价', b1.originalPrice > b1.price, `${b1.originalPrice} > ${b1.price}`)

console.log('\n=== ⑦ 书单 ===')
const tabs = await getBooklistTabs()
ok('4 个频道 Tab', tabs.length === 4, tabs.map((t) => t.name).join(' / '))
ok('Tab 与需求一致',
  ['热门书单', '新书书单', '高分必读', '限时特价书单'].every((n) => tabs.some((t) => t.name === n)))

for (const t of tabs) {
  const r = await pageBooklists({ tabId: t.id, pageNum: 1, pageSize: 100 })
  ok(`「${t.name}」有书单`, r.total >= 4, `${r.total} 个`)
  ok(`「${t.name}」书单都带 4 本预览封面`, r.list.every((l) => l.preview && l.preview.length === 4))
  ok(`「${t.name}」书单书目数 >= 6`, r.list.every((l) => l.bookCount >= 6),
    `最少 ${Math.min(...r.list.map((l) => l.bookCount))} 本`)
  ok(`「${t.name}」书单都有标签`, r.list.every((l) => l.tags && l.tags.length > 0))
}

const hotP1 = await pageBooklists({ tabId: 'hot', pageNum: 1, pageSize: 4 })
const hotP2 = await pageBooklists({ tabId: 'hot', pageNum: 2, pageSize: 4 })
ok('书单分页：首页 4 个', hotP1.list.length === 4)
ok('书单分页：第二页有剩余', hotP2.list.length >= 1, `${hotP2.list.length} 个`)
ok('书单分页无重复', !hotP1.list.some((a) => hotP2.list.some((b) => b.id === a.id)))

const firstList = hotP1.list[0]
const detail = await getBooklistDetail(firstList.id)
ok('书单详情带回全部图书', detail.books.length === detail.bookCount,
  `${detail.title} ${detail.books.length} 本`)
ok('书单详情图书可加购（有库存字段）', detail.books.every((b) => typeof b.stock === 'number'))
ok('书单详情带收藏人数', typeof detail.collectCount === 'number' && detail.collectCount > 0)

console.log('\n=== ⑧ 数据完整性 ===')
const books = all.list
ok('id 全部 >= 10001', books.every((b) => b.id >= MOCK_ID_BASE))
ok('id 无重复', new Set(books.map((b) => b.id)).size === books.length)
ok('书名无重复', new Set(books.map((b) => b.title)).size === books.length)
ok('都有作者与出版社', books.every((b) => b.author && b.publisher))
ok('都有简介', books.every((b) => b.description && b.description.length >= 10))
ok('价格都为正', books.every((b) => b.price > 0))
ok('无评价时 avgRating 为 null', books.every((b) => b.avgRating === null || b.avgRating > 0))
ok('有评分的都有评价数', books.filter((b) => b.avgRating).every((b) => b.reviewCount > 0))
ok('评分在 1~5 分制内', books.every((b) => b.avgRating === null || (b.avgRating >= 1 && b.avgRating <= 5)))
ok('售罄比例合理（<15%）', books.filter((b) => b.stock === 0).length / books.length < 0.15,
  `${books.filter((b) => b.stock === 0).length} 本售罄`)
ok('促销标签取值合法',
  books.every((b) => (b.promoTags || []).every((t) => ['特价', '满减', '清仓'].includes(t))))

console.log('\n' + '='.repeat(58))
console.log(`通过 ${pass} 项 / 失败 ${fail} 项`)
process.exit(fail ? 1 : 0)
