<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageBooks, getHotBooks, getBook } from '@/api/book'
import { getCategories } from '@/api/category'
import { getBanners } from '@/api/banner'
import { useHistoryStore } from '@/stores/history'
import BookCard from '@/components/BookCard.vue'
import { formatPrice } from '@/utils/format'
import { exportToExcel } from '@/utils/excel'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'

/**
 * 首页（当当风格）。
 *
 * <p>排布逻辑与当当一致：**先把入口铺满，再谈内容**。
 * 首屏三栏 —— 左分类树（不知道买什么就按类逛）、中轮播（今天有什么活动）、
 * 右公告与热销榜（别人都在买什么）；首屏之下是限时特惠（给你一个现在就下单的理由）、
 * 猜你喜欢（顺着你的口味推）、分类楼层（按类深入）。
 *
 * <p>这跟改造前「大 Banner + 5 列网格」的差别不在于好不好看，而在于
 * 首屏能点的地方从 5 个变成 20 多个 —— 电商首页的转化率取决于入口数量。
 */

const router = useRouter()
const historyStore = useHistoryStore()

const banners = ref([])
const hotBooks = ref([])
const floors = ref([])
const recommend = ref([])
const recommendSub = ref('')
const categories = ref([])
const deals = ref([])
const popoverBooks = ref({})

/** Banner 拉不到时用的兜底，保证首屏永远不是一块空白 */
const FALLBACK_BANNERS = [
  {
    id: 'f1',
    title: '新书上架',
    subTitle: '海量新书 抢先阅读',
    bgColor: 'linear-gradient(135deg,#ff2832,#ff6f00)',
    linkUrl: '/books'
  },
  {
    id: 'f2',
    title: '低价优选',
    subTitle: '精选好书 低至 5 折',
    bgColor: 'linear-gradient(135deg,#ff6f00,#ffa726)',
    linkUrl: '/books?sort=price_asc'
  },
  {
    id: 'f3',
    title: '畅销榜单',
    subTitle: '热门好书 不容错过',
    bgColor: 'linear-gradient(135deg,#e6162d,#ff6f00)',
    linkUrl: '/books?sort=sales'
  }
]

const rankColors = ['#ff2832', '#ff6f00', '#f5a623']

