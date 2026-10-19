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
const booklist = ref(null)
const form = reactive({
  name: '',
  description: ''
})

// 可选图书池
const poolBooks = ref([])
const poolLoading = ref(false)
const poolPage = ref(1)
const poolKeyword = ref('')
const poolTotal = ref(0)

// 已选图书
const selectedBooks = ref([])

// 拖拽
const dragIndex = ref(-1)

async function loadPool() {
  poolLoading.value = true
  try {
    const res = await pageBooks({
      keyword: poolKeyword.value,
      pageNum: poolPage.value,
      pageSize: 12
    })
    poolBooks.value = res.list || []
    poolTotal.value = res.total || 0
  } finally {
    poolLoading.value = false
  }
}

function addBook(book) {
  if (selectedBooks.value.some((b) => b.id === book.id)) {
    ElMessage.info('已在书单中')
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

function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入书单名称')
    return
  }
  if (isEdit.value) {
    booklistStore.updateBooklist(route.params.id, {
      name: form.name,
      description: form.description,
      books: selectedBooks.value
    })
    ElMessage.success('书单已更新')
  } else {
    const list = booklistStore.createBooklist(form.name, form.description)
    booklistStore.addBooksToList(list.id, selectedBooks.value)
    ElMessage.success('书单已创建')
  }
  router.push('/booklists')
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

onMounted(() => {
  if (isEdit.value) {
    booklist.value = booklistStore.getBooklist(route.params.id)
    if (booklist.value) {
      form.name = booklist.value.name
      form.description = booklist.value.description || ''
      selectedBooks.value = [...booklist.value.books]
    }
  }
  loadPool()
})
</script>

<template>
  <div class="page-container booklist-edit">
    <div class="edit-header card">
      <h2>{{ isEdit ? '编辑书单' : '新建书单' }}</h2>
      <div class="meta-form">
        <el-input v-model="form.name" placeholder="书单名称" style="width: 300px" size="large" />
        <el-input v-model="form.description" placeholder="书单描述（可选）" style="width: 400px" size="large" />
        <el-button type="primary" size="large" @click="save">保存书单</el-button>
        <el-button size="large" @click="exportList">导出 Excel</el-button>
      </div>
    </div>

    <div class="edit-body">
      <!-- 左：可选图书池 -->
      <div class="pool-panel card">
        <div class="panel-header">
          <h3>可选图书</h3>
          <el-input v-model="poolKeyword" placeholder="搜索图书" size="small" style="width: 200px" @keyup.enter="loadPool" />
        </div>
        <div class="pool-grid" v-loading="poolLoading">
          <div v-for="book in poolBooks" :key="book.id" class="pool-item">
            <img :src="bookCover(book) || placeholderCover(book.title)" class="pool-cover" />
            <div class="pool-info">
              <p class="pool-title text-ellipsis-2">{{ book.title }}</p>
              <p class="pool-author text-ellipsis">{{ book.author }}</p>
              <span class="pool-price">{{ formatPrice(book.price) }}</span>
            </div>
            <el-button type="primary" size="small" @click="addBook(book)">添加</el-button>
          </div>
        </div>
        <div class="pool-pagination" v-if="poolTotal > 12">
          <el-pagination
            v-model:current-page="poolPage"
            :page-size="12"
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
          <h3>已选图书（{{ selectedBooks.length }}）</h3>
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
