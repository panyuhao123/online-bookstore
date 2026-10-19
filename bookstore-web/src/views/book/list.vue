<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageBooks } from '@/api/book'
import BookCard from '@/components/BookCard.vue'
import CompareBar from '@/components/CompareBar.vue'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { useBooklistStore } from '@/stores/booklist'
import { formatPrice, formatRating } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()
const booklistStore = useBooklistStore()

const books = ref([])
const total = ref(0)
const loading = ref(false)
const viewMode = ref('card') // card | table

const query = reactive({
  keyword: '',
  categoryId: null,
  minPrice: null,
  maxPrice: null,
  sort: 'default',
  pageNum: 1,
  pageSize: 20
})

// 表格多选
const selectedBooks = ref([])
const compareBarRef = ref(null)

const priceRanges = [
  { label: '全部', min: null, max: null },
  { label: '50以下', min: null, max: 50 },
  { label: '50-100', min: 50, max: 100 },
  { label: '100-200', min: 100, max: 200 },
  { label: '200以上', min: 200, max: null }
]
const activePriceRange = ref(0)

const sortOptions = [
  { label: '综合', value: 'default' },
  { label: '销量', value: 'sales' },
  { label: '价格↑', value: 'price_asc' },
  { label: '价格↓', value: 'price_desc' }
]

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

function setPriceRange(idx) {
  activePriceRange.value = idx
  query.minPrice = priceRanges[idx].min
  query.maxPrice = priceRanges[idx].max
  query.pageNum = 1
  loadBooks()
}

function setSort(val) {
  query.sort = val
  query.pageNum = 1
  loadBooks()
}

function search() {
  query.pageNum = 1
  loadBooks()
}

function resetFilters() {
  query.keyword = ''
  query.categoryId = null
  query.minPrice = null
  query.maxPrice = null
  query.sort = 'default'
  activePriceRange.value = 0
  query.pageNum = 1
  loadBooks()
}

function handlePageChange(page) {
  query.pageNum = page
  loadBooks()
}

// 表格多选
function handleSelectionChange(rows) {
  selectedBooks.value = rows
}

