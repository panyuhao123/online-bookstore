<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageBooks } from '@/api/book'
import { getCategories } from '@/api/category'
import BookCard from '@/components/BookCard.vue'
import CompareBar from '@/components/CompareBar.vue'
import { useCompareStore } from '@/stores/compare'
import { addBatchToCartGuarded } from '@/utils/cart-action'
import { saveBooksToNewBooklist } from '@/utils/booklist-action'
import { ElMessage } from 'element-plus'

/**
 * 商品目录（S6）。
 *
 * <p>和「图书列表」的分工：列表页是**带着关键词来**的（搜索、筛选、表格批量操作），
 * 目录页是**不知道该买什么、只想逛**的 —— 所以这里把分类摆成一排平铺的页签
 * （带每类书目数），切类不跳页、不丢筛选条件，默认按销量排，先看这个类里什么最受欢迎。
 *
 * <p>分类导航做成横向页签而不是第二根左侧栏：全局侧栏已经有分类树，
 * 页内再来一根竖栏，左边就会同时立着两列几乎一样的东西。
 */

/** 后端 BookQuery.ALLOWED_SORTS 白名单；传其它值后端会静默回落成 default */
const ALLOWED_SORTS = ['default', 'sales', 'price_asc', 'price_desc']

const SORT_OPTIONS = [
  { label: '综合', value: 'default' },
  { label: '销量优先', value: 'sales' },
  { label: '价格从低到高', value: 'price_asc' },
  { label: '价格从高到低', value: 'price_desc' }
]

const PRICE_RANGES = [
  { label: '全部价格', min: null, max: null },
  { label: '50 元以下', min: null, max: 50 },
  { label: '50 - 100 元', min: 50, max: 100 },
  { label: '100 - 200 元', min: 100, max: 200 },
  { label: '200 元以上', min: 200, max: null }
]

const route = useRoute()
const router = useRouter()
const compareStore = useCompareStore()

const categories = ref([])
const categoryLoading = ref(false)
const activeCategoryId = ref(null)

const books = ref([])
const total = ref(0)
const loading = ref(false)
const selectedBooks = ref([])

const query = reactive({
  categoryId: null,
  minPrice: null,
  maxPrice: null,
  // 目录页默认按销量排：逛的人想先看"这个类里卖得最好的"
  sort: 'sales',
  pageNum: 1,
  pageSize: 20
})
const activePriceRange = ref(0)

const activeCategory = computed(
  () => categories.value.find((c) => String(c.id) === String(activeCategoryId.value)) || null
)

/**
 * `/api/category/list` 自 D-10 起返回 `bookCount`（**只统计上架图书**，口径见后端
 * `CategoryMapper.selectOnSaleBookCounts()`）。
 *
 * <p>此前该字段不存在，本页把各类的 `bookCount` 累加当"全店图书数"，
 * 页面上写的是「全店 0 本图书」「小说 0」，而右侧分页信息又写着「共 102 本」，
 * 同一屏两个互相打架的数字。
 *
 * <p>注意页签数字与右侧「共 N 本」口径不同：页签是该分类的**上架总数**，
 * 右侧是当前筛选条件下（含价格区间、关键词）的命中数，筛选后两者不相等是正常的。
 */
const onSaleTotal = computed(() =>
  categories.value.reduce((sum, c) => sum + (Number(c.bookCount) || 0), 0)
)

