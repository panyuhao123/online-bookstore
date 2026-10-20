<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">书单管理</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input v-model="query.keyword" placeholder="书单名称 / 简介" clearable style="width: 220px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.status" placeholder="全部治理状态" clearable style="width: 150px"
                   @change="handleSearch">
          <el-option label="正常" :value="1" />
          <el-option label="已隐藏" :value="0" />
        </el-select>
        <el-select v-model="query.isPublic" placeholder="全部可见性" clearable style="width: 150px"
                   @change="handleSearch">
          <el-option label="公开" :value="1" />
          <el-option label="仅自己可见" :value="0" />
        </el-select>
        <el-input v-model="query.userId" placeholder="创建者用户 ID" clearable style="width: 150px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <p class="app-muted tip">
        默认展示全部书单（含私密与已隐藏）。「隐藏」只是不对外展示、数据仍保留，可随时恢复；
        「删除」不可恢复。管理员不能编辑用户书单的内容，这是刻意的边界。
      </p>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="书单" min-width="240">
          <template #default="{ row }">
            <div class="name-cell">{{ row.name }}</div>
            <div v-if="row.description" class="app-muted desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="创建者" width="190">
          <template #default="{ row }">
            <div>{{ row.ownerNickname || ('用户#' + row.ownerId) }}</div>
            <div class="app-muted phone">{{ row.ownerPhone || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="bookCount" label="条目" width="80" />
        <el-table-column label="用户可见性" width="120">
          <template #default="{ row }">
            <el-tag :type="row.isPublic === 1 ? 'success' : 'info'" size="small">
              {{ row.isPublic === 1 ? '公开' : '仅自己可见' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="治理状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'"
                       @click="handleStatusChange(row)">
              {{ row.status === 1 ? '隐藏' : '恢复' }}
            </el-button>
            <el-popconfirm title="删除后连同条目一并清除且不可恢复，确定删除？" width="260"
                           confirm-button-text="删除" cancel-button-text="取消"
                           @confirm="handleDelete(row)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的书单" />
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

    <el-drawer v-model="detailVisible" title="书单详情" size="640px" @closed="detail = null">
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="书单名称" :span="2">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="创建者">
            {{ detail.ownerNickname || ('用户#' + detail.ownerId) }}
          </el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.ownerPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="用户可见性">
            {{ detail.isPublic === 1 ? '公开' : '仅自己可见' }}
          </el-descriptions-item>
          <el-descriptions-item label="治理状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
              {{ detail.status === 1 ? '正常' : '已隐藏' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="简介" :span="2">
            {{ detail.description || '（用户未填写简介）' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-books-title">
          收录图书（{{ (detail.books || []).length }}）
          <span class="app-muted detail-hint">已下架图书照常展示，便于判断内容是否违规</span>
        </div>
        <el-table :data="detail.books || []" border size="small">
          <el-table-column label="书名" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.title }}</template>
          </el-table-column>
          <el-table-column label="作者" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.author || '—' }}</template>
          </el-table-column>
          <el-table-column label="价格" width="90">
            <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="库存" width="70" prop="stock" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '上架' : '已下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该用户还没有往书单里添加图书" :image-size="60" />
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageBooklists, getBooklist, updateBooklistStatus, deleteBooklist } from '@/api/booklist'

const list = ref([])
const loading = ref(false)
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const query = reactive({
  keyword: '',
  status: null,
  isPublic: null,
  userId: '',
  pageNum: 1,
  pageSize: 10
})

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

/** 用户 ID 只接受数字：清空时传 null（后端按「不筛选」处理），非法输入直接不传 */
function buildParams() {
  const params = {
    keyword: query.keyword || undefined,
    status: query.status,
    isPublic: query.isPublic,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }
  const userId = String(query.userId || '').trim()
  params.userId = /^\d+$/.test(userId) ? Number(userId) : undefined
  return params
}

async function load() {
  loading.value = true
  try {
    const data = await pageBooklists(buildParams())
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
  query.isPublic = null
  query.userId = ''
  handleSearch()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getBooklist(row.id)
  } finally {
    detailLoading.value = false
  }
}

/** 违规书单优先隐藏：数据保留可追溯，且能随时恢复 */
async function handleStatusChange(row) {
  const next = row.status === 1 ? 0 : 1
  await updateBooklistStatus(row.id, next)
  row.status = next
  ElMessage.success(next === 1 ? '已恢复该单' : '已隐藏该单')
}

async function handleDelete(row) {
  await deleteBooklist(row.id)
  ElMessage.success('已删除该单')
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

.tip {
  margin: 0 0 12px;
  line-height: 1.7;
}

.name-cell {
  font-weight: 600;
}

.desc {
  font-size: 12px;
  margin-top: 2px;
}

.phone {
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
}

.detail-books-title {
  margin: 18px 0 10px;
  font-weight: 600;
}

.detail-hint {
  font-weight: 400;
  font-size: 12px;
  margin-left: 8px;
}
</style>
