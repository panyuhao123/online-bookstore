<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBooklistStore } from '@/stores/booklist'
import { addBatchToCartGuarded, addToCartGuarded } from '@/utils/cart-action'
import { formatPrice } from '@/utils/format'
import BookCard from '@/components/BookCard.vue'
import { exportToExcel } from '@/utils/excel'
import { ElMessage } from 'element-plus'
import { isOfficialId, getOfficialBooklist, fetchOfficialBooks } from '@/data/official-booklists'

const route = useRoute()
const router = useRouter()
const booklistStore = useBooklistStore()

const viewMode = ref('card')
const selectedBooks = ref([])
const loading = ref(true)
const notFound = ref(false)

/** 官方书单的图书来自接口解析，需要用 ref 装 */
const officialBooks = ref([])

const listId = computed(() => route.params.id)
const isOfficial = computed(() => isOfficialId(listId.value))
const officialDef = computed(() => (isOfficial.value ? getOfficialBooklist(listId.value) : null))

/** 个人书单直接用 store 里的数组，这样增删图书能自动反映到界面 */
const localList = computed(() => (isOfficial.value ? null : booklistStore.getBooklist(listId.value)))

const books = computed(() => (isOfficial.value ? officialBooks.value : localList.value?.books || []))

const header = computed(() => {
  if (isOfficial.value) {
    const def = officialDef.value
    return {
      name: def?.name || '书单',
      description: def?.description || '',
      tag: def?.tag || '',
      count: books.value.length
    }
  }
  const list = localList.value
  return {
    name: list?.name || '',
    description: list?.description || '暂无描述',
    tag: '',
    count: books.value.length
  }
})

/**
 * 统一加载入口。
 *
 * <p>官方书单是「规则」（分类名 + 排序，或人工挑的书名），要经图书接口解析成真实图书；
 * 个人书单直接读 localStorage。两者在这里分流，模板只认 `books`。
 */
async function load() {
  loading.value = true
  notFound.value = false
  try {
    if (isOfficial.value) {
      if (!officialDef.value) {
        notFound.value = true
        return
      }
      officialBooks.value = await fetchOfficialBooks(officialDef.value)
    } else if (!localList.value) {
      notFound.value = true
    }
  } catch (e) {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows) {
  selectedBooks.value = rows
}

function batchAddToCart() {
  return addBatchToCartGuarded(selectedBooks.value, { router })
}

function addOne(book) {
  return addToCartGuarded(book, { router })
}

function exportList() {
  if (books.value.length === 0) {
    ElMessage.warning('书单为空，没有可导出的图书')
    return
  }
  const columns = [
    { prop: 'title', label: '书名', width: 30 },
    { prop: 'author', label: '作者', width: 15 },
    { prop: 'publisher', label: '出版社', width: 18 },
    { prop: 'price', label: '价格', width: 10 }
  ]
  exportToExcel(books.value, columns, '书单_' + header.value.name)
}

function removeBook(bookId) {
  booklistStore.removeBookFromList(listId.value, bookId)
  ElMessage.success('已移除')
}

onMounted(load)
watch(listId, load)
</script>

<template>
  <div class="page-container booklist-detail" v-loading="loading">
    <el-result
      v-if="notFound && !loading"
      icon="warning"
      title="书单不存在"
      sub-title="该书单可能已被删除，或链接不正确"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/booklists')">返回书单列表</el-button>
      </template>
    </el-result>

    <template v-else-if="!loading">
      <div class="detail-header card">
        <div class="header-info">
          <h2>
            {{ header.name }}
            <el-tag v-if="header.tag" size="small" type="danger" effect="plain" class="official-tag">
              {{ isOfficial ? '官方' : '我的' }}
            </el-tag>
          </h2>
          <p class="desc">{{ header.description }}</p>
          <p class="meta">共 {{ header.count }} 本书</p>
        </div>
        <div class="actions">
          <el-button-group>
            <el-button :type="viewMode === 'card' ? 'primary' : 'default'" @click="viewMode = 'card'">
              卡片
            </el-button>
            <el-button :type="viewMode === 'table' ? 'primary' : 'default'" @click="viewMode = 'table'">
              表格
            </el-button>
          </el-button-group>
          <el-button :disabled="books.length === 0" @click="exportList">
            <el-icon><Download /></el-icon> 导出 Excel
          </el-button>
          <el-button
            type="primary"
            :disabled="viewMode === 'table' && selectedBooks.length === 0"
            @click="batchAddToCart"
          >
            批量加购
          </el-button>
        </div>
      </div>

      <!-- 卡片视图（复用 BookCard，自带 hover 快捷加购/对比） -->
      <div v-if="viewMode === 'card' && books.length > 0" class="book-grid">
        <BookCard v-for="book in books" :key="book.id" :book="book" />
      </div>

      <!-- 表格视图 -->
      <el-table
        v-if="viewMode === 'table' && books.length > 0"
        :data="books"
        @selection-change="handleSelectionChange"
        style="width: 100%"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <img :src="row.coverUrl" class="table-cover" @click="router.push('/books/' + row.id)" />
          </template>
        </el-table-column>
        <el-table-column label="书名" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push('/books/' + row.id)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="publisher" label="出版社" width="160" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ 'sold-out': row.stock <= 0 }">{{ row.stock <= 0 ? '售罄' : row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link :disabled="row.stock <= 0" @click="addOne(row)">
              加购
            </el-button>
            <!-- 官方书单是只读的，不提供「移除」 -->
            <el-button v-if="!isOfficial" size="small" type="danger" link @click="removeBook(row.id)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="books.length === 0" description="书单暂无图书">
        <el-button v-if="!isOfficial" type="primary" @click="router.push('/books')">去挑书</el-button>
      </el-empty>

      <p v-if="!isOfficial && books.length > 0" class="tip">
        提示：在图书列表勾选图书后可「批量加书单」，也可以在详情页把图书加入本书单。
      </p>
    </template>
  </div>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 20px 24px;
  margin-bottom: 16px;
}

.header-info h2 {
  font-size: 22px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.official-tag {
  font-size: 12px;
  font-weight: 400;
}

.desc {
  color: var(--text-regular);
  margin-bottom: 6px;
}

.meta {
  font-size: 13px;
  color: var(--text-sub);
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
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

.price {
  color: var(--brand);
  font-weight: 600;
}

.sold-out {
  color: var(--text-sub);
}

.tip {
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-sub);
  text-align: center;
}
</style>
