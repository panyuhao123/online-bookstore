<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">到货提醒</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input v-model="query.keyword" placeholder="书名" clearable style="width: 220px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-switch v-model="query.onlyWaiting" active-text="只看有人等待" @change="handleSearch" />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <p class="app-muted tip">
        按图书聚合的等待人数，按待通知人数倒序 —— 这就是补货顺序。补货并确认库存后点「标记已通知」，
        该书的等待记录会一次性置为已通知；重复点击返回 0，不会报错。
      </p>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image v-if="row.bookCoverUrl" :src="fileUrl(row.bookCoverUrl)"
                      :preview-src-list="[fileUrl(row.bookCoverUrl)]" class="cover" fit="cover"
                      preview-teleported />
            <span v-else class="app-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="图书" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="title-cell">{{ row.bookTitle }}</div>
            <div class="app-muted sub">#{{ row.bookId }} · ¥{{ Number(row.price || 0).toFixed(2) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="100">
          <template #default="{ row }">
            <span :class="{ 'stock-zero': row.stock <= 0 }">{{ row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图书状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.bookStatus === 1 ? 'success' : 'info'" size="small">
              {{ row.bookStatus === 1 ? '上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="待通知" width="120">
          <template #default="{ row }">
            <span v-if="row.waitingCount > 0" class="waiting">{{ row.waitingCount }} 人</span>
            <span v-else class="app-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column label="已通知" width="100">
          <template #default="{ row }">{{ row.notifiedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="最近订阅" width="160">
          <template #default="{ row }">{{ formatTime(row.lastSubscribeTime) }}</template>
        </el-table-column>
        <el-table-column label="补货提示" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.stock > 0 && row.waitingCount > 0" type="warning" size="small">
              已补货，可通知
            </el-tag>
            <el-tag v-else-if="row.stock <= 0 && row.waitingCount > 0" type="danger" size="small">
              仍缺货
            </el-tag>
            <span v-else class="app-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!row.waitingCount"
                       @click="handleNotify(row)">标记已通知</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有需要处理的到货提醒" />
        </template>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="handleSearch"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageStockNoticeSummary, markStockNoticeNotified } from '@/api/stock-notice'
import { fileUrl } from '@/utils/url'

const list = ref([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  keyword: '',
  onlyWaiting: true,
  pageNum: 1,
  pageSize: 10
})

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await pageStockNoticeSummary({
      keyword: query.keyword || undefined,
      onlyWaiting: query.onlyWaiting,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  load()
}

function handleReset() {
  query.keyword = ''
  query.onlyWaiting = true
  handleSearch()
}

/**
 * 标记已通知。
 *
 * 这里多做一步确认：一旦置为已通知，这些读者的等待记录就闭合了，
 * 若其实还没补到货，等于把真实需求清掉。所以先让运营确认「货确实到了」。
 */
function handleNotify(row) {
  ElMessageBox.confirm(
    '确认《' + row.bookTitle + '》已补货并已通知 ' + row.waitingCount + ' 位读者？该操作会把这批等待记录置为已通知。',
    '标记已通知',
    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
  )
    .then(async () => {
      const data = await markStockNoticeNotified(row.bookId)
      const notified = (data && data.notified) || 0
      if (notified > 0) {
        ElMessage.success('已通知 ' + notified + ' 位读者')
      } else {
        ElMessage.info('没有待通知的订阅（可能已被其他人处理）')
      }
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.tip {
  margin: 0 0 12px;
  line-height: 1.7;
}

.cover {
  width: 44px;
  height: 60px;
  border-radius: 4px;
}

.title-cell {
  font-weight: 600;
}

.sub {
  font-size: 12px;
  margin-top: 2px;
}

.stock-zero {
  color: #f56c6c;
  font-weight: 600;
}

.waiting {
  color: #e6a23c;
  font-weight: 600;
}
</style>
