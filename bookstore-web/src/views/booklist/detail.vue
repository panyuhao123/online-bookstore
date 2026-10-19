<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBooklistStore } from '@/stores/booklist'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { formatPrice } from '@/utils/format'
import BookCard from '@/components/BookCard.vue'
import { exportToExcel } from '@/utils/excel'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const booklistStore = useBooklistStore()
const cartStore = useCartStore()
const userStore = useUserStore()

const viewMode = ref('card')
const selectedBooks = ref([])

const booklist = computed(() => booklistStore.getBooklist(route.params.id))

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
    router.push('/login')
    return
  }
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedBooks.value.length} 本图书加入购物车？`, '提示')
    for (const book of selectedBooks.value) {
      await cartStore.add(book.id, 1)
    }
    ElMessage.success('已加入购物车')
  } catch (e) {
    /* 取消 */
  }
}

function exportList() {
  if (!booklist.value || booklist.value.books.length === 0) {
    ElMessage.warning('书单为空')
    return
  }
  const columns = [
    { prop: 'title', label: '书名', width: 30 },
    { prop: 'author', label: '作者', width: 15 },
    { prop: 'publisher', label: '出版社', width: 18 },
    { prop: 'price', label: '价格', width: 10 }
  ]
  exportToExcel(booklist.value.books, columns, '书单_' + booklist.value.name)
}

function removeBook(bookId) {
  booklistStore.removeBookFromList(route.params.id, bookId)
  ElMessage.success('已移除')
}

onMounted(() => {
  if (!booklist.value) {
    ElMessage.warning('书单不存在')
    router.push('/booklists')
  }
})
</script>

<template>
  <div class="page-container booklist-detail" v-if="booklist">
    <div class="detail-header card">
      <div>
        <h2>{{ booklist.name }}</h2>
        <p class="desc">{{ booklist.description || '暂无描述' }}</p>
        <p class="meta">{{ booklist.books.length }} 本书</p>
      </div>
      <div class="actions">
        <el-button-group>
          <el-button :type="viewMode === 'card' ? 'primary' : 'default'" @click="viewMode = 'card'">卡片</el-button>
          <el-button :type="viewMode === 'table' ? 'primary' : 'default'" @click="viewMode = 'table'">表格</el-button>
        </el-button-group>
        <el-button @click="exportList">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
        <el-button type="primary" @click="batchAddToCart" :disabled="selectedBooks.length === 0 && viewMode === 'table'">
          批量加购
        </el-button>
      </div>
    </div>

    <!-- 卡片视图（复用 BookCard，自动有 hover 浮层快捷操作） -->
    <div v-if="viewMode === 'card' && booklist.books.length > 0" class="book-grid">
      <BookCard
        v-for="book in booklist.books"
        :key="book.id"
        :book="book"
      />
    </div>

    <!-- 表格视图 -->
    <el-table
      v-if="viewMode === 'table'"
      :data="booklist.books"
      @selection-change="handleSelectionChange"
      style="width: 100%"
    >
      <el-table-column type="selection" width="48" />
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
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="cartStore.add(row.id, 1)">加购</el-button>
          <el-button size="small" type="danger" link @click="removeBook(row.id)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="booklist.books.length === 0" description="书单暂无图书" />
  </div>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px;
  margin-bottom: 16px;
}

.detail-header h2 {
  font-size: 22px;
  margin-bottom: 8px;
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
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.price {
  color: var(--brand);
  font-weight: 600;
}
</style>
