<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrder, payOrder, cancelOrder, confirmOrder } from '@/api/order'
import { getReviewableItems } from '@/api/review'
import { orderStatusText, orderStatusTagType, formatPrice, formatDateTime } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(true)
const reviewableItems = ref([])

const timeline = computed(() => {
  if (!order.value) return []
  const steps = [
    { time: order.value.createTime, text: '提交订单', done: true },
    { time: order.value.payTime, text: '支付成功', done: order.value.status >= 2 },
    { time: null, text: '商家发货', done: order.value.status >= 3 },
    { time: null, text: '确认收货', done: order.value.status >= 4 }
  ]
  if (order.value.status === 5) {
    return [{ time: order.value.createTime, text: '订单已取消', done: true }]
  }
  return steps
})

async function loadOrder() {
  loading.value = true
  try {
    order.value = await getOrder(route.params.id)
    if (order.value.status === 4) {
      try {
        reviewableItems.value = await getReviewableItems(route.params.id)
      } catch (e) {
        /* 静默 */
      }
    }
  } catch (e) {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

async function onPay() {
  try {
    await ElMessageBox.confirm('确认支付该订单？（演示为模拟支付）', '提示', { type: 'warning' })
    await payOrder(order.value.id)
    ElMessage.success('支付成功')
    loadOrder()
  } catch (e) {
    /* 取消 */
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    loadOrder()
  } catch (e) {
    /* 取消 */
  }
}

async function onConfirm() {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '提示', { type: 'warning' })
    await confirmOrder(order.value.id)
    ElMessage.success('已确认收货')
    loadOrder()
  } catch (e) {
    /* 取消 */
  }
}

onMounted(loadOrder)
</script>

<template>
  <div class="page-container order-detail" v-loading="loading">
    <template v-if="order">
      <div class="detail-header card">
        <div class="header-left">
          <h2>订单详情</h2>
          <div class="order-no">订单号：{{ order.orderNo }}</div>
        </div>
        <div class="header-right">
          <el-tag :type="orderStatusTagType(order.status)" size="large">{{ orderStatusText(order.status) }}</el-tag>
          <div class="actions">
            <el-button v-if="order.status === 1" type="primary" @click="onPay">立即支付</el-button>
            <el-button v-if="order.status === 1" @click="onCancel">取消订单</el-button>
            <el-button v-if="order.status === 3" type="success" @click="onConfirm">确认收货</el-button>
          </div>
        </div>
      </div>

      <!-- 订单时间线 -->
      <div class="section card">
        <h3 class="section-title">订单状态</h3>
        <el-steps :active="timeline.filter(s => s.done).length" finish-status="success">
          <el-step
            v-for="(step, idx) in timeline"
            :key="idx"
            :title="step.text"
            :description="step.time ? formatDateTime(step.time) : ''"
          />
        </el-steps>
      </div>

      <!-- 收货地址 -->
      <div class="section card">
        <h3 class="section-title">收货信息</h3>
        <div class="address-box">
          <el-icon :size="20" color="var(--brand)"><Location /></el-icon>
          <p>{{ order.addressSnapshot }}</p>
        </div>
      </div>

      <!-- 商品清单 -->
      <div class="section card">
        <h3 class="section-title">商品清单</h3>
        <el-table :data="order.items || []" style="width: 100%">
          <el-table-column label="商品" min-width="320">
            <template #default="{ row }">
              <div class="book-cell" @click="router.push('/books/' + row.bookId)">
                <img :src="row.bookCover || placeholderCover(row.bookTitle)" class="book-cover" />
                <div class="book-info">
                  <p class="book-title text-ellipsis-2">{{ row.bookTitle }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120" align="center">
            <template #default="{ row }">{{ formatPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="center">
            <template #default="{ row }">{{ row.quantity }}</template>
          </el-table-column>
          <el-table-column label="小计" width="120" align="center">
            <template #default="{ row }">
              <span class="price">{{ formatPrice(row.subtotal) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="order.status === 4" label="评价" width="120" align="center">
            <template #default="{ row }">
              <span v-if="reviewableItems.find(r => r.bookId === row.bookId)?.reviewed" class="reviewed">已评价</span>
              <span v-else class="not-reviewed">未评价</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 金额汇总 -->
      <div class="section card amount-section">
        <div class="amount-row">
          <span>商品总额：</span>
          <span>{{ formatPrice(order.totalAmount) }}</span>
        </div>
        <div class="amount-row">
          <span>运费：</span>
          <span class="free">免运费</span>
        </div>
        <div class="amount-row total">
          <span>实付金额：</span>
          <span class="total-price">{{ formatPrice(order.totalAmount) }}</span>
        </div>
      </div>
    </template>

    <el-result v-else-if="!loading" icon="warning" title="订单不存在" sub-title="该订单可能不存在或无权查看">
      <template #extra>
        <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  margin-bottom: 16px;
}

.header-left h2 {
  font-size: 20px;
  margin-bottom: 6px;
}

.order-no {
  font-size: 13px;
  color: var(--text-sub);
}

.header-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.actions {
  display: flex;
  gap: 8px;
}

.section {
  padding: 20px 24px;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.address-box {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: var(--bg-fill);
  padding: 14px 16px;
  border-radius: var(--radius-md);
}

.address-box p {
  color: var(--text-regular);
  line-height: 1.6;
}

.book-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
}

.book-cover {
  width: 56px;
  height: 74px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.book-info {
  flex: 1;
}

.book-title {
  font-size: 14px;
  line-height: 1.4;
}

.price {
  color: var(--brand);
  font-weight: 600;
}

.reviewed {
  color: #67c23a;
}

.not-reviewed {
  color: var(--text-sub);
}

.amount-section {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.amount-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
  max-width: 320px;
  margin-bottom: 10px;
  font-size: 14px;
  color: var(--text-regular);
}

.amount-row.total {
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
  font-size: 16px;
  color: var(--text-primary);
}

.free {
  color: #67c23a;
}

.total-price {
  color: var(--brand);
  font-size: 24px;
  font-weight: 700;
}
</style>
