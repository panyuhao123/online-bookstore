<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBook, pageBooks } from '@/api/book'
import { getCategories } from '@/api/category'
import { pageBookReviews, getReviewSummary } from '@/api/review'
import { subscribeStockNotice, unsubscribeStockNotice, pageMyStockNotices } from '@/api/stock-notice'
import CompareBar from '@/components/CompareBar.vue'
import BookCard from '@/components/BookCard.vue'
import { useCompareStore } from '@/stores/compare'
import { useCartStore } from '@/stores/cart'
import { useHistoryStore } from '@/stores/history'
import { useUserStore } from '@/stores/user'
import { useFreightStore } from '@/stores/freight'
import { addToCartGuarded } from '@/utils/cart-action'
import { setBuyNow } from '@/utils/buy-now'
import { formatPrice, formatRating, formatDateTime } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { bookBadges } from '@/utils/badges'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const compareStore = useCompareStore()
const cartStore = useCartStore()
const historyStore = useHistoryStore()
const userStore = useUserStore()
const freightStore = useFreightStore()

const book = ref(null)
const loading = ref(true)
const quantity = ref(1)
const activeTab = ref('description')

const categories = ref([])
const categoryName = computed(() => {
  const c = categories.value.find((c) => c.id === book.value?.categoryId)
  return c?.name || ''
})

// 评价
const reviews = ref([])
const reviewSummary = ref(null)
const reviewPage = ref(1)
const reviewTotal = ref(0)
const reviewPageSize = ref(10)

// 同类推荐
const similarBooks = ref([])

const cover = computed(() => (book.value ? bookCover(book.value) || placeholderCover(book.value.title) : ''))
const soldOut = computed(() => book.value?.stock <= 0)

/** 徽标（N5）：售罄 / 仅剩 N 件 / 热销 / 好评 / 包邮 */
const badges = computed(() =>
  bookBadges(book.value, {
    freeEnabled: freightStore.enabled,
    freeThreshold: freightStore.freeThreshold,
    limit: 4
  })
)

/** 到货提醒（N7）：仅售罄图书可订阅 */
const noticeSubscribed = ref(false)
const noticeLoading = ref(false)

async function loadStockNotice() {
  if (!userStore.isLogin || !soldOut.value) {
    noticeSubscribed.value = false
    return
  }
  try {
    // 没有「按 bookId 查订阅状态」的接口，从我的订阅列表里找 —— 单页 50 条足够覆盖，
    // 超出的场景只是订阅了很多书，此时按钮默认显示「到货提醒我」，重复订阅后端会幂等处理。
    const res = await pageMyStockNotices({ pageNum: 1, pageSize: 50 })
    noticeSubscribed.value = (res.list || []).some((n) => String(n.bookId) === String(book.value.id))
  } catch (e) {
    noticeSubscribed.value = false
  }
}

async function toggleStockNotice() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后再订阅到货提醒')
    router.push('/login')
    return
  }
  noticeLoading.value = true
  try {
    if (noticeSubscribed.value) {
      await unsubscribeStockNotice(book.value.id)
      noticeSubscribed.value = false
      ElMessage.info('已取消到货提醒')
    } else {
      await subscribeStockNotice(book.value.id)
      noticeSubscribed.value = true
      ElMessage.success('已订阅，补货后会第一时间提醒你')
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    noticeLoading.value = false
  }
}

