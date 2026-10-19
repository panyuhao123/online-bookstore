<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageBooks } from '@/api/book'
import { useBooklistStore } from '@/stores/booklist'
import { formatPrice } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { exportToExcel } from '@/utils/excel'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const booklistStore = useBooklistStore()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  name: '',
  description: '',
  isPublic: 0
})

// 可选图书池
const poolBooks = ref([])
const poolLoading = ref(false)
const poolPage = ref(1)
const poolKeyword = ref('')
const poolTotal = ref(0)
const POOL_SIZE = 12

// 已选图书
const selectedBooks = ref([])
/** 编辑态下「打开页面时书单里已有的书」——保存时据此算增量 */
const originalBookIds = ref([])

// 拖拽
const dragIndex = ref(-1)

async function loadPool() {
  poolLoading.value = true
  try {
    const res = await pageBooks({
      keyword: poolKeyword.value || undefined,
      pageNum: poolPage.value,
      pageSize: POOL_SIZE
    })
    poolBooks.value = res.list || []
    poolTotal.value = res.total || 0
  } finally {
    poolLoading.value = false
  }
}

function searchPool() {
  poolPage.value = 1
  loadPool()
}

async function loadExisting() {
  loading.value = true
  try {
    const detail = await booklistStore.fetchDetail(route.params.id)
    if (!detail?.mine) {
      ElMessage.error('只能编辑自己创建的书单')
      router.replace('/booklists/' + route.params.id)
      return
    }
    form.name = detail.name || ''
    form.description = detail.description || ''
    // 必须回填公开状态：后端 BookListRequest 的 isPublic 默认值是 1，
    // 不传就会把用户设的「仅自己可见」改成公开 —— 一次静默的设置篡改。
    form.isPublic = detail.isPublic ?? 0
    selectedBooks.value = [...(detail.books || [])]
    originalBookIds.value = selectedBooks.value.map((b) => b.id)
  } catch (e) {
    ElMessage.error('书单加载失败')
  } finally {
    loading.value = false
  }
}

function addBook(book) {
  if (selectedBooks.value.some((b) => b.id === book.id)) {
    ElMessage.info('已在书单中')
    return
  }
  if (selectedBooks.value.length >= 200) {
    ElMessage.warning('单个书单最多 200 本图书')
    return
  }
  selectedBooks.value.push({ ...book })
}

function removeBook(idx) {
  selectedBooks.value.splice(idx, 1)
}

function onDragStart(idx) {
  dragIndex.value = idx
}

function onDrop(targetIdx) {
  if (dragIndex.value === -1 || dragIndex.value === targetIdx) return
  const item = selectedBooks.value.splice(dragIndex.value, 1)[0]
  selectedBooks.value.splice(targetIdx, 0, item)
  dragIndex.value = -1
}

/**
 * 保存。
 *
 * <p>编辑态**不算「全删全加」**：只提交真正变化的条目（新增的 addBooks、移出的 removeBook）。
 * 全删全加看起来更"简单可靠"，实际会把用户没动过的书也写一遍，书单条目顺序被打乱，
 * 而且一次网络抖动就可能只删没加完 —— 用户看到的是"我什么都没改，书少了半本"。
 */
