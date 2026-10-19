<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">订单管理</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 200px"
                  @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px"
                   @change="handleSearch">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button type="danger" plain :icon="Delete" @click="handleCancelTimeout">
          取消超时订单
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column label="下单用户" min-width="140">
          <template #default="{ row }">
            <div>{{ row.userPhone }}</div>
            <span class="app-muted">{{ row.userNickname || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="totalQuantity" label="件数" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" disable-transitions>
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 2"
              link
              type="warning"
              :loading="row.shipping"
              @click="handleShip(row)"
            >
              发货
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的订单" />
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

    <el-drawer v-model="detailVisible" title="订单详情" size="520px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="下单用户">
          {{ detail.userPhone }}（{{ detail.userNickname || '-' }}）
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="statusTagType(detail.status)" disable-transitions>{{ detail.statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="订单金额">¥{{ Number(detail.totalAmount).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="商品件数">{{ detail.totalQuantity }} 件</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatTime(detail.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="收货信息">{{ detail.addressSnapshot }}</el-descriptions-item>
      </el-descriptions>

      <div class="items-title">商品明细</div>
      <el-table v-if="detail" :data="detail.items" border size="small">
        <el-table-column prop="bookTitle" label="书名" min-width="160" show-overflow-tooltip />
        <el-table-column label="单价" width="90">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column label="小计" width="100">
          <template #default="{ row }">¥{{ Number(row.subtotal).toFixed(2) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && detail.status === 2" type="warning" @click="handleShip(detail, true)">
          确认发货
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Delete } from '@element-plus/icons-vue'
import { pageOrders, getOrder, shipOrder, cancelTimeoutOrders } from '@/api/order'

/** 与后端 OrderStatus 枚举保持一致：1待支付 2已支付 3已发货 4已完成 5已取消 */
const STATUS_OPTIONS = [
  { value: 1, label: '待支付' },
  { value: 2, label: '已支付' },
  { value: 3, label: '已发货' },
  { value: 4, label: '已完成' },
  { value: 5, label: '已取消' }
]

const route = useRoute()
const list = ref([])
const detail = ref(null)
const loading = ref(false)
const detailVisible = ref(false)
const total = ref(0)

const query = reactive({
  orderNo: '',
  status: null,
  pageNum: 1,
  pageSize: 10
})

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: '', 4: 'success', 5: 'info' }
  return map[status] || 'info'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await pageOrders(query)
    // 追加行内 loading 标记，避免发货按钮上 loading 串到同表其他行
    list.value = data.list.map((row) => ({ ...row, shipping: false }))
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
  query.orderNo = ''
  query.status = null
  handleSearch()
}

async function openDetail(row) {
  detail.value = await getOrder(row.id)
  detailVisible.value = true
}

async function handleShip(row, inDrawer) {
  ElMessageBox.confirm('确认订单「' + row.orderNo + '」已发货？', '发货确认', {
    type: 'warning',
    confirmButtonText: '确认发货',
    cancelButtonText: '取消'
  })
    .then(async () => {
      row.shipping = true
      await shipOrder(row.id)
      ElMessage.success('发货成功')
      if (inDrawer) {
        detail.value.status = 3
        detail.value.statusText = '已发货'
      }
      load()
    })
    .catch(() => {})
    .finally(() => {
      row.shipping = false
    })
}

/**
 * 手动取消超时未支付订单。
 * 阈值做成可输入：演示时填 0 可一键清理全部未支付订单，不必等定时任务到点。
 * 请求失败由 axios 拦截器统一提示，这里只处理「用户取消输入」。
 */
async function handleCancelTimeout() {
  try {
    const { value } = await ElMessageBox.prompt(
      '取消超过该时长仍未支付的订单，并回补库存与销量。填 0 表示把所有未支付订单都视为超时。',
      '取消超时订单',
      {
        type: 'warning',
        inputValue: '30',
        inputPattern: /^\d{1,4}$/,
        inputErrorMessage: '请输入 0 ~ 1440 之间的整数（分钟）',
        confirmButtonText: '执行',
        cancelButtonText: '取消'
      }
    )
    const count = await cancelTimeoutOrders(Number(value))
    ElMessage.success(count > 0 ? `已取消 ${count} 笔超时订单，库存已回补` : '没有需要取消的超时订单')
    load()
  } catch (e) {
    // 用户关闭输入框：静默返回
  }
}

onMounted(() => {
  // 支持从首页「处理待发货」直接跳转并预置筛选条件
  if (route.query.status) {
    query.status = Number(route.query.status)
  }
  load()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.items-title {
  margin: 20px 0 10px;
  font-weight: 600;
}
</style>
