<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBooklistStore } from '@/stores/booklist'
import { useUserStore } from '@/stores/user'
import { addBatchToCartGuarded, addToCartGuarded } from '@/utils/cart-action'
import { formatPrice } from '@/utils/format'
import BookCard from '@/components/BookCard.vue'
import { exportToExcel } from '@/utils/excel'
import { ElMessage, ElMessageBox } from 'element-plus'
import { isOfficialId, getOfficialBooklist, fetchOfficialBooks } from '@/data/official-booklists'

const route = useRoute()
const router = useRouter()
const booklistStore = useBooklistStore()
const userStore = useUserStore()

const viewMode = ref('card')
const selectedBooks = ref([])
const loading = ref(true)
const notFound = ref(false)
const forbidden = ref(false)
const forbiddenMsg = ref('')

/**
 * 官方书单的图书来自接口解析（规则 → 真实图书），需要用 ref 装；
 * 后端书单直接读 store 里的 detail，两者在 books 计算属性里汇合，模板只认一个数组。
 */
const officialBooks = ref([])
const backendDetail = ref(null)

const listId = computed(() => route.params.id)
const isOfficial = computed(() => isOfficialId(listId.value))
const officialDef = computed(() => (isOfficial.value ? getOfficialBooklist(listId.value) : null))

const books = computed(() => (isOfficial.value ? officialBooks.value : backendDetail.value?.books || []))

/** 只有后端书单才有「我的 / 别人的」之分 */
const mine = computed(() => (isOfficial.value ? false : !!backendDetail.value?.mine))
/** 平台已隐藏：创建者自己仍能打开，但要让本人知道状态 */
const platformHidden = computed(() => !isOfficial.value && backendDetail.value?.status === 0)

const header = computed(() => {
  if (isOfficial.value) {
    const def = officialDef.value
    return {
      name: def?.name || '书单',
      description: def?.description || '',
      tag: def?.tag || '',
      count: books.value.length,
      owner: '官方'
    }
  }
  const d = backendDetail.value
  return {
    name: d?.name || '',
    description: d?.description || '暂无描述',
    tag: '',
    count: books.value.length,
    owner: d?.mine ? '我' : d?.ownerNickname || '书友'
  }
})

/**
 * 统一加载入口。
 *
 * <p>官方书单是「规则」（分类名 + 排序，或人工挑的书名），要经图书接口解析成真实图书；
 * 后端书单直接调详情接口。两条路都归到 `books`，模板不必分叉。
 */
async function load() {
  loading.value = true
  notFound.value = false
  forbidden.value = false

  try {
    if (isOfficial.value) {
      if (!officialDef.value) {
        notFound.value = true
        return
      }
      officialBooks.value = await fetchOfficialBooks(officialDef.value)
      return
    }

    backendDetail.value = await booklistStore.fetchDetail(listId.value)
  } catch (e) {
    // 403 = 别人的私密书单（后端刻意不回 404：自增 ID 藏不住存在性，
    // 说「不存在」只会让创建者以为自己书单丢了）；404 = 真的没有或已被删除
    if (e?.code === 403) {
      forbidden.value = true
      forbiddenMsg.value = e.message || '该书单仅创建者可见'
    } else {
      notFound.value = true
    }
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

async function removeBook(book) {
  try {
    await ElMessageBox.confirm(`确认把《${book.title}》移出书单？`, '提示', { type: 'warning' })
    await booklistStore.removeBook(listId.value, book.id)
    backendDetail.value = booklistStore.detail
    ElMessage.success('已移除')
  } catch (e) {
    /* 取消或接口报错 */
  }
}

/**
 * 把官方书单收藏为自己的书单。
 *
 * <p>为什么是「复制」而不是「引用」：官方书单是前端规则，随代码发布变化，
 * 一旦它是引用关系，用户刚收藏的书单可能因为规则调整而内容突变。
 * 复制一份实体数据，用户之后想怎么改都行 —— 这才符合"收藏"的语义。
 */
const collecting = ref(false)
async function collectAsMine() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (books.value.length === 0) {
    ElMessage.warning('该精选书单暂无图书，无法收藏')
    return
  }
  collecting.value = true
  try {
    const created = await booklistStore.create({
      name: header.value.name,
      description: header.value.description || '',
      isPublic: 0
    })
    const res = await booklistStore.addBooks(created.id, books.value.map((b) => b.id))
    ElMessage.success(`已收藏为我的书单，共 ${res.added} 本`)
    router.push('/booklists/' + created.id)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    collecting.value = false
  }
}

onMounted(load)
watch(listId, load)
</script>

<template>
  <div class="page-container booklist-detail" v-loading="loading">
    <!-- 别人的私密书单 -->
    <el-result
      v-if="forbidden && !loading"
      icon="warning"
      title="该书单仅创建者可见"
      :sub-title="forbiddenMsg"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/booklists')">返回书单列表</el-button>
      </template>
    </el-result>

    <el-result
      v-else-if="notFound && !loading"
      icon="warning"
      title="书单不存在"
      sub-title="该书单可能已被删除，或链接不正确"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/booklists')">返回书单列表</el-button>
      </template>
    </el-result>

    <template v-else-if="!loading">
      <!-- 被平台隐藏：内容仍可读，但要让创建者知道发生了什么 -->
      <el-alert
        v-if="platformHidden"
        class="hidden-alert"
        type="error"
        show-icon
        :closable="false"
        title="该书单已被平台隐藏"
        description="书单内容仅你自己可见，不会出现在书单广场。如需申诉请联系客服。"
      />

      <div class="detail-header card">
        <div class="header-info">
          <h2>
            {{ header.name }}
            <el-tag v-if="isOfficial" size="small" type="danger" effect="plain" class="official-tag">
              官方
            </el-tag>
            <el-tag v-else-if="mine" size="small" type="success" effect="plain" class="official-tag">
              我的
            </el-tag>
            <el-tag v-else-if="backendDetail?.isPublic === 1" size="small" type="info" effect="plain" class="official-tag">
              公开
            </el-tag>
            <el-tag v-else size="small" type="info" effect="plain" class="official-tag">仅自己可见</el-tag>
          </h2>
          <p class="desc">{{ header.description }}</p>
          <p class="meta">
            共 {{ header.count }} 本书
            <span class="dot">·</span>
            {{ header.owner === '我' ? '我创建的' : header.owner === '官方' ? '官方精选' : header.owner + ' 的书单' }}
          </p>
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
          <!-- 官方书单：一键复制成自己的书单 -->
          <el-button
            v-if="isOfficial"
            type="primary"
            :loading="collecting"
            :disabled="books.length === 0"
            @click="collectAsMine"
          >
            <el-icon><Star /></el-icon> 收藏为我的书单
          </el-button>
          <el-button v-if="mine" @click="router.push('/booklists/' + listId + '/edit')">
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
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
            <!-- 只有自己的书单能改内容；官方书单与他人书单都是只读 -->
            <el-button v-if="mine" size="small" type="danger" link @click="removeBook(row)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="books.length === 0" description="书单暂无图书">
        <el-button v-if="mine" type="primary" @click="router.push('/books')">去挑书</el-button>
      </el-empty>

      <p v-if="mine && books.length > 0" class="tip">
        提示：在图书列表勾选图书后可「批量加书单」，也可以在详情页把图书加入本书单。
      </p>
    </template>
  </div>
</template>

<style scoped>
.hidden-alert {
  margin-bottom: 12px;
}

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

.dot {
  margin: 0 6px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
  justify-content: flex-end;
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
