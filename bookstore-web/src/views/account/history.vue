<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useHistoryStore } from '@/stores/history'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { addBooksToPickedBooklist } from '@/utils/booklist-action'
import { formatPrice, formatDateTime } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const historyStore = useHistoryStore()
const userStore = useUserStore()

const selectedRows = ref([])
const pageNum = ref(1)
const pageSize = ref(20)

const isLogin = computed(() => userStore.isLogin)
const rows = computed(() => historyStore.displayList)

async function load() {
  if (isLogin.value) {
    await historyStore.fetchPage({ pageNum: pageNum.value, pageSize: pageSize.value })
  }
}

function handleSelectionChange(list) {
  selectedRows.value = list
}

/** 统一取「选中行的图书 id」—— 本地记录字段是 bookId，后端 VO 也是 bookId */
function selectedBookIds() {
  return selectedRows.value.map((r) => r.bookId)
}

const addingCart = ref(false)

/** 批量加入购物车（逐本，与其它页面共用同一套加购口径） */
async function batchAddCart() {
  const ids = selectedBookIds()
  if (ids.length === 0) {
    ElMessage.warning('请先选择记录')
    return
  }
  if (!isLogin.value) {
    ElMessage.warning('请先登录')
    return
  }
  addingCart.value = true
  try {
    const cartStore = useCartStore()
    for (const bookId of ids) {
      await cartStore.add(bookId, 1)
    }
    ElMessage.success(`已加入购物车 ${ids.length} 本`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    addingCart.value = false
  }
}

/** 批量加入书单：选择目标书单后走后端批量加书（与其它页面共用同一套口径） */
async function batchAddToBooklist() {
  const ids = selectedBookIds()
  if (ids.length === 0) {
    ElMessage.warning('请先选择记录')
    return
  }
  if (!isLogin.value) {
    ElMessage.warning('请先登录')
    return
  }
  await addBooksToPickedBooklist(ids.map((bookId) => ({ bookId })), { router })
}

async function removeOne(row) {
  try {
    await historyStore.removeOne(row.bookId)
    ElMessage.success('已删除')
    if (isLogin.value) await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function batchRemove() {
  const rowsSelected = [...selectedRows.value]
  if (rowsSelected.length === 0) {
    ElMessage.warning('请先选择记录')
    return
  }
  ElMessageBox.confirm(`确认删除选中的 ${rowsSelected.length} 条浏览记录？`, '提示', {
    type: 'warning'
  })
    .then(async () => {
      for (const r of rowsSelected) {
        await historyStore.removeOne(r.bookId)
      }
      ElMessage.success('已删除选中记录')
      if (isLogin.value) await load()
    })
    .catch(() => {})
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确认清空全部浏览历史？此操作不可恢复。', '提示', { type: 'warning' })
    await historyStore.clearAll()
    ElMessage.success('已清空')
    if (isLogin.value) await load()
  } catch (e) {
    /* 取消 */
  }
}

watch(pageNum, load)
watch(isLogin, (v) => {
  if (v) load()
})

onMounted(load)
</script>

<template>
  <div class="page-container history-page">
    <div class="page-header">
      <div>
        <h2>浏览历史</h2>
        <p class="page-sub">
          <template v-if="isLogin">记录已同步到账号，换设备也能看到</template>
          <template v-else>当前记录只存在本浏览器，登录后会一并同步到账号</template>
        </p>
      </div>
      <div class="actions">
        <el-button @click="batchRemove" :disabled="selectedRows.length === 0">批量删除</el-button>
        <el-button
          type="primary"
          :loading="addingCart"
          @click="batchAddCart"
          :disabled="selectedRows.length === 0"
        >
          批量加购
        </el-button>
        <el-button @click="batchAddToBooklist" :disabled="selectedRows.length === 0">加入书单</el-button>
        <el-button type="danger" plain @click="clearAll" :disabled="rows.length === 0">清空历史</el-button>
      </div>
    </div>

    <!-- 未登录：本地记录的提示条 -->
    <el-alert
      v-if="!isLogin && rows.length > 0"
      class="login-tip"
      type="info"
      show-icon
      :closable="false"
    >
      <template #title>
        这些浏览记录还没有同步到账号
        <el-button link type="primary" @click="router.push('/login')">去登录同步</el-button>
      </template>
    </el-alert>

    <div class="card" v-loading="historyStore.loading" v-if="rows.length > 0">
      <el-table :data="rows" @selection-change="handleSelectionChange" style="width: 100%">
        <el-table-column type="selection" width="48" />
        <el-table-column label="图书" min-width="300">
          <template #default="{ row }">
            <div class="book-cell" @click="router.push('/books/' + row.bookId)">
              <img :src="bookCover(row) || placeholderCover(row.title)" class="table-cover" />
              <div class="book-info">
                <p class="book-title text-ellipsis-2">{{ row.title }}</p>
                <p class="book-author">{{ row.author || '佚名' }}</p>
                <!-- 后端 VO 会带库存与上架状态：售罄 / 已下架要标出来，
                     否则用户点进详情才发现买不了 -->
                <p class="stock-line">
                  <el-tag v-if="row.status === 0" size="small" type="info">已下架</el-tag>
                  <el-tag v-else-if="row.stock !== undefined && row.stock <= 0" size="small" type="warning">
                    售罄
                  </el-tag>
                </p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110" align="center">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="浏览时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.viewTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="removeOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="isLogin && historyStore.total > pageSize" class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="historyStore.total"
          layout="prev, pager, next, total"
          background
        />
      </div>
    </div>

    <el-empty v-else-if="!historyStore.loading" description="暂无浏览历史">
      <el-button type="primary" @click="router.push('/books')">去逛逛</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 12px;
}

.page-header h2 {
  font-size: 20px;
  margin: 0 0 4px;
}

.page-sub {
  font-size: 13px;
  color: var(--text-sub);
}

.actions {
  display: flex;
  gap: 8px;
}

.login-tip {
  margin-bottom: 12px;
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

.stock-line {
  margin-top: 4px;
  min-height: 20px;
}

.price {
  color: var(--brand);
  font-weight: 600;
}

.pager {
  display: flex;
  justify-content: center;
  padding: 16px 0 4px;
}
</style>