async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入书单名称')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await booklistStore.update(route.params.id, {
        name: form.name.trim(),
        description: form.description || '',
        isPublic: form.isPublic
      })

      const currentIds = selectedBooks.value.map((b) => b.id)
      const removed = originalBookIds.value.filter((id) => !currentIds.includes(id))
      const added = currentIds.filter((id) => !originalBookIds.value.includes(id))

      // 先删后加：避免中途短暂超出书单容量上限
      for (const bookId of removed) {
        await booklistStore.removeBook(route.params.id, bookId)
      }
      if (added.length > 0) {
        await booklistStore.addBooks(route.params.id, added)
      }
      ElMessage.success('书单已更新')
      router.push('/booklists/' + route.params.id)
    } else {
      const created = await booklistStore.create({
        name: form.name.trim(),
        description: form.description || '',
        isPublic: form.isPublic
      })
      if (selectedBooks.value.length > 0) {
        await booklistStore.addBooks(
          created.id,
          selectedBooks.value.map((b) => b.id)
        )
      }
      ElMessage.success('书单已创建')
      router.push('/booklists/' + created.id)
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

function exportList() {
  if (selectedBooks.value.length === 0) {
    ElMessage.warning('书单为空')
    return
  }
  const columns = [
    { prop: 'title', label: '书名', width: 30 },
    { prop: 'author', label: '作者', width: 15 },
    { prop: 'publisher', label: '出版社', width: 18 },
    { prop: 'price', label: '价格', width: 10 }
  ]
  exportToExcel(selectedBooks.value, columns, '书单_' + (form.name || '未命名'))
}

onMounted(async () => {
  if (isEdit.value) await loadExisting()
  await loadPool()
})
</script>

<template>
  <div class="page-container booklist-edit" v-loading="loading">
    <div class="edit-header card">
      <h2>{{ isEdit ? '编辑书单' : '新建书单' }}</h2>
      <div class="meta-form">
        <el-input v-model="form.name" placeholder="书单名称（必填）" style="width: 260px" size="large" maxlength="50" />
        <el-input
          v-model="form.description"
          placeholder="书单描述（可选）"
          style="width: 360px"
          size="large"
          maxlength="200"
        />
        <el-switch
          v-model="form.isPublic"
          :active-value="1"
          :inactive-value="0"
          active-text="公开到书单广场"
          inline-prompt
        />
        <el-button type="primary" size="large" :loading="saving" @click="save">保存书单</el-button>
        <el-button size="large" @click="exportList">导出 Excel</el-button>
      </div>
      <p class="form-tip">
        不公开的书单只有你自己能看到；公开后会出现在书单广场，其他书友也能浏览和收藏。
      </p>
    </div>

    <div class="edit-body">
      <!-- 左：可选图书池 -->
      <div class="pool-panel card">
        <div class="panel-header">
          <h3>可选图书</h3>
          <el-input
            v-model="poolKeyword"
            placeholder="搜索书名或作者"
            size="small"
            style="width: 200px"
            clearable
            @keyup.enter="searchPool"
            @clear="searchPool"
          />
        </div>
        <div class="pool-grid" v-loading="poolLoading">
          <div v-for="book in poolBooks" :key="book.id" class="pool-item">
            <img :src="bookCover(book) || placeholderCover(book.title)" class="pool-cover" />
            <div class="pool-info">
              <p class="pool-title text-ellipsis-2">{{ book.title }}</p>
              <p class="pool-author text-ellipsis">{{ book.author }}</p>
              <span class="pool-price">{{ formatPrice(book.price) }}</span>
            </div>
            <el-button
              type="primary"
              size="small"
              :disabled="selectedBooks.some((b) => b.id === book.id)"
              @click="addBook(book)"
            >
              {{ selectedBooks.some((b) => b.id === book.id) ? '已添加' : '添加' }}
            </el-button>
          </div>
          <el-empty v-if="!poolLoading && poolBooks.length === 0" description="没有匹配的图书" :image-size="70" />
        </div>
        <div class="pool-pagination" v-if="poolTotal > POOL_SIZE">
          <el-pagination
            v-model:current-page="poolPage"
            :page-size="POOL_SIZE"
            :total="poolTotal"
            layout="prev, pager, next"
            background
            @current-change="loadPool"
          />
        </div>
      </div>

      <!-- 右：已选图书 -->
      <div class="selected-panel card">
        <div class="panel-header">
          <h3>已选图书（{{ selectedBooks.length }}/200）</h3>
          <span class="drag-tip">拖拽可调整顺序</span>
        </div>
        <div v-if="selectedBooks.length === 0" class="empty-selected">
          <el-empty description="请从左侧添加图书" :image-size="80" />
        </div>
        <div v-else class="selected-list">
          <div
            v-for="(book, idx) in selectedBooks"
            :key="book.id"
            class="selected-item"
            draggable="true"
            @dragstart="onDragStart(idx)"
            @dragover.prevent
            @drop="onDrop(idx)"
          >
            <span class="order-no">{{ idx + 1 }}</span>
            <img :src="bookCover(book) || placeholderCover(book.title)" class="sel-cover" />
            <div class="sel-info">
              <p class="sel-title text-ellipsis">{{ book.title }}</p>
              <p class="sel-author">{{ book.author }}</p>
            </div>
            <span class="sel-price">{{ formatPrice(book.price) }}</span>
            <el-button type="danger" link size="small" @click="removeBook(idx)">移除</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.edit-header {
  padding: 20px 24px;
  margin-bottom: 16px;
}

.edit-header h2 {
  font-size: 20px;
  margin-bottom: 16px;
}

.meta-form {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.form-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-sub);
}

.edit-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.panel-header h3 {
  font-size: 16px;
}

.drag-tip {
  font-size: 12px;
  color: var(--text-sub);
}

.pool-panel,
.selected-panel {
  display: flex;
  flex-direction: column;
  max-height: 70vh;
}

.pool-grid {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.pool-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: var(--radius-sm);
  transition: background 0.15s;
}

.pool-item:hover {
  background: var(--bg-fill);
}

.pool-cover {
  width: 40px;
  height: 54px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.pool-info {
  flex: 1;
  min-width: 0;
}

.pool-title {
  font-size: 13px;
  line-height: 1.3;
  margin-bottom: 2px;
}

.pool-author {
  font-size: 11px;
  color: var(--text-sub);
}

.pool-price {
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
}

.pool-pagination {
  padding: 12px;
  display: flex;
  justify-content: center;
}

.empty-selected {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.selected-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selected-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-fill);
  border-radius: var(--radius-sm);
  cursor: move;
  border: 1px solid transparent;
}

.selected-item:hover {
  border-color: var(--brand);
}

.order-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.sel-cover {
  width: 40px;
  height: 54px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.sel-info {
  flex: 1;
  min-width: 0;
}

.sel-title {
  font-size: 13px;
  margin-bottom: 2px;
}

.sel-author {
  font-size: 11px;
  color: var(--text-sub);
}

.sel-price {
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
}
</style>