async function batchAddToCart() {
  if (selectedBooks.value.length === 0) {
    ElMessage.warning('请先选择图书')
    return
  }
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    localStorage.setItem('bookstore_web_redirect', route.fullPath)
    router.push('/login')
    return
  }
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedBooks.value.length} 本图书加入购物车？`, '提示')
    for (const book of selectedBooks.value) {
      if (book.stock > 0) {
        await cartStore.add(book.id, 1)
      }
    }
    ElMessage.success('已加入购物车')
  } catch (e) {
    /* 取消 */
  }
}

async function batchAddToBooklist() {
  if (selectedBooks.value.length === 0) {
    ElMessage.warning('请先选择图书')
    return
  }
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    const { value: name } = await ElMessageBox.prompt('请输入书单名称', '保存为书单', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPattern: /^\S+$/,
      inputErrorMessage: '书单名称不能为空'
    })
    const list = booklistStore.createBooklist(name)
    booklistStore.addBooksToList(list.id, selectedBooks.value)
    ElMessage.success(`已保存 ${selectedBooks.value.length} 本书到书单「${name}」`)
    router.push('/booklists/' + list.id)
  } catch (e) {
    /* 取消 */
  }
}

function addToCompare(book) {
  compareBarRef.value?.addToCompare(book)
}

const tableColumns = [
  { prop: 'title', label: '书名' },
  { prop: 'author', label: '作者', width: 120 },
  { prop: 'publisher', label: '出版社', width: 160 },
  { prop: 'price', label: '价格', width: 100 },
  { prop: 'stock', label: '库存', width: 80 },
  { prop: 'avgRating', label: '评分', width: 100 }
]

onMounted(async () => {
  // 从 URL 读取初始筛选
  if (route.query.keyword) query.keyword = route.query.keyword
  if (route.query.categoryId) query.categoryId = Number(route.query.categoryId)
  loadBooks()
})

watch(() => route.query, (q) => {
  if (q.keyword !== undefined) query.keyword = q.keyword
  if (q.categoryId !== undefined) query.categoryId = q.categoryId ? Number(q.categoryId) : null
  loadBooks()
})
</script>

<template>
  <div class="page-container book-list-page">
    <!-- 筛选面板 -->
    <div class="filter-panel card">
      <div class="filter-row">
        <span class="filter-label">价格：</span>
        <div class="filter-tags">
          <el-tag
            v-for="(r, idx) in priceRanges"
            :key="idx"
            :type="activePriceRange === idx ? 'danger' : 'info'"
            :effect="activePriceRange === idx ? 'dark' : 'plain'"
            class="price-tag"
            @click="setPriceRange(idx)"
          >
            {{ r.label }}
          </el-tag>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">自定义：</span>
        <el-input-number v-model="query.minPrice" :min="0" placeholder="最低价" size="small" style="width: 110px" controls-position="right" />
        <span class="sep">~</span>
        <el-input-number v-model="query.maxPrice" :min="0" placeholder="最高价" size="small" style="width: 110px" controls-position="right" />
        <el-button size="small" type="primary" @click="search">确定</el-button>
        <el-button size="small" @click="resetFilters">重置</el-button>
      </div>
    </div>

    <!-- 工具栏：排序 + 视图切换 + 批量操作 -->
    <div class="toolbar card">
      <div class="sort-group">
        <el-radio-group v-model="query.sort" size="small" @change="setSort">
          <el-radio-button v-for="s in sortOptions" :key="s.value" :label="s.value">{{ s.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tool-right">
        <span class="total-text">共 {{ total }} 本</span>
        <el-button-group>
          <el-button size="small" :type="viewMode === 'card' ? 'primary' : 'default'" @click="viewMode = 'card'">
            <el-icon><Grid /></el-icon> 卡片
          </el-button>
          <el-button size="small" :type="viewMode === 'table' ? 'primary' : 'default'" @click="viewMode = 'table'">
            <el-icon><List /></el-icon> 表格
          </el-button>
        </el-button-group>
      </div>
    </div>

    <!-- 批量操作条（表格视图） -->
    <div v-if="viewMode === 'table' && selectedBooks.length > 0" class="batch-bar">
      <span class="batch-info">已选 <b>{{ selectedBooks.length }}</b> 本</span>
      <el-button type="primary" size="small" @click="batchAddToCart">
        <el-icon><ShoppingCart /></el-icon> 批量加购
      </el-button>
      <el-button type="success" size="small" @click="batchAddToBooklist">
        <el-icon><Collection /></el-icon> 批量加书单
      </el-button>
      <el-button size="small" @click="selectedBooks.forEach(b => addToCompare(b))">
        <el-icon><ScaleToOriginal /></el-icon> 加入对比
      </el-button>
    </div>

    <!-- 空状态 -->
    <el-result
      v-if="!loading && books.length === 0"
      icon="empty"
      title="未找到符合条件的图书"
      sub-title="试试调整筛选条件或清空筛选"
    >
      <template #extra>
        <el-button type="primary" @click="resetFilters">清空筛选</el-button>
      </template>
    </el-result>

    <template v-else>
      <!-- 卡片视图 -->
      <div v-if="viewMode === 'card'" class="book-grid">
        <BookCard v-for="book in books" :key="book.id" :book="book" />
      </div>

      <!-- 表格视图 -->
      <el-table
        v-if="viewMode === 'table'"
        :data="books"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        style="width: 100%"
        class="book-table"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <img :src="row.coverUrl" class="table-cover" @click="router.push('/books/' + row.id)" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push('/books/' + row.id)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="publisher" label="出版社" width="160" />
        <el-table-column prop="price" label="价格" width="100">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column label="评分" width="100">
          <template #default="{ row }">
            {{ formatRating(row.avgRating) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="cartStore.add(row.id, 1)">加购</el-button>
            <el-button size="small" link @click="addToCompare(row)">对比</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="handlePageChange"
      />
    </div>

    <CompareBar ref="compareBarRef" />
  </div>
</template>

<style scoped>
.filter-panel {
  padding: 16px;
  margin-bottom: 12px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.filter-row:last-child {
  margin-bottom: 0;
}

.filter-label {
  color: var(--text-sub);
  font-size: 13px;
  flex-shrink: 0;
}

.filter-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.price-tag {
  cursor: pointer;
}

.sep {
  color: var(--text-sub);
}

.toolbar {
  padding: 12px 16px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tool-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.total-text {
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
}

.table-cover {
  width: 50px;
  height: 66px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
}

.book-table {
  margin-bottom: 12px;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}
</style>
