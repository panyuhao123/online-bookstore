<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageBooks, getHotBooks } from '@/api/book'
import BookCard from '@/components/BookCard.vue'
import { formatPrice } from '@/utils/format'
import { exportToExcel } from '@/utils/excel'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'

const router = useRouter()
const hotBooks = ref([])
const newBooks = ref([])
const bestSellerBooks = ref([]) // 热销好书（与右侧榜单同源但取不同数量用于网格展示）

// Banner 轮播
const banners = [
  { id: 1, title: '新书上架', desc: '海量新书 抢先阅读', color: 'linear-gradient(135deg,#667eea,#764ba2)' },
  { id: 2, title: '低价优选', desc: '精选好书 低至5折', color: 'linear-gradient(135deg,#f093fb,#f5576c)' },
  { id: 3, title: '畅销榜单', desc: '热门好书 不容错过', color: 'linear-gradient(135deg,#4facfe,#00f2fe)' }
]

onMounted(async () => {
  try {
    // 右侧热销榜单：Top 8
    hotBooks.value = await getHotBooks(8)

    // 新书上架：按创建时间排序取前 10
    const newRes = await pageBooks({ pageNum: 1, pageSize: 10, sort: 'default' })
    newBooks.value = newRes.list || []

    // 热销好书：按销量排序取前 10
    const bestRes = await pageBooks({ pageNum: 1, pageSize: 10, sort: 'sales' })
    bestSellerBooks.value = bestRes.list || []
  } catch (e) {
    /* 静默 */
  }
})

function goBook(id) {
  router.push('/books/' + id)
}

const rankColors = ['#e6a23c', '#909399', '#c08a4e']

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
      <!-- 左：Banner -->
      <div class="banner-wrap">
        <el-carousel height="360px" arrow="hover">
          <el-carousel-item v-for="b in banners" :key="b.id">
            <div class="banner-slide" :style="{ background: b.color }">
              <div class="banner-text">
                <h2>{{ b.title }}</h2>
                <p>{{ b.desc }}</p>
                <el-button type="primary" size="large" round @click="router.push('/books')">
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

    <!-- 新书上架：5 列网格 -->
    <section class="floor">
      <div class="floor-header">
        <div class="floor-title">
          <span class="floor-bar" />
          <h2>新书上架</h2>
          <span class="floor-sub">最新到货 抢先阅读</span>
        </div>
        <el-link type="primary" @click="router.push('/books')">查看更多 ›</el-link>
      </div>
      <div class="book-grid">
        <BookCard v-for="book in newBooks" :key="book.id" :book="book" />
      </div>
    </section>

    <!-- 热销好书：5 列网格 -->
    <section class="floor">
      <div class="floor-header">
        <div class="floor-title">
          <span class="floor-bar" />
          <h2>热销好书</h2>
          <span class="floor-sub">畅销排行 不容错过</span>
        </div>
        <el-link type="primary" @click="router.push('/books?sort=sales')">查看更多 ›</el-link>
      </div>
      <div class="book-grid">
        <BookCard v-for="book in bestSellerBooks" :key="book.id" :book="book" />
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
}

.banner-text {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