async function loadBook() {
  loading.value = true
  // 切换图书时先把上一本的残留清掉，否则新数据到达前会短暂看到"别人的评分/评价"
  reviewPage.value = 1
  reviews.value = []
  reviewTotal.value = 0
  reviewSummary.value = null
  similarBooks.value = []
  noticeSubscribed.value = false
  try {
    book.value = await getBook(route.params.id)
    historyStore.addRecord(book.value)
    loadReviews()
    loadReviewSummary()
    loadSimilar()
    loadStockNotice()
  } catch (e) {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

async function loadReviews() {
  try {
    const res = await pageBookReviews(route.params.id, { pageNum: reviewPage.value, pageSize: reviewPageSize.value })
    reviews.value = res.list || []
    reviewTotal.value = res.total || 0
  } catch (e) {
    /* 静默 */
  }
}

async function loadReviewSummary() {
  try {
    reviewSummary.value = await getReviewSummary(route.params.id)
  } catch (e) {
    /* 静默 */
  }
}

/**
 * 同类推荐（N2）。
 *
 * <p>只看同分类有个明显缺口：冷门分类（比如只有一两本书）的详情页会几乎没有推荐。
 * 所以做成「同分类优先，同价位补足」—— 同价位用当前书价的 ±30% 区间跨分类取，
 * 这样"同类"退化成了"同档次"，对读者来说仍然是可解释的推荐理由。
 */
async function loadSimilar() {
  const current = book.value
  if (!current) return
  const result = []
  const seen = new Set([String(current.id)])

  const push = (books) => {
    for (const b of books || []) {
      if (seen.has(String(b.id))) continue
      seen.add(String(b.id))
      result.push(b)
    }
  }

  try {
    if (current.categoryId) {
      const res = await pageBooks({
        categoryId: current.categoryId,
        pageNum: 1,
        pageSize: 12,
        sort: 'sales'
      })
      push(res.list)
    }

    if (result.length < 5) {
      const price = Number(current.price || 0)
      if (price > 0) {
        const res = await pageBooks({
          minPrice: Math.max(1, Math.round(price * 0.7 * 100) / 100),
          maxPrice: Math.round(price * 1.3 * 100) / 100,
          pageNum: 1,
          pageSize: 12,
          sort: 'sales'
        })
        push(res.list)
      }
    }

    similarBooks.value = result.slice(0, 5)
  } catch (e) {
    /* 静默 */
  }
}

async function addToCart() {
  return addToCartGuarded(book.value, { quantity: quantity.value, router })
}

async function buyNow() {
  const ok = await addToCartGuarded(book.value, {
    quantity: quantity.value,
    router,
    silent: true
  })
  if (!ok) return
  // 加购后购物车已刷新，反查这次加入的 cartId：结算页只结算这一条，
  // 否则会连购物车里原本勾着的其它书一起下单。
  const item = cartStore.list.find((i) => i.bookId === book.value.id)
  setBuyNow(item ? [item.id] : [])
  router.push('/orders/confirm')
}

function addToCompare() {
  compareStore.add(book.value)
}

onMounted(async () => {
  try {
    categories.value = await getCategories()
  } catch (e) {
    /* 静默 */
  }
  // 运费规则用于「包邮」徽标与免运费提示；拿不到时 store 内部降级为不显示徽标
  freightStore.ensure().catch(() => {})
  loadBook()
})

// 从「同类推荐」点进另一本书时路由参数变了但组件被复用，onMounted 不会重跑 ——
// 不监听参数的话页面会一直停在上本书的数据上。
watch(() => route.params.id, loadBook)

const distribution = computed(() => reviewSummary.value?.distribution || [])
const maxCount = computed(() => Math.max(...distribution.value.map((d) => d.count || 0), 1))
</script>

<template>
  <div class="page-container book-detail" v-loading="loading">
    <template v-if="book">
      <!-- 左右分栏 -->
      <div class="detail-hero card">
        <div class="cover-section">
          <img :src="cover" :alt="book.title" class="main-cover" />
        </div>
        <div class="info-section">
          <h1 class="book-title">
            {{ book.title }}
            <el-tag
              v-for="b in badges"
              :key="b.key"
              :type="b.type"
              size="small"
              effect="plain"
              class="title-badge"
            >
              {{ b.text }}
            </el-tag>
          </h1>
          <div class="book-meta">
            <span>作者：{{ book.author }}</span>
            <span>出版社：{{ book.publisher }}</span>
            <span v-if="categoryName">分类：{{ categoryName }}</span>
          </div>

          <div class="rating-row">
            <el-rate :model-value="Number(book.avgRating || 0)" disabled show-score text-color="#ff9900" />
            <span class="rating-text">{{ formatRating(book.avgRating) }}</span>
            <span class="review-count">{{ book.reviewCount || 0 }} 条评价</span>
          </div>

          <div class="price-box">
            <span class="current-price">{{ formatPrice(book.price) }}</span>
            <span v-if="book.stock > 0" class="stock-tag">有货（库存 {{ book.stock }}）</span>
            <span v-else class="stock-tag out">已售罄</span>
            <span
              v-if="freightStore.enabled && freightStore.isFreeShipping(book.price)"
              class="free-tag"
            >
              满 {{ formatPrice(freightStore.freeThreshold) }} 包邮
            </span>
            <span v-else-if="freightStore.enabled" class="free-tag muted">
              再买 {{ formatPrice(freightStore.amountToFreeShipping(book.price)) }} 包邮
            </span>
          </div>

          <div class="action-row">
            <div class="quantity-box">
              <el-button size="small" @click="quantity > 1 && quantity--" :disabled="soldOut">-</el-button>
              <el-input-number v-model="quantity" :min="1" :max="book.stock" controls-position="" style="width: 100px" />
              <el-button size="small" @click="quantity < book.stock && quantity++" :disabled="soldOut">+</el-button>
            </div>
            <el-button type="primary" size="large" :disabled="soldOut" @click="addToCart">
              <el-icon><ShoppingCart /></el-icon> 加入购物车
            </el-button>
            <el-button type="danger" size="large" :disabled="soldOut" @click="buyNow">立即购买</el-button>
            <!-- 售罄时给一条明确的出路：留个联系方式等补货，比只能干看着强 -->
            <el-button
              v-if="soldOut"
              type="warning"
              size="large"
              :loading="noticeLoading"
              @click="toggleStockNotice"
            >
              <el-icon><Bell /></el-icon>
              {{ noticeSubscribed ? '已订阅到货提醒' : '到货提醒我' }}
            </el-button>
            <el-button size="large" @click="addToCompare">
              <el-icon><ScaleToOriginal /></el-icon> 加入对比
            </el-button>
          </div>

          <div class="sales-row">
            <span>销量：{{ book.sales || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- Tab 区 -->
      <div class="detail-tabs card">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="商品简介" name="description">
            <div class="tab-content">
              <p v-if="book.description" class="desc-text">{{ book.description }}</p>
              <p v-else class="empty-desc">暂无简介</p>
            </div>
          </el-tab-pane>

          <el-tab-pane label="图书目录" name="catalog">
            <div class="tab-content">
              <p class="empty-desc">暂无目录信息</p>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="'用户评价 (' + reviewTotal + ')'" name="reviews">
            <div class="tab-content reviews-tab">
              <!-- 评分汇总 -->
              <div class="review-summary" v-if="reviewSummary">
                <div class="score-box">
                  <div class="score">{{ formatRating(reviewSummary.avgRating) }}</div>
                  <el-rate :model-value="Number(reviewSummary.avgRating || 0)" disabled />
                  <div class="score-label">共 {{ reviewSummary.reviewCount }} 条评价</div>
                </div>
                <div class="distribution">
                  <div v-for="d in distribution" :key="d.rating" class="dist-row">
                    <span class="dist-label">{{ d.rating }}星</span>
                    <el-progress :percentage="Math.round((d.count / maxCount) * 100)" :stroke-width="8" :show-text="false" color="#f5a623" />
                    <span class="dist-count">{{ d.count }}</span>
                  </div>
                </div>
              </div>

              <!-- 评价表格 -->
              <el-table
                v-if="reviews.length > 0"
                :data="reviews"
                style="width: 100%; margin-top: 16px"
              >
                <el-table-column prop="nickname" label="用户" width="140">
                  <template #default="{ row }">
                    <div class="review-user-cell">
                      <el-avatar :size="28" class="review-avatar">{{ row.nickname?.charAt(0) || '匿' }}</el-avatar>
                      <span>{{ row.nickname || '匿名用户' }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="评分" width="160">
                  <template #default="{ row }">
                    <el-rate :model-value="row.rating" disabled size="small" />
                  </template>
                </el-table-column>
                <el-table-column prop="content" label="评价内容" min-width="300">
                  <template #default="{ row }">
                    <span class="review-content-cell">{{ row.content || '此用户未填写评价内容' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="时间" width="180">
                  <template #default="{ row }">
                    <span style="color: var(--text-sub); font-size: 12px">{{ formatDateTime(row.createTime) }}</span>
                  </template>
                </el-table-column>
              </el-table>

              <el-pagination
                v-if="reviewTotal > reviewPageSize"
                v-model:current-page="reviewPage"
                v-model:page-size="reviewPageSize"
                :total="reviewTotal"
                layout="prev, pager, next, total"
                background
                style="margin-top: 16px; justify-content: center"
                @current-change="loadReviews"
              />

              <el-empty v-else-if="reviews.length === 0" description="暂无评价" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 同类推荐 -->
      <section v-if="similarBooks.length > 0" class="similar-section">
        <div class="floor-header">
          <div class="floor-title">
            <span class="floor-bar" />
            <h2>同类推荐</h2>
          </div>
          <el-link type="primary" @click="router.push('/catalog?categoryId=' + book.categoryId)">查看更多 ›</el-link>
        </div>
        <div class="book-grid">
          <BookCard v-for="b in similarBooks" :key="b.id" :book="b" />
        </div>
      </section>
    </template>

    <el-result v-else-if="!loading" icon="warning" title="图书不存在" sub-title="该图书可能已下架">
      <template #extra>
        <el-button type="primary" @click="router.push('/books')">返回图书列表</el-button>
      </template>
    </el-result>

    <CompareBar />
  </div>
</template>

<style scoped>
.detail-hero {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 28px;
  padding: 24px;
  margin-bottom: 16px;
}

.cover-section {
  display: flex;
  justify-content: center;
}

.main-cover {
  width: 320px;
  height: 426px;
  object-fit: cover;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.book-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.3;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title-badge {
  font-weight: 400;
  flex-shrink: 0;
}

.free-tag {
  font-size: 13px;
  color: #67c23a;
  background: rgba(103, 194, 58, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
}

.free-tag.muted {
  color: var(--text-sub);
  background: var(--bg-fill);
}

.book-meta {
  display: flex;
  gap: 20px;
  color: var(--text-sub);
  font-size: 14px;
  flex-wrap: wrap;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rating-text {
  color: #f5a623;
  font-weight: 600;
}

.review-count {
  color: var(--text-sub);
}

.price-box {
  background: var(--brand-weak);
  padding: 16px 20px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.current-price {
  font-size: 30px;
  color: var(--brand);
  font-weight: 700;
}

.stock-tag {
  font-size: 13px;
  color: #67c23a;
}

.stock-tag.out {
  color: var(--text-sub);
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.quantity-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sales-row {
  color: var(--text-sub);
  font-size: 13px;
}

.detail-tabs {
  padding: 16px 20px;
  margin-bottom: 16px;
}

.tab-content {
  padding: 16px 4px;
  min-height: 160px;
}

.desc-text {
  line-height: 1.8;
  color: var(--text-regular);
  white-space: pre-wrap;
}

.empty-desc {
  color: var(--text-sub);
  text-align: center;
  padding: 40px 0;
}

.reviews-tab {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.review-summary {
  display: flex;
  gap: 36px;
  background: var(--bg-fill);
  padding: 18px 20px;
  border-radius: var(--radius-md);
}

.score-box {
  text-align: center;
  min-width: 110px;
}

.score {
  font-size: 32px;
  font-weight: 700;
  color: #f5a623;
}

.score-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}

.distribution {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dist-label {
  width: 32px;
  font-size: 13px;
  color: var(--text-regular);
}

.dist-count {
  width: 32px;
  font-size: 13px;
  color: var(--text-sub);
  text-align: right;
}

.review-user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-avatar {
  background: var(--brand);
  color: #fff;
  font-size: 12px;
}

.review-content-cell {
  line-height: 1.6;
  color: var(--text-regular);
}

/* 同类推荐 */
.similar-section {
  margin-bottom: 20px;
}

.floor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 10px;
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
  font-size: 18px;
  color: var(--text-primary);
  margin: 0;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
</style>