/** 后台配了图就用图，没配图就用背景色（bgColor 支持 #xxx 或 linear-gradient(...)） */
function bannerStyle(b) {
  if (b.imageUrl) {
    return {
      backgroundImage: `url(${b.imageUrl})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center'
    }
  }
  return { background: b.bgColor || 'linear-gradient(135deg,#ff2832,#ff6f00)' }
}

function goBanner(b) {
  const url = b.linkUrl
  if (!url) return
  // 外部链接新开标签页，站内路径走路由（站内跳转不该把整个应用重新加载一遍）
  if (/^https?:\/\//i.test(url)) window.open(url, '_blank', 'noopener')
  else router.push(url)
}

function goBook(id) {
  router.push('/books/' + id)
}

function goCategory(id) {
  router.push({ path: '/catalog', query: { categoryId: id } })
}

/* ===== 限时特惠倒计时：到当天 24 点 ===== */
const countdown = ref('00:00:00')
let timer = null
function tick() {
  const now = new Date()
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0)
  let s = Math.max(0, Math.floor((end - now) / 1000))
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const sec = String(s % 60).padStart(2, '0')
  countdown.value = `${h}:${m}:${sec}`
}

async function loadBanners() {
  try {
    const list = await getBanners()
    // 后台可能把所有 Banner 都停用了，这时同样要兜底
    banners.value = list && list.length > 0 ? list : FALLBACK_BANNERS
  } catch (e) {
    banners.value = FALLBACK_BANNERS
  }
}

async function loadHot() {
  try {
    hotBooks.value = await getHotBooks(8)
  } catch (e) {
    hotBooks.value = []
  }
}

async function loadCategories() {
  try {
    categories.value = (await getCategories()) || []
  } catch (e) {
    categories.value = []
  }
}

/** 分类树二级面板：hover 才拉，避免首屏并发 10 个请求 */
async function onCatHover(cat) {
  if (popoverBooks.value[cat.id]) return
  try {
    const res = await pageBooks({ categoryId: cat.id, pageNum: 1, pageSize: 4, sort: 'sales' })
    popoverBooks.value = { ...popoverBooks.value, [cat.id]: res.list || [] }
  } catch (e) {
    popoverBooks.value = { ...popoverBooks.value, [cat.id]: [] }
  }
}

/**
 * 限时特惠：按价格升序取 5 本。
 *
 * <p>不用热销榜的数据 —— 「便宜」和「卖得好」是两件事，
 * 特惠位放热销书只会让用户发现自己买贵了。
 */
async function loadDeals() {
  try {
    const res = await pageBooks({ pageNum: 1, pageSize: 5, sort: 'price_asc' })
    deals.value = res.list || []
  } catch (e) {
    deals.value = []
  }
}

/**
 * 分类楼层（N4）。
 *
 * <p>取前 4 个分类、每类按销量取 5 本 —— 再多首页就变成一张无限长的目录了。
 * 空分类直接不渲染：一个标题下面挂着"暂无图书"比不显示更难看。
 */
async function loadFloors() {
  let cats = []
  try {
    cats = (await getCategories()) || []
  } catch (e) {
    floors.value = []
    return
  }
  const picked = cats.slice(0, 4)
  const results = await Promise.allSettled(
    picked.map((c) => pageBooks({ categoryId: c.id, pageNum: 1, pageSize: 5, sort: 'sales' }))
  )
  floors.value = picked
    .map((category, i) => ({
      category,
      books: results[i].status === 'fulfilled' ? results[i].value?.list || [] : []
    }))
    .filter((f) => f.books.length > 0)
}

/**
 * 猜你喜欢（N6）。
 *
 * <p>数据源是浏览历史。历史记录里只有 bookId 和标题、**没有分类**，
 * 所以要拿最近 3 本去问一下详情，才能凑出"你最近在看哪类书"。
 * 只探 3 本：再多就是拿用户的时间换一点推荐精度，不划算。
 *
 * <p>推不出来（没登录、没历史、接口失败）就退化成热销榜，
 * 但副标题会如实写"热销好书"而不是假装是"根据你的浏览记录"。
 */
async function loadRecommend() {
  if (historyStore.isLogin) {
    // 登录态下服务端历史要显式拉一次，store 里的 list 默认是空的
    await historyStore.fetchPage({ pageNum: 1, pageSize: 10 }).catch(() => {})
  }
  const viewed = (historyStore.displayList || []).slice(0, 10)
  const seen = new Set(viewed.map((r) => r.bookId).filter((id) => id !== undefined && id !== null))

  const catIds = []
  const probe = viewed.slice(0, 3)
  if (probe.length > 0) {
    const details = await Promise.allSettled(probe.map((r) => getBook(r.bookId)))
    details.forEach((d) => {
      const cid = d.status === 'fulfilled' ? d.value?.categoryId : null
      if (cid && !catIds.includes(cid)) catIds.push(cid)
    })
  }

  const out = []
  for (const cid of catIds.slice(0, 2)) {
    try {
      const res = await pageBooks({ categoryId: cid, pageNum: 1, pageSize: 8, sort: 'sales' })
      for (const b of res.list || []) {
        if (seen.has(b.id)) continue
        seen.add(b.id)
        out.push(b)
        if (out.length >= 10) break
      }
    } catch (e) {
      /* 单个分类失败不影响其它 */
    }
    if (out.length >= 10) break
  }

  const fromHistory = out.length
  if (out.length < 10) {
    try {
      const res = await pageBooks({ pageNum: 1, pageSize: 20, sort: 'sales' })
      for (const b of res.list || []) {
        if (seen.has(b.id)) continue
        seen.add(b.id)
        out.push(b)
        if (out.length >= 10) break
      }
    } catch (e) {
      /* 静默 */
    }
  }

  recommend.value = out
  recommendSub.value =
    fromHistory > 0
      ? '根据你最近看过的书挑的'
      : viewed.length > 0
        ? '看过的同类书暂时不多，先看看大家都在买的'
        : '还没有浏览记录，先看看大家都在买的'
}

onMounted(() => {
  // 各块独立加载：Banner 挂了不该连累热销榜，反之亦然
  loadBanners()
  loadHot()
  loadCategories()
  loadDeals()
  loadFloors()
  loadRecommend()
  tick()
  timer = setInterval(tick, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function exportHotRank() {
  if (!hotBooks.value.length) {
    ElMessage.warning('暂无热销榜单数据')
    return
  }
  await exportToExcel(
    hotBooks.value.map((b, i) => ({ ...b, rank: i + 1 })),
    [
      { prop: 'rank', label: '排名', width: 8 },
      { prop: 'title', label: '书名', width: 30 },
      { prop: 'author', label: '作者', width: 16 },
      { prop: 'publisher', label: '出版社', width: 20 },
      { prop: 'price', label: '价格', width: 10 },
      { prop: 'avgRating', label: '评分', width: 8 }
    ],
    '热销榜单'
  )
  ElMessage.success('榜单已导出')
}
</script>

<template>
  <div class="home-page">
    <div class="container">
      <!-- ===== 首屏三栏：分类树 | 轮播 | 公告+热销榜 ===== -->
      <div class="first-screen">
        <!-- 左：分类树 -->
        <aside class="cat-tree">
          <el-popover
            v-for="cat in categories.slice(0, 10)"
            :key="cat.id"
            placement="right-start"
            :width="300"
            trigger="hover"
            :show-after="120"
            :hide-after="60"
            popper-class="cat-popover"
            @show="onCatHover(cat)"
          >
            <div class="pop-content">
              <div class="pop-head">
                <span class="pop-title">{{ cat.name }}</span>
                <a class="pop-more" @click="goCategory(cat.id)">查看全部 ›</a>
              </div>
              <div v-if="(popoverBooks[cat.id] || []).length" class="pop-books">
                <a
                  v-for="b in popoverBooks[cat.id]"
                  :key="b.id"
                  class="pop-book"
                  @click="goBook(b.id)"
                >
                  <span class="pop-book-title text-ellipsis">{{ b.title }}</span>
                  <span class="pop-book-price">{{ formatPrice(b.price) }}</span>
                </a>
              </div>
              <div v-else class="pop-empty">该分类暂无图书</div>
            </div>

            <template #reference>
              <div class="cat-item" @click="goCategory(cat.id)">
                <span class="cat-name">{{ cat.name }}</span>
                <el-icon class="cat-arrow" :size="12"><ArrowRight /></el-icon>
              </div>
            </template>
          </el-popover>
          <div v-if="categories.length === 0" class="cat-empty">分类加载中…</div>
        </aside>

        <!-- 中：轮播（S4） -->
        <div class="banner-col">
          <el-carousel height="360px" arrow="hover" :interval="5000">
            <el-carousel-item v-for="b in banners" :key="b.id">
              <div
                class="banner-slide"
                :style="bannerStyle(b)"
                :class="{ clickable: !!b.linkUrl }"
                @click="goBanner(b)"
              >
                <div class="banner-text">
                  <h2>{{ b.title }}</h2>
                  <p v-if="b.subTitle">{{ b.subTitle }}</p>
                  <button v-if="b.linkUrl" class="banner-cta" type="button" @click.stop="goBanner(b)">
                    立即选购
                  </button>
                </div>
                <div class="banner-dots">
                  <span v-for="n in banners.length" :key="n" class="dot" />
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>

        <!-- 右：公告 + 热销榜 -->
        <div class="side-col">
          <div class="notice-card">
            <div class="notice-head">服务公告</div>
            <ul class="notice-list">
              <li>满 99 元免运费</li>
              <li>正版保障 假一赔十</li>
              <li>7 天无理由退换</li>
              <li>订单满 199 元加赠书签</li>
            </ul>
          </div>

          <div class="rank-card">
            <div class="rank-head">
              <span class="rank-head-title">热销榜</span>
              <el-button link size="small" :icon="Download" @click="exportHotRank">
                导出
              </el-button>
            </div>
            <ol class="rank-list">
              <li
                v-for="(book, idx) in hotBooks.slice(0, 7)"
                :key="book.id"
                class="rank-item"
                @click="goBook(book.id)"
              >
                <span
                  class="rank-no"
                  :style="{ background: rankColors[idx] || '#cccccc' }"
                >{{ idx + 1 }}</span>
                <span class="rank-title-text text-ellipsis">{{ book.title }}</span>
                <span class="rank-price">{{ formatPrice(book.price) }}</span>
              </li>
            </ol>
          </div>
        </div>
      </div>

      <!-- ===== 限时特惠 ===== -->
      <section v-if="deals.length" class="deal-strip">
        <div class="deal-head">
          <div class="deal-title">
            <span class="deal-flag">限时特惠</span>
            <span class="deal-sub">每日 0 点更新 · 低至 5 折</span>
          </div>
          <div class="deal-clock">
            <span class="clock-label">距结束</span>
            <span class="clock-num">{{ countdown }}</span>
          </div>
        </div>
        <div class="deal-body">
          <div
            v-for="b in deals"
            :key="b.id"
            class="deal-item"
            @click="goBook(b.id)"
          >
            <div class="deal-cover">
              <img :src="b.coverUrl" :alt="b.title" loading="lazy" />
              <span class="deal-tag">特价</span>
            </div>
            <p class="deal-name text-ellipsis-2">{{ b.title }}</p>
            <p class="deal-price">{{ formatPrice(b.price) }}</p>
          </div>
        </div>
      </section>

      <!-- ===== 猜你喜欢（N6） ===== -->
      <section v-if="recommend.length > 0" class="floor">
        <div class="floor-aside">
          <div class="floor-title">
            <h2>猜你喜欢</h2>
            <span class="floor-sub">{{ recommendSub }}</span>
          </div>
          <div class="floor-links">
            <a @click="router.push('/books?sort=sales')">热销图书</a>
            <a @click="router.push('/books?sort=price_asc')">低价好书</a>
            <a @click="router.push('/booklists')">编辑书单</a>
          </div>
          <a class="floor-more" @click="router.push('/books')">查看更多 ›</a>
        </div>
        <div class="floor-body">
          <div class="book-grid">
            <BookCard v-for="book in recommend" :key="book.id" :book="book" />
          </div>
        </div>
      </section>

      <!-- ===== 分类楼层（N4） ===== -->
      <section v-for="floor in floors" :key="floor.category.id" class="floor">
        <div class="floor-aside">
          <div class="floor-title">
            <h2>{{ floor.category.name }}</h2>
            <!-- 分类接口不返回 bookCount，这里不要写「0 本在售」 -->
            <span class="floor-sub">按销量排序</span>
          </div>
          <div class="floor-links">
            <a @click="router.push('/books?categoryId=' + floor.category.id + '&sort=sales')">
              按销量排
            </a>
            <a @click="router.push('/books?categoryId=' + floor.category.id + '&sort=price_asc')">
              价格从低到高
            </a>
            <a @click="router.push('/books?categoryId=' + floor.category.id + '&sort=price_desc')">
              价格从高到低
            </a>
          </div>
          <a class="floor-more" @click="router.push('/catalog?categoryId=' + floor.category.id)">
            查看更多 ›
          </a>
        </div>
        <div class="floor-body">
          <div class="book-grid">
            <BookCard v-for="book in floor.books" :key="book.id" :book="book" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  padding: 10px 0 30px;
}

/* ===== 首屏三栏 ===== */
.first-screen {
  display: grid;
  grid-template-columns: 220px 1fr 240px;
  gap: 10px;
  margin-bottom: 10px;
}

/* ---- 分类树 ---- */
.cat-tree {
  background: #fff;
  border: 1px solid var(--border);
  height: 360px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cat-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  border-bottom: 1px dashed var(--border-light);
}

.cat-item:last-child {
  border-bottom: none;
}

.cat-item:hover {
  background: var(--brand-weak);
  color: var(--brand);
}

.cat-arrow {
  color: var(--text-placeholder);
}

.cat-item:hover .cat-arrow {
  color: var(--brand);
}

.cat-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-sub);
}

/* ---- 轮播 ---- */
.banner-col {
  overflow: hidden;
  border: 1px solid var(--border);
  background: #fff;
}

.banner-slide {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 50px;
  color: #fff;
  position: relative;
}

.banner-slide.clickable {
  cursor: pointer;
}

/* 有背景图时压一层暗色蒙版，否则浅色图上白字看不清 */
.banner-slide::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.3), rgba(0, 0, 0, 0));
  pointer-events: none;
}

.banner-text {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.banner-text h2 {
  font-size: 34px;
  font-weight: 700;
  margin: 0;
  letter-spacing: 2px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.banner-text p {
  font-size: 16px;
  opacity: 0.92;
  margin: 0;
}

.banner-cta {
  align-self: flex-start;
  margin-top: 6px;
  padding: 7px 22px;
  background: #fff;
  color: var(--brand);
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.banner-cta:hover {
  background: var(--brand);
  color: #fff;
}

/* 装饰性页码点：只做氛围，不接管点击（关掉 pointer 让轮播箭头正常工作） */
.banner-dots {
  position: absolute;
  right: 20px;
  bottom: 16px;
  display: flex;
  gap: 5px;
  z-index: 1;
  pointer-events: none;
}

.banner-dots .dot {
  width: 18px;
  height: 3px;
  background: rgba(255, 255, 255, 0.5);
}

.banner-dots .dot:first-child {
  background: #fff;
}

/* ---- 右栏 ---- */
.side-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notice-card {
  background: #fff;
  border: 1px solid var(--border);
  padding: 10px 12px;
  flex-shrink: 0;
}

.notice-head {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 8px;
}

.notice-list li {
  font-size: 12px;
  color: var(--text-regular);
  line-height: 20px;
  padding-left: 10px;
  position: relative;
}

.notice-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 3px;
  height: 3px;
  background: var(--brand);
}

.rank-card {
  background: #fff;
  border: 1px solid var(--border);
  padding: 10px 12px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.rank-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--brand);
  margin-bottom: 6px;
}

.rank-head-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.rank-list {
  flex: 1;
  overflow: hidden;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 0;
  cursor: pointer;
  border-bottom: 1px dashed var(--border-light);
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-item:hover .rank-title-text {
  color: var(--brand);
}

.rank-no {
  width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.rank-title-text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--text-primary);
}

.rank-price {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--brand);
}

/* ===== 限时特惠 ===== */
.deal-strip {
  background: #fff;
  border: 1px solid var(--border);
  margin-bottom: 10px;
}

.deal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-light);
}

.deal-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.deal-flag {
  font-size: 16px;
  font-weight: 700;
  color: var(--brand);
}

.deal-sub {
  font-size: 12px;
  color: var(--text-sub);
}

.deal-clock {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-regular);
}

.clock-num {
  background: var(--text-primary);
  color: #fff;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  font-weight: 700;
  padding: 2px 6px;
  letter-spacing: 1px;
}

.deal-body {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  padding: 10px;
}

.deal-item {
  cursor: pointer;
  padding: 6px;
  border: 1px solid transparent;
  transition: border-color 0.15s;
}

.deal-item:hover {
  border-color: var(--brand);
}

.deal-cover {
  position: relative;
  /* 书封基本是 3:4，容器跟着书封比例走，5 张封面才对得齐 */
  aspect-ratio: 3 / 4;
  background: #fff;
  overflow: hidden;
  margin-bottom: 6px;
  border: 1px solid var(--border-light);
}

/* 书封是竖版，裁成方图会把书名切掉，用 contain 留白反而更像"书" */
.deal-cover img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.deal-tag {
  position: absolute;
  left: 0;
  top: 0;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
}

.deal-name {
  font-size: 12px;
  color: var(--text-primary);
  line-height: 18px;
  height: 36px;
}

.deal-price {
  font-size: 15px;
  font-weight: 700;
  color: var(--brand);
  margin-top: 4px;
}

/* ===== 响应式 ===== */
@media (max-width: 1199px) {
  .first-screen {
    grid-template-columns: 200px 1fr;
  }
  .side-col {
    display: none;
  }
  .deal-body {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 991px) {
  .first-screen {
    grid-template-columns: 1fr;
  }
  .cat-tree {
    display: none;
  }
  .deal-body {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>

<style>
/* 分类树二级面板被 teleport 到 body，scoped 够不着 */
.cat-popover {
  padding: 10px 12px !important;
}

.cat-popover .pop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--border-light);
}

.cat-popover .pop-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.cat-popover .pop-more {
  font-size: 12px;
  color: var(--brand);
  cursor: pointer;
}

.cat-popover .pop-book {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 5px 6px;
  font-size: 12px;
  cursor: pointer;
}

.cat-popover .pop-book:hover {
  background: var(--brand-weak);
}

.cat-popover .pop-book-title {
  flex: 1;
  min-width: 0;
  color: var(--text-regular);
}

.cat-popover .pop-book-price {
  flex-shrink: 0;
  color: var(--brand);
  font-weight: 600;
}

.cat-popover .pop-empty {
  padding: 14px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}
</style>
