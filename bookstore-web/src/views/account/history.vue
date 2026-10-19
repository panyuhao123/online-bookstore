<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHistoryStore } from '@/stores/history'
import { useCartStore } from '@/stores/cart'
import { useBooklistStore } from '@/stores/booklist'
import { useUserStore } from '@/stores/user'
import { formatPrice, formatDateTime } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const historyStore = useHistoryStore()
const cartStore = useCartStore()
const booklistStore = useBooklistStore()
const userStore = useUserStore()

const selectedRows = ref([])

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function batchAddToCart() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择记录')
    return
  }
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  for (const r of selectedRows.value) {
    await cartStore.add(r.bookId, 1)
  }
  ElMessage.success('已加入购物车')
}

async function batchAddToBooklist() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择记录')
    return
  }
  const lists = booklistStore.booklists
  if (lists.length === 0) {
    ElMessage.info('暂无书单，请先创建')
    router.push('/booklists/new')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('选择书单', '加入书单', {
      inputType: 'select',
      inputOptions: lists.map((l) => ({ value: l.id, label: l.name }))
    })
    const books = selectedRows.value.map((r) => ({
      id: r.bookId, title: r.title, coverUrl: r.coverUrl, price: r.price, author: r.author
    }))
    booklistStore.addBooksToList(Number(value), books)
    ElMessage.success('已加入书单')
  } catch (e) {
    /* 取消 */
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确认清空全部浏览历史？', '提示', { type: 'warning' })
    historyStore.clearAll()
    ElMessage.success('已清空')
  } catch (e) {
    /* 取消 */
  }
}

function batchRemove() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择记录')
    return
  }
  const ids = selectedRows.value.map((r) => r.bookId)
  historyStore.removeRecords(ids)
  ElMessage.success('已删除选中记录')
}
</script>

<template>
  <div class="page-container history-page">
    <div class="page-header">
      <h2>浏览历史</h2>
      <div class="actions">
        <el-button @click="batchRemove" :disabled="selectedRows.length === 0">批量删除</el-button>
        <el-button type="primary" @click="batchAddToCart" :disabled="selectedRows.length === 0">批量加购</el-button>
        <el-button @click="batchAddToBooklist" :disabled="selectedRows.length === 0">加入书单</el-button>
        <el-button type="danger" @click="clearAll">清空历史</el-button>
      </div>
    </div>

    <div class="card" v-if="historyStore.list.length > 0">
      <el-table :data="historyStore.list" @selection-change="handleSelectionChange" style="width: 100%">
        <el-table-column type="selection" width="48" />
        <el-table-column label="图书" min-width="300">
          <template #default="{ row }">
            <div class="book-cell" @click="router.push('/books/' + row.bookId)">
              <img :src="bookCover(row) || placeholderCover(row.title)" class="table-cover" />
              <div class="book-info">
                <p class="book-title text-ellipsis-2">{{ row.title }}</p>
                <p class="book-author">{{ row.author }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="100" align="center">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="浏览时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.viewTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="cartStore.add(row.bookId, 1)">加购</el-button>
            <el-button size="small" link @click="historyStore.removeRecords([row.bookId])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-empty v-else description="暂无浏览历史" />
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 20px;
}

.actions {
  display: flex;
  gap: 8px;
}

.book-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
}

.table-cover {
  width: 50px;
  height: 66px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.book-info {
  flex: 1;
}

.book-title {
  font-size: 14px;
  line-height: 1.4;
  margin-bottom: 4px;
}

.book-author {
  font-size: 12px;
  color: var(--text-sub);
}

.price {
  color: var(--brand);
  font-weight: 600;
}
</style>
