<template>
  <view class="page">
    <page-nav title="订单详情" />
    <!-- 加载骨架 -->
    <view v-if="loading" class="skeleton">
      <view class="status-bar sk sk-anim" style="border-radius: 0" />
      <view class="card">
        <view class="sk sk-anim" style="height: 32rpx; width: 30%" />
        <view class="sk sk-anim" style="height: 28rpx; width: 85%; margin-top: 20rpx" />
      </view>
    </view>

    <!-- 越权访问他人订单：后端返回 403，与「订单不存在」区分开 -->
    <view v-else-if="forbidden" class="state-box">
      <view class="state-icon state-icon-error">!</view>
      <text class="state-title">无权查看该订单</text>
      <view class="state-btn" hover-class="hover-opacity" @tap="goOrderList">返回订单列表</view>
    </view>

    <!-- 订单不存在 -->
    <view v-else-if="!order.id" class="state-box">
      <view class="state-icon state-icon-error">!</view>
      <text class="state-title">订单不存在或已被删除</text>
      <view class="state-btn" hover-class="hover-opacity" @tap="goOrderList">返回订单列表</view>
    </view>

    <template v-else>
      <!-- ① 状态头：颜色随订单状态推进 -->
      <view class="status-bar" :class="'bg-s' + order.status">
        <text class="status-text">{{ order.statusText }}</text>
        <text v-if="order.status === 1" class="status-tip">请尽快完成支付</text>
        <text v-else-if="order.status === 3" class="status-tip">商品已发出，收货后请在下方确认</text>
        <text v-else-if="order.payTime" class="status-tip">
          支付时间 {{ dateText(order.payTime) }}
        </text>
      </view>

      <!-- ② 收货信息 -->
      <view class="card">
        <view class="card-title">收货信息</view>
        <text class="addr">{{ order.addressSnapshot }}</text>
      </view>

      <!-- ③ 商品明细 -->
      <view class="card">
        <view class="card-title">商品明细</view>
        <view v-for="it in order.items || []" :key="it.bookId" class="goods">
          <image
            class="cover"
            :src="coverSrc(it.bookCover)"
            mode="aspectFill"
            @tap="goBook(it.bookId)"
          />
          <view class="goods-info">
            <text class="title ellipsis-2">{{ it.bookTitle }}</text>
            <view class="goods-bottom">
              <text class="price">¥{{ price(it.price) }}</text>
              <text class="qty">×{{ it.quantity }}</text>
            </view>
            <!-- 只有「已完成」订单可评价：后端按订单状态 + 是否已评价双向校验 -->
            <view v-if="order.status === 4" class="goods-actions">
              <view
                v-if="reviewOf(it.bookId).reviewed"
                class="rv-done"
                hover-class="hover-opacity"
                @tap="goBook(it.bookId)"
              >
                <text class="rv-done-stars">{{ starStr(reviewOf(it.bookId).rating) }}</text>
                <text class="rv-done-text">已评价 ›</text>
              </view>
              <view v-else class="rv-btn" hover-class="hover-opacity" @tap="goReview(it)">
                去评价
              </view>
            </view>
          </view>
        </view>
        <view class="sum-row">
          <text class="sum-label">共 {{ order.totalQuantity || 0 }} 件</text>
          <text class="price sum-price">¥{{ price(order.totalAmount) }}</text>
        </view>
      </view>

      <!-- ④ 订单信息 -->
      <view class="card">
        <view class="card-title">订单信息</view>
        <view class="info-row">
          <text class="info-label">订单编号</text>
          <text class="info-value">{{ order.orderNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">下单时间</text>
          <text class="info-value">{{ dateText(order.createTime) }}</text>
        </view>
        <view v-if="order.payTime" class="info-row">
          <text class="info-label">支付时间</text>
          <text class="info-value">{{ dateText(order.payTime) }}</text>
        </view>
        <view class="info-row no-border">
          <text class="info-label">订单状态</text>
          <text class="info-value" :class="'s' + order.status">{{ order.statusText }}</text>
        </view>
      </view>

      <view class="bar-holder" />

      <!-- ⑤ 底部操作：待支付可支付，已发货可确认收货 -->
      <view v-if="order.status === 1 || order.status === 3" class="footer">
        <view
          v-if="order.status === 1"
          class="pay-btn"
          :class="{ disabled: paying }"
          hover-class="hover-opacity"
          @tap="onPay"
        >
          {{ paying ? '支付中...' : '立即支付 ¥' + price(order.totalAmount) }}
        </view>
        <view v-else class="pay-btn" :class="{ disabled: confirming }" hover-class="hover-opacity" @tap="onConfirm">
          {{ confirming ? '处理中...' : '确认收货' }}
        </view>
      </view>
    </template>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrder, payOrder, confirmOrder } from '@/api/order'
import { getReviewableItems } from '@/api/review'
import { requireLogin } from '@/common/guard'
import { coverSrc } from '@/common/config'
import { price, dateText } from '@/common/format'
import PageNav from '@/components/page-nav.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const order = ref({})
const orderId = ref(null)
const loading = ref(true)
/** 后端返回 403：订单存在但不属于当前用户 */
const forbidden = ref(false)
const paying = ref(false)
const confirming = ref(false)
/** bookId -> ReviewableItemVO：已完成订单里每本书的评价状态 */
const reviewMap = ref({})

function reviewOf(bookId) {
  return reviewMap.value[String(bookId)] || {}
}

function starStr(n) {
  const v = Math.max(0, Math.min(5, Number(n) || 0))
  return '★'.repeat(v) + '☆'.repeat(5 - v)
}

/** 已完成订单才拉评价状态：其余状态拉了也用不上，白跑一次请求 */
async function loadReviewables() {
  if (order.value.status !== 4) {
    reviewMap.value = {}
    return
  }
  try {
    const items = (await getReviewableItems(orderId.value)) || []
    const map = {}
    items.forEach((i) => {
      map[String(i.bookId)] = i
    })
    reviewMap.value = map
  } catch (e) {
    reviewMap.value = {}
  }
}

async function load() {
  loading.value = true
  forbidden.value = false
  try {
    order.value = (await getOrder(orderId.value)) || {}
    await loadReviewables()
  } catch (e) {
    order.value = {}
    // 改 URL 里的订单号看别人的订单时后端返回 403，这里给出明确文案
    if (e && e.code === 403) forbidden.value = true
  } finally {
    loading.value = false
  }
}

/** 从评价页返回时刷新：onLoad 不会再次触发，靠 onShow 让按钮就地变「已评价」 */
onShow(() => {
  if (orderId.value) loadReviewables()
})

/**
 * 模拟支付。
 *
 * paying 锁 + showLoading mask 双保险，避免慢网络下连点造成重复支付。
 * 支付成功后重新拉取详情，让状态头与时间就地更新。
 */
async function onPay() {
  if (paying.value) return
  uni.showModal({
    title: '模拟支付',
    content: `确认支付 ¥${price(order.value.totalAmount)} 吗？（毕设演示，不涉及真实交易）`,
    success: async (res) => {
      if (!res.confirm) return
      paying.value = true
      uni.showLoading({ title: '支付中', mask: true })
      try {
        await payOrder(orderId.value)
        uni.hideLoading()
        uni.showToast({ title: '支付成功', icon: 'success' })
        await load()
      } catch (e) {
        uni.hideLoading()
      } finally {
        paying.value = false
      }
    }
  })
}

/**
 * 确认收货：订单流转到「已完成」后才能评价。
 * 成功后重新拉取详情，状态头、可评价按钮与评价入口会一起就位。
 */
async function onConfirm() {
  if (confirming.value) return
  uni.showModal({
    title: '确认收货',
    content: '请确认已收到商品，确认后订单将变更为「已完成」。',
    success: async (res) => {
      if (!res.confirm) return
      confirming.value = true
      uni.showLoading({ title: '处理中', mask: true })
      try {
        await confirmOrder(orderId.value)
        uni.hideLoading()
        uni.showToast({ title: '已确认收货', icon: 'success' })
        await load()
      } catch (e) {
        uni.hideLoading()
      } finally {
        confirming.value = false
      }
    }
  })
}

function goBook(bookId) {
  if (!bookId) return
  uni.navigateTo({ url: '/pages/book/detail?id=' + bookId })
}

function goReview(it) {
  if (!orderId.value || !it.bookId) return
  uni.navigateTo({
    url:
      '/pages/review/edit?orderId=' +
      orderId.value +
      '&bookId=' +
      it.bookId +
      '&title=' +
      encodeURIComponent(it.bookTitle || '')
  })
}

function goOrderList() {
  uni.redirectTo({ url: '/pages/order/list' })
}

onLoad((options) => {
  if (!requireLogin()) return
  orderId.value = options && options.id
  if (orderId.value) {
    load()
  } else {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  box-sizing: border-box;
}

.status-bar {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: $sp-6 $sp-5;
  background: linear-gradient(135deg, $brand, $brand-light);
}

/* 状态色随 1待支付 2已支付 3已发货 4已完成 5已取消 推进 */
.status-bar.bg-s2 {
  background: linear-gradient(135deg, $info, $info-light);
}

.status-bar.bg-s3 {
  background: linear-gradient(135deg, $warning, $warning-light);
}

.status-bar.bg-s4 {
  background: linear-gradient(135deg, $success, $success-light);
}

.status-bar.bg-s5 {
  background: linear-gradient(135deg, $neutral, $neutral-light);
}

.status-text {
  font-size: $fs-2xl;
  font-weight: 600;
  color: $color-white;
}

.status-tip {
  margin-top: 12rpx;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.85);
}

.card {
  margin: $gap-item $gap-page 0;
  padding: $gap-card;
  border-radius: $radius-lg;
  background-color: $bg-card;
  box-shadow: $shadow-card;
}

.card-title {
  margin-bottom: 18rpx;
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-main;
}

.addr {
  font-size: $fs-base;
  line-height: 1.7;
  color: $text-regular;
}

.goods {
  display: flex;
  flex-direction: row;
  padding: 18rpx 0;
  border-top: 1rpx solid $border-color;
}

.cover {
  width: 130rpx;
  height: 173rpx;
  flex-shrink: 0;
  border-radius: $radius-sm;
  background-color: $bg-skeleton;
}

.goods-info {
  flex: 1;
  min-width: 0;
  margin-left: $sp-2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.title {
  font-size: $fs-md;
  line-height: 1.4;
  color: $text-main;
}

.goods-actions {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  margin-top: 12rpx;
}

.rv-btn {
  height: 56rpx;
  padding: 0 $sp-4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-sm;
  font-weight: 600;
  color: $brand;
  border: 1rpx solid $brand;
  border-radius: $radius-pill;
}

.rv-done {
  height: 56rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.rv-done-stars {
  font-size: $fs-xs;
  letter-spacing: 2rpx;
  color: $warning;
}

.rv-done-text {
  margin-left: $sp-1;
  font-size: $fs-sm;
  color: $text-sub;
}

.goods-bottom {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10rpx;
}

.price {
  font-size: $fs-lg;
}

.qty {
  font-size: $fs-base;
  color: $text-sub;
}

.sum-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 12rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid $border-color;
}

.sum-label {
  font-size: $fs-base;
  color: $text-regular;
}

.sum-price {
  font-size: 34rpx;
  font-weight: 700;
}

.info-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  padding: 14rpx 0;
  border-top: 1rpx solid $border-color;
}

.info-row.no-border {
  border-top: none;
}

.info-label {
  font-size: $fs-base;
  color: $text-sub;
}

.info-value {
  font-size: $fs-base;
  color: $text-main;
}

.info-value.s1 {
  color: $brand;
  font-weight: 600;
}

.info-value.s2 {
  color: $info;
}

.info-value.s3 {
  color: $warning;
}

.info-value.s4 {
  color: $success;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: $sp-2 $gap-page;
  padding-bottom: calc(#{$sp-2} + #{$safe-bottom});
  background-color: $bg-card;
  box-shadow: $shadow-float;
  z-index: 20;
}

.pay-btn {
  height: $btn-h;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-pill;
}

.pay-btn.disabled {
  background-color: $border-strong;
}
</style>
