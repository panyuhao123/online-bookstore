<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pageBooks } from '@/api/book'
import { getCategories } from '@/api/category'
import { useBooklistStore } from '@/stores/booklist'
import { addBatchToCartGuarded } from '@/utils/cart-action'
import { formatPrice, formatRating } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const booklistStore = useBooklistStore()

const categories = ref([])
const results = ref([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const selectedBooks = ref([])
const filterExpanded = ref(true)

const filters = reactive({
  keyword: '',
  categoryId: null,
  minPrice: null,
  maxPrice: null,
  author: '',
  publisher: '',
  publishYear: null,
  minRating: null
})

async function loadCategories() {
  try {
    categories.value = await getCategories()
  } catch (e) {
    /* 静默 */
  }
}

async function doSearch() {
  loading.value = true
  try {
    const params = {
      keyword: filters.keyword || undefined,
      categoryId: filters.categoryId || undefined,
      minPrice: filters.minPrice || undefined,
      maxPrice: filters.maxPrice || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    const res = await pageBooks(params)
    let list = res.list || []
    if (filters.author) list = list.filter((b) => b.author?.includes(filters.author))
    if (filters.publisher) list = list.filter((b) => b.publisher?.includes(filters.publisher))
    if (filters.minRating) list = list.filter((b) => b.avgRating !== null && b.avgRating >= filters.minRating)
    results.value = list
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 提交检索：条件变了必须回到第 1 页。
 *
 * <p>此前 `search()` 自己把 `pageNum` 重置成 1，而分页器又直接绑到 `search`，
 * 于是点第 2 页 → 页码先被重置为 1 再请求 → 永远翻不动。拆成两个入口就对了。
 */
function submitSearch() {
  pageNum.value = 1
  doSearch()
}

/** 翻页：保留分页器已设好的页码 */
function handlePageChange() {
  doSearch()
}

function reset() {
  Object.assign(filters, {
    keyword: '', categoryId: null, minPrice: null, maxPrice: null,
    author: '', publisher: '', publishYear: null, minRating: null
  })
  submitSearch()
}

/** 作者 / 出版社 / 评分是后端不支持的筛选维度，只能对当前页结果做本地过滤 */
const hasPageLocalFilter = computed(
  () => !!(filters.author || filters.publisher || filters.minRating)
)

function handleSelectionChange(rows) {
  selectedBooks.value = rows
}

function batchAddToCart() {
  return addBatchToCartGuarded(selectedBooks.value, { router })
}

async function saveToBooklist() {
  if (selectedBooks.value.length === 0) {
    ElMessage.warning('请先选择图书')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入书单名称', '保存为书单', {
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const list = booklistStore.createBooklist(value)
    booklistStore.addBooksToList(list.id, selectedBooks.value)
    ElMessage.success(`已保存 ${selectedBooks.value.length} 本书到书单「${value}」`)
    router.push('/booklists/' + list.id)
  } catch (e) {
    /* 取消 */
  }
}

function goDetail(id) {
  router.push('/books/' + id)
}

onMounted(() => {
  loadCategories()
  submitSearch()
})
</script>

<template>
  <div class="page-container advanced-search">
    <h2 class="page-title">高级检索</h2>

    <!-- 筛选面板（可折叠） -->
    <div class="filter-card card">
      <div class="filter-header" @click="filterExpanded = !filterExpanded">
        <span class="filter-header-title">
          <el-icon><Filter /></el-icon>
          筛选条件
        </span>
        <el-icon class="filter-toggle" :class="{ expanded: filterExpanded }">
          <ArrowDown />
        </el-icon>
      </div>
      <transition name="slide">
        <el-form v-show="filterExpanded" :model="filters" label-width="90px" class="filter-form">
          <div class="filter-row">
            <el-form-item label="关键词">
              <el-input v-model="filters.keyword" placeholder="书名/作者" style="width: 220px" clearable />
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="filters.categoryId" placeholder="全部分类" clearable style="width: 180px">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="最低评分">
              <el-select v-model="filters.minRating" placeholder="不限" clearable style="width: 120px">
                <el-option label="1星以上" :value="1" />
                <el-option label="2星以上" :value="2" />
                <el-option label="3星以上" :value="3" />
                <el-option label="4星以上" :value="4" />
                <el-option label="4.5星以上" :value="4.5" />
              </el-select>
            </el-form-item>
          </div>
          <div class="filter-row">
            <el-form-item label="价格区间">
              <el-input-number v-model="filters.minPrice" :min="0" placeholder="最低" size="default" controls-position="right" style="width: 110px" />
              <span style="margin: 0 6px">~</span>
              <el-input-number v-model="filters.maxPrice" :min="0" placeholder="最高" size="default" controls-position="right" style="width: 110px" />
            </el-form-item>
            <el-form-item label="作者">
              <el-input v-model="filters.author" placeholder="作者名" style="width: 160px" clearable />
            </el-form-item>
            <el-form-item label="出版社">
              <el-input v-model="filters.publisher" placeholder="出版社" style="width: 180px" clearable />
            </el-form-item>
          </div>
          <div class="filter-actions">
            <el-button type="primary" @click="submitSearch">
              <el-icon><Search /></el-icon> 检索
            </el-button>
            <el-button @click="reset">重置</el-button>
          </div>
        </el-form>
      </transition>
    </div>

    <!-- 结果操作栏 -->
    <div class="result-toolbar card">
      <span>
        共找到 <b>{{ total }}</b> 本图书
        <el-tooltip
          v-if="hasPageLocalFilter"
          content="作者 / 出版社 / 评分由后端接口不支持，此处只在当前页结果内过滤"
          placement="top"
        >
          <el-tag size="small" type="warning" effect="plain" class="local-filter-tag">
            本页内筛选
          </el-tag>
        </el-tooltip>
      </span>
      <div class="result-tool-right">
        <span class="selected-count" v-if="selectedBooks.length > 0">已选 <b>{{ selectedBooks.length }}</b> 本</span>
        <el-button type="primary" size="small" @click="batchAddToCart" :disabled="selectedBooks.length === 0">
          <el-icon><ShoppingCart /></el-icon> 批量加购
        </el-button>
        <el-button size="small" @click="saveToBooklist" :disabled="selectedBooks.length === 0">
          <el-icon><Collection /></el-icon> 保存为书单（{{ selectedBooks.length }}）
        </el-button>
      </div>
    </div>

    <!-- 结果表格 -->
    <div class="result-card card">
      <el-table
        :data="results"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        style="width: 100%"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <img :src="bookCover(row) || placeholderCover(row.title)" class="table-cover" @click="goDetail(row.id)" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.id)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="publisher" label="出版社" width="160" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="100">
          <template #default="{ row }">{{ formatRating(row.avgRating) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
      </el-table>

      <div class="pagination" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="handlePageChange"
        />
      </div>

      <el-empty v-if="results.length === 0 && !loading" description="未找到符合条件的图书" />
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 16px;
}

.filter-card {
  padding: 12px 20px;
  margin-bottom: 16px;
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.filter-header-title {
  font-weight: 600;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-toggle {
  transition: transform 0.25s;
  color: var(--text-sub);
}

.filter-toggle.expanded {
  transform: rotate(180deg);
}

.filter-form {
  margin-top: 16px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 4px;
}

.filter-actions {
  display: flex;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-light);
  margin-top: 12px;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.25s;
  overflow: hidden;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.result-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  margin-bottom: 12px;
}

.result-tool-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selected-count {
  font-size: 13px;
  color: var(--text-sub);
}

.local-filter-tag {
  margin-left: 6px;
}

.selected-count b {
  color: var(--brand);
  font-size: 15px;
}

.table-cover {
  width: 50px;
  height: 66px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
}

.price {
  color: var(--brand);
  font-weight: 600;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}
</style>
