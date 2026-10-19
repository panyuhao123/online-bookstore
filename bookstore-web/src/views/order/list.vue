<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageOrders, cancelOrder } from '@/api/order'
import { orderStatusText, orderStatusTagType, formatPrice, formatDateTime } from '@/utils/format'
import { exportToExcel } from '@/utils/excel'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const total = ref(0)
const loading = ref(false)
const activeStatus = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)

const statusTabs = [
  { label: '全部', value: null },
  { label: '待支付', value: 1 },
  { label: '已支付', value: 2 },
  { label: '已发货', value: 3 },
  { label: '已完成', value: 4 },
  { label: '已取消', value: 5 }
]

async function loadOrders() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (activeStatus.value !== null) params.status = activeStatus.value
    const res = await pageOrders(params)
    orders.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleTabChange(val) {
  activeStatus.value = val
  pageNum.value = 1
  loadOrders()
}

async function onCancel(order) {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
    await cancelOrder(order.id)
    ElMessage.success('订单已取消')
    loadOrders()
  } catch (e) {
    /* 取消 */
  }
}

function exportOrders() {
  if (orders.value.length === 0) {
    ElMessage.warning('暂无订单可导出')
    return
  }
  const columns = [
    { prop: 'orderNo', label: '订单编号', width: 24 },
    { prop: 'statusText', label: '状态', width: 10 },
    { prop: 'totalAmount', label: '金额', width: 12 },
    { prop: 'totalQuantity', label: '件数', width: 8 },
    { prop: 'createTime', label: '下单时间', width: 20 },
    { prop: 'addressSnapshot', label: '收货地址', width: 30 }
  ]
  exportToExcel(orders.value, columns, '我的订单_' + Date.now())
}

onMounted(() => {
  // 支持 /orders?status=4（个人中心统计卡点进来时直接定到对应标签）
  const s = route.query.status
  if (s !== undefined && s !== null && s !== '') activeStatus.value = Number(s)
  loadOrders()
})
</script>

<template>
  <div class="page-container order-list-page">
    <div class="page-header">
      <h2>我的订单</h2>
      <el-button @click="exportOrders">
        <el-icon><Download /></el-icon> 导出 Excel
      </el-button>
    </div>

    <div class="order-tabs card">
      <el-tabs v-model="activeStatus" @tab-change="handleTabChange">
        <el-tab-pane v-for="tab in statusTabs" :key="tab.value" :label="tab.label" :name="tab.value" />
      </el-tabs>
    </div>

    <div class="card" v-loading="loading">
      <el-table :data="orders" style="width: 100%">
        <el-table-column label="订单信息" min-width="320">
          <template #default="{ row }">
            <div class="order-cell" @click="router.push('/orders/' + row.id)">
              <div class="order-no">订单号：{{ row.orderNo }}</div>
              <div class="order-time">{{ formatDateTime(row.createTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="100" align="center">
          <template #default="{ row }">
            共 {{ row.totalQuantity || 0 }} 件
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="center">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="orderStatusTagType(row.status)">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="router.push('/orders/' + row.id)">详情</el-button>
            <el-button
              v-if="row.status === 4"
              size="small"
              link
              type="success"
              @click="router.push('/orders/' + row.id)"
            >
              去评价
            </el-button>
            <el-button v-if="row.status === 1" size="small" link type="danger" @click="onCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="loadOrders"
        />
      </div>

      <el-empty v-if="orders.length === 0 && !loading" description="暂无订单" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 20px;
}

.order-tabs {
  margin-bottom: 16px;
}

.order-cell {
  cursor: pointer;
}

.order-no {
  font-weight: 600;
  margin-bottom: 4px;
}

.order-time {
  font-size: 12px;
  color: var(--text-sub);
}

.price {
  color: var(--brand);
  font-weight: 600;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}
</style>
