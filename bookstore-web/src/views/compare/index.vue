<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getBook } from '@/api/book'
import { useCompareStore } from '@/stores/compare'
import { addToCartGuarded } from '@/utils/cart-action'
import { formatPrice, formatRating } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'

const router = useRouter()
const compareStore = useCompareStore()

const books = ref([])
const loading = ref(false)

async function loadBooks() {
  const ids = compareStore.ids
  if (ids.length === 0) {
    books.value = []
    return
  }
  loading.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => getBook(id)))
    // 有图书查不到（下架/删除）时，顺手把它从对比栏摘掉，避免对比页一直少一格
    books.value = results.filter((r) => r.status === 'fulfilled').map((r) => r.value)
    const loadedIds = new Set(books.value.map((b) => b.id))
    ids.filter((id) => !loadedIds.has(id)).forEach((id) => compareStore.remove(id))
  } finally {
    loading.value = false
  }
}

function removeBook(book) {
  compareStore.remove(book.id)
  books.value = books.value.filter((b) => b.id !== book.id)
}

function addToCart(book) {
  return addToCartGuarded(book, { router })
}

// 判断某字段是否有差异，用于高亮
function hasDiff(prop) {
  const vals = books.value.map((b) => String(b[prop] ?? ''))
  return new Set(vals).size > 1
}

const compareFields = [
  { prop: 'price', label: '价格', format: (v) => formatPrice(v) },
  { prop: 'author', label: '作者', format: (v) => v || '-' },
  { prop: 'publisher', label: '出版社', format: (v) => v || '-' },
  { prop: 'stock', label: '库存', format: (v) => v + ' 件' },
  { prop: 'sales', label: '销量', format: (v) => v || 0 },
  { prop: 'avgRating', label: '评分', format: (v) => formatRating(v) },
  { prop: 'reviewCount', label: '评价数', format: (v) => v || 0 }
]

onMounted(loadBooks)
</script>

<template>
  <div class="page-container compare-page">
    <h2 class="page-title">图书对比（{{ books.length }}/4）</h2>

    <div v-if="books.length < 2" class="empty-state card">
      <el-result icon="info" title="至少选择 2 本图书进行对比" sub-title="请在图书列表或详情页将图书加入对比栏">
        <template #extra>
          <el-button type="primary" @click="router.push('/books')">去选书</el-button>
        </template>
      </el-result>
    </div>

    <div v-else v-loading="loading" class="compare-table card">
      <table class="compare-tbl">
        <thead>
          <tr>
            <th class="field-col">对比项</th>
            <th v-for="book in books" :key="book.id" class="book-col">
              <div class="col-header">
                <el-button link type="danger" size="small" @click="removeBook(book)" class="remove-btn">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </th>
          </tr>
        </thead>
        <tbody>
          <!-- 封面 + 书名 -->
          <tr class="cover-row">
            <td class="field-col">图书</td>
            <td v-for="book in books" :key="book.id">
              <div class="book-col-content" @click="router.push('/books/' + book.id)">
                <img :src="bookCover(book) || placeholderCover(book.title)" class="comp-cover" />
                <h4 class="comp-title text-ellipsis-2">{{ book.title }}</h4>
              </div>
            </td>
          </tr>
          <!-- 加购按钮 -->
          <tr>
            <td class="field-col">操作</td>
            <td v-for="book in books" :key="book.id">
              <el-button type="primary" size="small" @click="addToCart(book)">加入购物车</el-button>
              <el-button size="small" @click="router.push('/books/' + book.id)">查看详情</el-button>
            </td>
          </tr>
          <!-- 各字段对比 -->
          <tr v-for="field in compareFields" :key="field.prop">
            <td class="field-col">{{ field.label }}</td>
            <td
              v-for="book in books"
              :key="book.id"
              :class="{ diff: hasDiff(field.prop) }"
            >
              {{ field.format(book[field.prop]) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 16px;
}

.empty-state {
  padding: 40px;
}

.compare-table {
  overflow-x: auto;
}

.compare-tbl {
  width: 100%;
  border-collapse: collapse;
}

.field-col {
  width: 100px;
  background: var(--bg-fill);
  font-weight: 600;
  color: var(--text-regular);
  text-align: center;
  padding: 14px 8px;
  border: 1px solid var(--border-light);
  white-space: nowrap;
}

.book-col {
  text-align: center;
  padding: 12px;
  border: 1px solid var(--border-light);
  vertical-align: middle;
}

.col-header {
  display: flex;
  justify-content: flex-end;
}

.remove-btn {
  margin: 0;
}

.cover-row .book-col {
  vertical-align: top;
}

.book-col-content {
  cursor: pointer;
  padding: 0 8px;
}

.comp-cover {
  width: 100px;
  height: 134px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  margin: 0 auto 10px;
  box-shadow: var(--shadow-sm);
}

.comp-title {
  font-size: 14px;
  line-height: 1.4;
  text-align: center;
}

.diff {
  background: var(--brand-weak);
  color: var(--brand);
  font-weight: 600;
}

tr td {
  padding: 12px 8px;
  border: 1px solid var(--border-light);
  text-align: center;
}
</style>
