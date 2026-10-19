<script setup>
import { ref, onMounted } from 'vue'
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
 * 首页。
 *
 * <p>四块内容，按"用户从不知道买什么到知道买什么"的顺序排：
 * Banner（今天有什么活动）→ 热销榜（别人都在买什么）→ 分类楼层（按类逛）
 * → 猜你喜欢（顺着你自己的口味推）。
 */

const router = useRouter()
const historyStore = useHistoryStore()

const banners = ref([])
const hotBooks = ref([])
const floors = ref([])
const recommend = ref([])
const recommendSub = ref('')

/** Banner 拉不到时用的兜底，保证首屏永远不是一块空白 */
const FALLBACK_BANNERS = [
  {
    id: 'f1',
    title: '新书上架',
    subTitle: '海量新书 抢先阅读',
    bgColor: 'linear-gradient(135deg,#667eea,#764ba2)',
    linkUrl: '/books'
  },
  {
    id: 'f2',
    title: '低价优选',
    subTitle: '精选好书 低至 5 折',
    bgColor: 'linear-gradient(135deg,#f093fb,#f5576c)',
    linkUrl: '/books?sort=price_asc'
  },
  {
    id: 'f3',
    title: '畅销榜单',
    subTitle: '热门好书 不容错过',
    bgColor: 'linear-gradient(135deg,#4facfe,#00f2fe)',
    linkUrl: '/books?sort=sales'
  }
]

const rankColors = ['#e6a23c', '#909399', '#c08a4e']

/** 后台配了图就用图，没配图就用背景色（bgColor 支持 #xxx 或 linear-gradient(...)） */
function bannerStyle(b) {
  if (b.imageUrl) {
    return {
      backgroundImage: `url(${b.imageUrl})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center'
    }
  }
  return { background: b.bgColor || 'linear-gradient(135deg,#667eea,#764ba2)' }
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
  // 四块各自独立加载：Banner 挂了不该连累热销榜，反之亦然
  loadBanners()
  loadHot()
  loadFloors()
  loadRecommend()
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
  <div class="page-container home-page">
    <!-- 首屏两栏：Banner + 热销榜（整个页面的左侧分类栏已由 AppSidebar 承担） -->
    <div class="hero-row">
      <!-- 左：Banner（S4，数据来自后台配置） -->
      <div class="banner-wrap">
        <el-carousel height="360px" arrow="hover">
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
                <el-button v-if="b.linkUrl" type="primary" size="large" round @click.stop="goBanner(b)">
                  立即选购
                </el-button>
              </div>
            </div>
          </el-carousel-item>
        </el-carousel>
      </div>

      <!-- 右：热销榜 -->
      <div class="rank-card card">
        <div class="rank-title">
          <div class="rank-title-left">
            <el-icon color="var(--brand)"><Trophy /></el-icon>
            <span>热销榜单</span>
          </div>
          <el-button link type="primary" size="small" :icon="Download" @click="exportHotRank">
            导出 Excel
          </el-button>
        </div>
        <ol class="rank-list">
          <li
            v-for="(book, idx) in hotBooks"
            :key="book.id"
            class="rank-item"
            @click="goBook(book.id)"
          >
            <span
              class="rank-no"
              :style="{
                background: rankColors[idx] || '#dcdfe6',
                color: idx < 3 ? '#fff' : '#555'
              }"
            >{{ idx + 1 }}</span>
            <div class="rank-info">
              <p class="rank-title-text text-ellipsis">{{ book.title }}</p>
              <p class="rank-author text-ellipsis">{{ book.author }}</p>
            </div>
            <span class="rank-price">{{ formatPrice(book.price) }}</span>
          </li>
        </ol>
      </div>
    </div>

    <!-- 猜你喜欢（N6） -->
    <section v-if="recommend.length > 0" class="floor">
      <div class="floor-header">
        <div class="floor-title">
          <span class="floor-bar" />
          <h2>猜你喜欢</h2>
          <span class="floor-sub">{{ recommendSub }}</span>
        </div>
        <el-link type="primary" @click="router.push('/books')">查看更多 ›</el-link>
      </div>
      <div class="book-grid">
        <BookCard v-for="book in recommend" :key="book.id" :book="book" />
      </div>
    </section>

    <!-- 分类楼层（N4）：每个分类一层，按销量取前 5 -->
    <section v-for="floor in floors" :key="floor.category.id" class="floor">
      <div class="floor-header">
        <div class="floor-title">
          <span class="floor-bar" />
          <h2>{{ floor.category.name }}</h2>
          <span class="floor-sub">
            {{ floor.category.bookCount || 0 }} 本在售 · 按销量排序
          </span>
        </div>
        <el-link type="primary" @click="router.push('/catalog?categoryId=' + floor.category.id)">
          查看更多 ›
        </el-link>
      </div>
      <div class="book-grid">
        <BookCard v-for="book in floor.books" :key="book.id" :book="book" />
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 首屏：两栏（Banner | 热销榜 280px） */
.hero-row {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 20px;
  margin-bottom: 36px;
}

.banner-wrap {
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.banner-slide {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 60px;
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
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.35), rgba(0, 0, 0, 0));
  pointer-events: none;
}

.banner-text {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.banner-text h2 {
  font-size: 40px;
  font-weight: 700;
  margin: 0;
  letter-spacing: 2px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.banner-text p {
  font-size: 18px;
  opacity: 0.9;
  margin: 0;
}

/* 热销榜卡片 */
.rank-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  height: 360px;
}

.rank-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.rank-title-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rank-list {
  padding: 0;
  flex: 1;
  overflow: hidden;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  cursor: pointer;
  border-bottom: 1px dashed var(--border-light);
  transition: background 0.15s;
}

.rank-item:hover {
  background: var(--bg-fill);
  border-radius: 4px;
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-no {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: #dcdfe6;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-title-text {
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.rank-author {
  font-size: 11px;
  color: var(--text-sub);
}

.rank-price {
  font-size: 13px;
  color: var(--brand);
  font-weight: 600;
  flex-shrink: 0;
}

/* 板块通用 */
.floor {
  margin-bottom: 36px;
}

.floor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--brand);
}

.floor-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.floor-bar {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: var(--brand);
  border-radius: 2px;
}

.floor-title h2 {
  font-size: 20px;
  color: var(--text-primary);
  margin: 0;
}

.floor-sub {
  font-size: 12px;
  color: var(--text-sub);
}

/* 5 列网格 */
.book-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
</style>
