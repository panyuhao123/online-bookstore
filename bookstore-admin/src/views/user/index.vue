<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">用户管理</h2>
      <el-button :icon="Refresh" :loading="loading" @click="reloadAll">刷新</el-button>
    </div>

    <!-- ==================== 汇总条：注册盘子有多大、禁用多少 ==================== -->
    <div class="app-card statbar">
      <div class="statbar__cell">
        <span class="statbar__label">注册用户</span>
        <b class="statbar__value num">{{ summary.total || 0 }}</b>
        <span class="statbar__foot">累计注册</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">正常</span>
        <b class="statbar__value num">{{ summary.active || 0 }}</b>
        <span class="statbar__foot">可正常登录</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">已禁用</span>
        <b class="statbar__value num" :class="{ 'is-alert': summary.disabled > 0 }">
          {{ summary.disabled || 0 }}
        </b>
        <span class="statbar__foot">令牌立即失效</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">本月新增</span>
        <b class="statbar__value num">{{ summary.newThisMonth || 0 }}</b>
        <span class="statbar__foot">自然月口径</span>
      </div>
    </div>

    <!-- ==================== 筛选卡：手机号/昵称 + 状态胶囊 ==================== -->
    <div class="app-card filter">
      <div class="filter__row">
        <el-input
          v-model="query.keyword"
          placeholder="手机号 / 昵称"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        <span class="filter__echo">当前筛选 {{ total }} 人</span>
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
            <i></i>正常<b class="num">{{ summary.active || 0 }}</b>
          </button>
          <button
            type="button"
            class="chip"
            :class="{ on: query.status === 0 }"
            style="--dc: #b4bac6"
            @click="pickStatus(0)"
          >
            <i></i>已禁用<b class="num">{{ summary.disabled || 0 }}</b>
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 列表 ==================== -->
    <div class="app-card">
      <el-table v-loading="loading" :data="list" :border="false" class="list-table">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="cell-main">{{ row.nickname || '未设置昵称' }}</div>
            <div class="cell-sub num">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="累计订单" width="110" align="center">
          <template #default="{ row }">
            <span class="num cell-sub-strong">{{ row.orderCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计消费" width="140" align="right">
          <template #default="{ row }">
            <span class="num cell-amount">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              inline-prompt
              active-text="正常"
              inactive-text="禁用"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="注册时间" min-width="170">
          <template #default="{ row }">
            <span class="cell-sub">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的用户" :image-size="72" />
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
import { formatTime } from '@/utils/format'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { pageUsers, updateUserStatus, userSummary } from '@/api/user'

const list = ref([])
const loading = ref(false)
const total = ref(0)
const summary = ref({})

const query = reactive({
  keyword: '',
  status: null,
  pageNum: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const data = await pageUsers(query)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/** 汇总指标：失败时退化为空对象（页面显示 0），不阻断列表 */
async function loadSummary() {
  summary.value = (await userSummary().catch(() => ({}))) || {}
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
  query.status = null
  handleSearch()
}

async function handleStatusChange(row, val) {
  try {
    await updateUserStatus(row.id, val)
    ElMessage.success(val === 1 ? '已启用该用户' : '已禁用该用户')
    // 启用/禁用会改变「正常 / 已禁用」两个汇总数字
    loadSummary()
  } catch (e) {
    // 失败回滚开关，避免界面显示与实际可用状态不一致
    row.status = val === 1 ? 0 : 1
  }
}

onMounted(reloadAll)
</script>
