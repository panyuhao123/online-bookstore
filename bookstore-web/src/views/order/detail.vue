<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrder, payOrder, cancelOrder, confirmOrder } from '@/api/order'
import { getReviewableItems, createReview } from '@/api/review'
import { orderStatusText, orderStatusTagType, formatPrice, formatDateTime } from '@/utils/format'
import { exportReceipt } from '@/utils/excel'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(true)
const reviewableItems = ref([])

// 发表评价
const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewForm = reactive({
  bookId: null,
  bookTitle: '',
  rating: 5,
  content: '',
  anonymous: false
})

/**
 * 订单时间线（N3）。
 *
 * <p>四个节点的耗时字段是分开的：`createTime` / `payTime` / `shipTime` / `finishTime`。
 * 早先只有前两个能显示，发货和收货的节点永远空着 —— 用户看到"已发货"却没有任何时间，
 * 会怀疑是不是没真发。W1 给 orders 补了这两列，这里接上。
 */
const timeline = computed(() => {
  if (!order.value) return []
  const o = order.value
  if (o.status === 5) {
    return [{ time: o.createTime, text: '订单已取消', done: true }]
  }
  return [
    { time: o.createTime, text: '提交订单', done: true },
    { time: o.payTime, text: '支付成功', done: o.status >= 2 },
    { time: o.shipTime, text: '商家发货', done: o.status >= 3 },
    { time: o.finishTime, text: '确认收货', done: o.status >= 4 }
  ]
})

const doneStepCount = computed(() => timeline.value.filter((s) => s.done).length)

/** 小票导出（N8）：把这一单导成 Excel，含订单信息、明细与三项金额 */
function onExportReceipt() {
  if (!order.value) return
  exportReceipt(order.value, `小票_${order.value.orderNo || order.value.id}`)
}

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

/** 该商品是否已评价（后端按 orderId + bookId 判定） */
function isReviewed(bookId) {
  return !!reviewableItems.value.find((r) => r.bookId === bookId)?.reviewed
}

function openReview(row) {
  reviewForm.bookId = row.bookId
  reviewForm.bookTitle = row.bookTitle
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewForm.anonymous = false
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewForm.rating) {
    ElMessage.warning('请先选择评分')
    return
  }
  reviewSubmitting.value = true
  try {
    await createReview({
      orderId: order.value.id,
      bookId: reviewForm.bookId,
      rating: reviewForm.rating,
      content: reviewForm.content || undefined,
      anonymous: reviewForm.anonymous
    })
    ElMessage.success('评价发表成功')
    reviewVisible.value = false
    // 重新拉一次评价状态，把该行从「未评价」改成「已评价」
    reviewableItems.value = await getReviewableItems(route.params.id).catch(
      () => reviewableItems.value
    )
  } catch (e) {
    /* 重复评价后端返回 409，拦截器已提示 */
  } finally {
    reviewSubmitting.value = false
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
            <el-button @click="onExportReceipt">
              <el-icon><Download /></el-icon> 导出小票
            </el-button>
          </div>
        </div>
      </div>

      <!-- 订单时间线 -->
      <div class="section card">
        <h3 class="section-title">订单状态</h3>
        <el-steps :active="doneStepCount" finish-status="success" align-center>
          <el-step
            v-for="(step, idx) in timeline"
            :key="idx"
            :title="step.text"
            :description="step.time ? formatDateTime(step.time) : '—'"
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
              <span v-if="isReviewed(row.bookId)" class="reviewed">
                <el-icon><Select /></el-icon> 已评价
              </span>
              <el-button v-else size="small" type="primary" link @click="openReview(row)">
                发表评价
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 金额汇总：商品小计 + 运费 = 应付，口径与结算页一致 -->
      <div class="section card amount-section">
        <div class="amount-row">
          <span>商品总额：</span>
          <span>{{ formatPrice(order.totalAmount) }}</span>
        </div>
        <div class="amount-row">
          <span>运费：</span>
          <span v-if="Number(order.freightAmount || 0) > 0" class="freight">
            {{ formatPrice(order.freightAmount) }}
          </span>
          <span v-else class="free">免运费</span>
        </div>
        <div class="amount-row total">
          <span>实付金额：</span>
          <span class="total-price">
            {{ formatPrice(order.payableAmount ?? order.totalAmount) }}
          </span>
        </div>
      </div>
    </template>

    <el-result v-else-if="!loading" icon="warning" title="订单不存在" sub-title="该订单可能不存在或无权查看">
      <template #extra>
        <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
      </template>
    </el-result>

    <!-- 发表评价 -->
    <el-dialog v-model="reviewVisible" title="发表评价" width="480px">
      <div class="review-book">
        <span class="review-book-label">图书</span>
        <span class="review-book-title">{{ reviewForm.bookTitle }}</span>
      </div>
      <el-form label-width="70px" label-position="left">
        <el-form-item label="评分" required>
          <el-rate v-model="reviewForm.rating" show-text :texts="['很差', '较差', '一般', '满意', '推荐']" />
        </el-form-item>
        <el-form-item label="评价">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="说说这本书值不值得读（选填）"
          />
        </el-form-item>
        <el-form-item label="匿名">
          <el-switch v-model="reviewForm.anonymous" />
          <span class="anon-tip">开启后其他用户看不到你的昵称</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
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
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.not-reviewed {
  color: var(--text-sub);
}

.review-book {
  display: flex;
  gap: 10px;
  align-items: baseline;
  background: var(--bg-fill);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  margin-bottom: 16px;
  font-size: 14px;
}

.review-book-label {
  color: var(--text-sub);
  font-size: 13px;
  flex-shrink: 0;
}

.review-book-title {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.5;
}

.anon-tip {
  margin-left: 10px;
  font-size: 12px;
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

.freight {
  color: var(--brand);
  font-weight: 600;
}

.total-price {
  color: var(--brand);
  font-size: 24px;
  font-weight: 700;
}
</style>
