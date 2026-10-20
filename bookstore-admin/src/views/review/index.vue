<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">评价管理</h2>
      <el-button :icon="Refresh" :loading="loading" @click="reloadAll">刷新</el-button>
    </div>

    <!-- ==================== 汇总条：合规与水军都看这几个数 ==================== -->
    <div class="app-card statbar">
      <div class="statbar__cell">
        <span class="statbar__label">评价总数</span>
        <b class="statbar__value num">{{ summary.total || 0 }}</b>
        <span class="statbar__foot">含已隐藏</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">正常展示</span>
        <b class="statbar__value num">{{ summary.visible || 0 }}</b>
        <span class="statbar__foot">前台可见并计入评分</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">已隐藏</span>
        <b class="statbar__value num" :class="{ 'is-alert': summary.hidden > 0 }">
          {{ summary.hidden || 0 }}
        </b>
        <span class="statbar__foot">不参与评分</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">平均分</span>
        <b class="statbar__value num">
          {{ summary.avgRating === null || summary.avgRating === undefined ? '—' : Number(summary.avgRating).toFixed(1) }}
        </b>
        <span class="statbar__foot">仅统计正常评价</span>
      </div>
    </div>

    <!-- ==================== 筛选卡：关键词 + 星级 + 状态胶囊 ==================== -->
    <div class="app-card filter">
      <div class="filter__row">
        <el-input
          v-model="query.keyword"
          placeholder="图书标题 / 评价内容"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.rating" placeholder="全部星级" clearable style="width: 130px"
                   @change="handleSearch">
          <el-option v-for="n in 5" :key="n" :label="n + ' 星'" :value="n" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        <span class="filter__echo">当前筛选 {{ total }} 条</span>
      </div>

      <div class="filter__row filter__row--status">
        <span class="filter__label">状态</span>
        <div class="chips">
          <button
            type="button"
            class="chip"
            :class="{ on: query.status === null }"
            style="--dc: #6c4df2"
            @click="pickStatus(null)"
          >
            <i></i>全部<b class="num">{{ summary.total || 0 }}</b>
          </button>
          <button
            type="button"
            class="chip"
            :class="{ on: query.status === 1 }"
            style="--dc: #1f9d63"
            @click="pickStatus(1)"
          >
            <i></i>正常<b class="num">{{ summary.visible || 0 }}</b>
          </button>
          <button
            type="button"
            class="chip"
            :class="{ on: query.status === 0 }"
            style="--dc: #b4bac6"
            @click="pickStatus(0)"
          >
            <i></i>已隐藏<b class="num">{{ summary.hidden || 0 }}</b>
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 列表 ==================== -->
    <div class="app-card">
      <el-table v-loading="loading" :data="list" :border="false" class="list-table">
        <el-table-column label="图书 / 来源订单" min-width="200">
          <template #default="{ row }">
            <div class="cell-main">{{ row.bookTitle || ('#' + row.bookId) }}</div>
            <div class="cell-sub num">{{ row.orderNo || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="140">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.content ? 'cell-main' : 'cell-sub'">
              {{ row.content || '（用户未填写文字评价）' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="评价人" width="150">
          <template #default="{ row }">
            <span class="cell-main">{{ row.userNickname || ('用户#' + row.userId) }}</span>
            <span v-if="row.anonymous === 1" class="pill pill--gray anon">匿名</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="pill" :class="row.status === 1 ? 'pill--ok' : 'pill--gray'">
              {{ row.status === 1 ? '正常' : '已隐藏' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" min-width="160">
          <template #default="{ row }">
            <span class="cell-sub">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
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
          <el-empty description="没有符合条件的评价" :image-size="72" />
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
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { pageReviews, updateReviewStatus, deleteReview, reviewSummary } from '@/api/review'

const list = ref([])
const loading = ref(false)
const total = ref(0)
const summary = ref({})

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
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/** 汇总指标：平均分只在「正常评价」上算，隐藏/恢复后必须重读 */
async function loadSummary() {
  summary.value = (await reviewSummary().catch(() => ({}))) || {}
}

function reloadAll() {
  loadSummary()
  load()
}

function pickStatus(status) {
  query.status = status
  handleSearch()
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
  loadSummary()
}

async function handleDelete(row) {
  await deleteReview(row.id)
  ElMessage.success('已删除该评价')
  // 末页删完最后一条时把页码退回，避免出现空白页
  if (list.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  await Promise.all([load(), loadSummary()])
}

onMounted(reloadAll)
</script>

<style scoped>
.anon {
  margin-left: 6px;
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
}
</style>
