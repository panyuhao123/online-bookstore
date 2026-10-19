<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">操作日志</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-select v-model="query.module" placeholder="全部模块" clearable style="width: 150px"
                   @change="handleSearch">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="query.success" placeholder="全部结果" clearable style="width: 130px"
                   @change="handleSearch">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-input v-model="query.adminName" placeholder="操作人" clearable style="width: 160px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-box">
              <div class="expand-row">
                <span class="expand-label">请求参数</span>
                <span class="expand-value">{{ row.params || '（无）' }}</span>
              </div>
              <div v-if="row.errorMsg" class="expand-row">
                <span class="expand-label">失败原因</span>
                <span class="expand-value danger">{{ row.errorMsg }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="操作人" width="150">
          <template #default="{ row }">
            <div>{{ row.adminName || '-' }}</div>
            <span class="app-muted">ID {{ row.adminId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="action" label="动作" min-width="130" show-overflow-tooltip />
        <el-table-column label="请求" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.httpMethod" size="small" class="method-tag">{{ row.httpMethod }}</el-tag>
            <span>{{ row.uri }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的操作日志" />
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
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageLogs, logModules } from '@/api/log'

const list = ref([])
const loading = ref(false)
const total = ref(0)
const modules = ref([])

const query = reactive({
  module: null,
  success: null,
  adminName: '',
  pageNum: 1,
  pageSize: 10
})

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await pageLogs(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 模块列表只取一次：它是下拉框数据源，不随查询条件变化 */
async function loadModules() {
  modules.value = await logModules()
}

function handleSearch() {
  query.pageNum = 1
  load()
}

function handleReset() {
  query.module = null
  query.success = null
  query.adminName = ''
  handleSearch()
}

onMounted(() => {
  loadModules()
  load()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.method-tag {
  margin-right: 6px;
}

.expand-box {
  padding: 4px 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
}

.expand-label {
  display: inline-block;
  width: 72px;
  color: #909399;
}

.expand-value {
  word-break: break-all;
}

.danger {
  color: #f56c6c;
}
</style>
