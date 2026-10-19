<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">评价管理</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input v-model="query.keyword" placeholder="图书标题 / 评价内容" clearable style="width: 240px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.rating" placeholder="全部星级" clearable style="width: 130px"
                   @change="handleSearch">
          <el-option v-for="n in 5" :key="n" :label="n + ' 星'" :value="n" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px"
                   @change="handleSearch">
          <el-option label="正常" :value="1" />
          <el-option label="已隐藏" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图书" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bookTitle || ('#' + row.bookId) }}</template>
        </el-table-column>
        <el-table-column label="评分" width="140">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '（用户未填写文字评价）' }}</template>
        </el-table-column>
        <el-table-column label="评价人" width="160">
          <template #default="{ row }">
            <span>{{ row.userNickname || ('用户#' + row.userId) }}</span>
            <el-tag v-if="row.anonymous === 1" size="small" type="info" class="ml-6">匿名</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="来源订单" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '正常' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link :type="row.status === 1 ? 'warning' : 'success'"
                       @click="handleStatusChange(row)">
              {{ row.status === 1 ? '隐藏' : '恢复' }}
            </el-button>
            <el-popconfirm title="删除后不可恢复，确定删除该评价吗？" width="220"
                           confirm-button-text="删除" cancel-button-text="取消"
                           @confirm="handleDelete(row)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的评价" />
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
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageReviews, updateReviewStatus, deleteReview } from '@/api/review'

const list = ref([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  keyword: '',
  rating: null,
  status: null,
  pageNum: 1,
  pageSize: 10
})

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await pageReviews(query)
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
  query.rating = null
  query.status = null
  handleSearch()
}

/** 违规评价优先隐藏：数据保留可追溯，同时也从前台列表与平均分中移除 */
async function handleStatusChange(row) {
  const next = row.status === 1 ? 0 : 1
  await updateReviewStatus(row.id, next)
  row.status = next
  ElMessage.success(next === 1 ? '已恢复该评价' : '已隐藏该评价')
}

async function handleDelete(row) {
  await deleteReview(row.id)
  ElMessage.success('已删除该评价')
  // 末页删完最后一条时把页码退回，避免出现空白页
  if (list.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.ml-6 {
  margin-left: 6px;
}
</style>