async function loadCategories() {
  categoryLoading.value = true
  try {
    categories.value = (await getCategories()) || []
  } catch (e) {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

async function loadBooks() {
  loading.value = true
  try {
    const res = await pageBooks({ ...query })
    books.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 切分类。
 *
 * <p>分类 id 写进 URL：这样"某个分类"可以直接分享 / 收藏，
 * 刷新和前进后退也不会跳回全部图书。
 */
function setCategory(id) {
  activeCategoryId.value = id
  query.categoryId = id
  query.pageNum = 1
  selectedBooks.value = []
  const nextQuery = id ? { categoryId: id } : {}
  router.replace({ path: '/catalog', query: nextQuery })
  loadBooks()
}

function setPriceRange(idx) {
  activePriceRange.value = idx
  query.minPrice = PRICE_RANGES[idx].min
  query.maxPrice = PRICE_RANGES[idx].max
  query.pageNum = 1
  loadBooks()
}

function setSort(val) {
  query.sort = ALLOWED_SORTS.includes(val) ? val : 'default'
  query.pageNum = 1
  loadBooks()
}

function applyCustomPrice() {
  query.pageNum = 1
  // 手动填了区间就不再对应任何预设档，避免高亮和实际条件对不上
  activePriceRange.value = -1
  loadBooks()
}

function resetFilters() {
  query.minPrice = null
  query.maxPrice = null
  query.sort = 'sales'
  activePriceRange.value = 0
  query.pageNum = 1
  loadBooks()
}

function handlePageChange(page) {
  query.pageNum = page
  loadBooks()
}

function handleSelectionChange(rows) {
  selectedBooks.value = rows
}

function batchAddToCart() {
  return addBatchToCartGuarded(selectedBooks.value, { router })
}

async function batchAddToBooklist() {
  if (selectedBooks.value.length === 0) {
    ElMessage.warning('请先选择图书')
    return
  }
  const created = await saveBooksToNewBooklist(selectedBooks.value, { router })
  if (created) router.push('/booklists/' + created.id)
}

/** 从 URL 读初始分类（刷新 / 分享链接进入时用） */
function applyRouteQuery() {
  const q = route.query
  activeCategoryId.value = q.categoryId ? Number(q.categoryId) : null
  query.categoryId = activeCategoryId.value
  query.pageNum = 1
}

onMounted(async () => {
  applyRouteQuery()
  await Promise.all([loadCategories(), loadBooks()])
})

// 只监听 categoryId：页内切分类是自己 router.replace 出来的，
// 全量监听 query 会和 setCategory 里已经发起的请求撞车、白跑一次
watch(
  () => route.query.categoryId,
  (val) => {
    const id = val ? Number(val) : null
    if (String(id) === String(activeCategoryId.value)) return
    activeCategoryId.value = id
    query.categoryId = id
    query.pageNum = 1
    loadBooks()
  }
)
</script>

<template>
  <div class="page-container catalog-page">
    <div class="page-head">
      <h2 class="page-title">商品目录</h2>
      <p class="page-sub">
        <template v-if="activeCategory">
          「{{ activeCategory.name }}」共 {{ total }} 本
        </template>
        <template v-else> 全店 {{ categories.length }} 个分类 · {{ onSaleTotal }} 本在售 </template>
      </p>
    </div>

    <!-- 分类页签：平铺展示，带每类书目数 -->
    <div v-loading="categoryLoading" class="cat-tabs card">
      <span
        class="cat-tab"
        :class="{ active: activeCategoryId === null }"
        @click="setCategory(null)"
      >
        全部图书
        <em v-if="onSaleTotal > 0" class="cnt">{{ onSaleTotal }}</em>
      </span>
      <span
        v-for="cat in categories"
        :key="cat.id"
        class="cat-tab"
        :class="{ active: String(activeCategoryId) === String(cat.id) }"
        @click="setCategory(cat.id)"
      >
        {{ cat.name }}
        <!-- 0 本也照实显示：藏着反而让用户以为页签坏了 -->
        <em v-if="cat.bookCount !== null && cat.bookCount !== undefined" class="cnt">{{ cat.bookCount }}</em>
      </span>
    </div>

    <!-- 筛选条 -->
    <div class="toolbar card">
      <div class="filter-line">
        <span class="filter-label">价格</span>
        <el-tag
          v-for="(r, idx) in PRICE_RANGES"
          :key="idx"
          :type="activePriceRange === idx ? 'danger' : 'info'"
          :effect="activePriceRange === idx ? 'dark' : 'plain'"
          class="price-tag"
          @click="setPriceRange(idx)"
        >
          {{ r.label }}
        </el-tag>
        <el-input-number
          v-model="query.minPrice"
          :min="0"
          size="small"
          placeholder="最低"
          controls-position="right"
          style="width: 110px"
        />
        <span class="sep">~</span>
        <el-input-number
          v-model="query.maxPrice"
          :min="0"
          size="small"
          placeholder="最高"
          controls-position="right"
          style="width: 110px"
        />
        <el-button size="small" type="primary" @click="applyCustomPrice">确定</el-button>
        <el-button size="small" @click="resetFilters">重置</el-button>
      </div>
      <div class="filter-line">
        <span class="filter-label">排序</span>
        <el-radio-group v-model="query.sort" size="small" @change="setSort">
          <el-radio-button v-for="s in SORT_OPTIONS" :key="s.value" :label="s.value">
            {{ s.label }}
          </el-radio-button>
        </el-radio-group>
        <span class="total-text">共 {{ total }} 本</span>
      </div>
    </div>

    <!-- 批量操作条 -->
    <div v-if="selectedBooks.length > 0" class="batch-bar">
      <span class="batch-info">已选 <b>{{ selectedBooks.length }}</b> 本</span>
      <el-button type="primary" size="small" @click="batchAddToCart">
        <el-icon><ShoppingCart /></el-icon> 批量加购
      </el-button>
      <el-button type="success" size="small" @click="batchAddToBooklist">
        <el-icon><Collection /></el-icon> 批量存书单
      </el-button>
      <el-button size="small" @click="compareStore.addMany(selectedBooks)">
        <el-icon><ScaleToOriginal /></el-icon> 加入对比
      </el-button>
      <el-button size="small" @click="selectedBooks = []">取消选择</el-button>
    </div>

    <!-- 空状态 -->
    <el-result
      v-if="!loading && books.length === 0"
      icon="empty"
      title="该分类下暂无符合条件的图书"
      sub-title="换个分类，或把价格区间放宽一些"
    >
      <template #extra>
        <el-button type="primary" @click="resetFilters">重置筛选</el-button>
      </template>
    </el-result>

    <template v-else>
      <div class="book-grid" v-loading="loading">
        <BookCard v-for="book in books" :key="book.id" :book="book" />
      </div>

      <!-- 表格视图：便于勾选后批量操作 -->
      <el-table
        :data="books"
        v-loading="loading"
        class="catalog-table card"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="title" label="书名" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push('/books/' + row.id)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="140" />
        <el-table-column prop="publisher" label="出版社" width="180" />
        <el-table-column prop="price" label="价格" width="100" />
        <el-table-column prop="stock" label="库存" width="90">
          <template #default="{ row }">
            <span :class="{ 'sold-out': row.stock <= 0 }">
              {{ row.stock <= 0 ? '售罄' : row.stock }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="90" />
      </el-table>

      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="handlePageChange"
        />
      </div>
    </template>

    <CompareBar />
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 14px;
}

.page-title {
  font-size: 20px;
  margin-bottom: 4px;
}

.page-sub {
  font-size: 13px;
  color: var(--text-sub);
}

.cat-tabs {
  padding: 12px 16px;
  margin-bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--text-regular);
  background: var(--bg-fill);
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.cat-tab:hover {
  color: var(--brand);
  border-color: var(--brand-weak);
}

.cat-tab.active {
  background: var(--brand);
  color: #fff;
}

.cat-tab .cnt {
  font-style: normal;
  font-size: 11px;
  opacity: 0.75;
}

.toolbar {
  padding: 12px 16px;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 13px;
  color: var(--text-sub);
  flex-shrink: 0;
}

.price-tag {
  cursor: pointer;
}

.sep {
  color: var(--text-sub);
}

.total-text {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-sub);
}

.batch-bar {
  background: var(--brand-weak);
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.batch-info b {
  color: var(--brand);
  font-size: 15px;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 16px;
  min-height: 60px;
}

.catalog-table {
  margin-bottom: 12px;
}

.sold-out {
  color: var(--text-sub);
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 12px 0 20px;
}
</style>
