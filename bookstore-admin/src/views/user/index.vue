<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">用户管理</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input v-model="query.keyword" placeholder="手机号 / 昵称" clearable style="width: 220px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px"
                   @change="handleSearch">
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" show-overflow-tooltip />
        <el-table-column label="累计订单" width="110" prop="orderCount" />
        <el-table-column label="累计消费" width="130">
          <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
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
        <el-table-column label="注册时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的用户" />
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
import { pageUsers, updateUserStatus } from '@/api/user'

const list = ref([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  keyword: '',
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
    const data = await pageUsers(query)
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
  query.status = null
  handleSearch()
}

async function handleStatusChange(row, val) {
  try {
    await updateUserStatus(row.id, val)
    ElMessage.success(val === 1 ? '已启用该用户' : '已禁用该用户')
  } catch (e) {
    // 失败回滚开关，避免界面显示与实际可用状态不一致
    row.status = val === 1 ? 0 : 1
  }
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
