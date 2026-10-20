<template>
  <view class="page">
    <page-nav title="我的订单" />
    <!-- 状态筛选 -->
    <view class="tabs" :style="{ top: navTop + 'px' }">
      <view
        v-for="t in tabs"
        :key="String(t.value)"
        class="tab"
        :class="{ on: status === t.value }"
        hover-class="hover-opacity"
        @tap="switchTab(t.value)"
      >{{ t.label }}</view>
    </view>

    <!-- 加载骨架 -->
    <view v-if="loading" class="list">
      <view v-for="n in 2" :key="n" class="card">
        <view class="sk sk-anim" style="height: 26rpx; width: 55%" />
        <view class="goods" style="margin-top: 24rpx">
          <view class="cover sk sk-anim" />
          <view class="goods-info">
            <view class="sk sk-anim" style="height: 28rpx; width: 80%" />
            <view class="sk sk-anim" style="height: 24rpx; width: 45%; margin-top: 16rpx" />
          </view>
        </view>
      </view>
    </view>

    <!-- 请求失败 -->
    <view v-else-if="error" class="state-box">
      <view class="state-icon state-icon-error">!</view>
      <text class="state-title">订单加载失败</text>
      <text class="state-sub">请检查网络后重试</text>
      <view class="state-btn" hover-class="hover-opacity" @tap="retry">重新加载</view>
    </view>

    <template v-else>
      <view v-if="list.length" class="list">
        <view
          v-for="o in list"
          :key="o.id"
          class="card"
          hover-class="hover-dim"
          @tap="goDetail(o.id)"
        >
          <view class="card-top">
            <text class="order-no ellipsis">订单号 {{ o.orderNo }}</text>
            <text class="status" :class="statusClass(o.status)">{{ o.statusText }}</text>
          </view>
          <view v-for="it in showItems(o)" :key="it.bookId" class="goods">
            <image class="cover" :src="coverSrc(it.bookCover)" mode="aspectFill" />
            <view class="goods-info">
              <text class="title ellipsis">{{ it.bookTitle }}</text>
              <text class="sub">¥{{ price(it.price) }} × {{ it.quantity }}</text>
            </view>
          </view>
          <text v-if="(o.items || []).length > MAX_SHOW" class="more-goods">
            等 {{ o.items.length }} 件商品
          </text>
          <view class="card-bottom">
            <text class="time">{{ dateText(o.createTime) }}</text>
            <text class="total">共 {{ o.totalQuantity || 0 }} 件 合计 ¥{{ price(o.totalAmount) }}</text>
          </view>
          <view v-if="o.status === 1 || o.status === 3" class="ops">
            <view
              v-if="o.status === 1"
              class="op-btn"
              :class="{ disabled: payingId === o.id }"
              hover-class="hover-opacity"
              @tap.stop="onPay(o)"
            >
              {{ payingId === o.id ? '支付中...' : '去支付' }}
            </view>
            <view
              v-else
              class="op-btn"
              :class="{ disabled: confirmingId === o.id }"
              hover-class="hover-opacity"
              @tap.stop="onConfirm(o)"
            >
              {{ confirmingId === o.id ? '处理中...' : '确认收货' }}
            </view>
          </view>
        </view>
        <view v-if="noMore" class="load-more">— 没有更多订单了 —</view>
      </view>

      <!-- 空态：按当前筛选给不同引导 -->
      <view v-else class="state-box">
        <view class="state-icon">📦</view>
        <text class="state-title">暂无相关订单</text>
        <text class="state-sub">{{ status === '' ? '去挑几本好书吧' : '换一个状态看看' }}</text>
        <view class="state-btn" hover-class="hover-opacity" @tap="goHome">去逛逛</view>
      </view>
    </template>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow, onReachBottom } from '@dcloudio/uni-app'
import { pageOrders, payOrder, confirmOrder } from '@/api/order'
import { requireLogin } from '@/common/guard'
import { coverSrc } from '@/common/config'
import PageNav from '@/components/page-nav.vue'

/** 吸顶 tab 的 top 偏移：让出固定导航的高度（状态栏 + 88rpx 折算 px） */
const sys = uni.getSystemInfoSync()
const navTop = (sys.statusBarHeight || 0) + Math.round((88 * (sys.windowWidth || 375)) / 750)
import { price, dateText } from '@/common/format'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

/** 状态与后端 Orders.status 一致：1待支付 2已支付 3已发货 4已完成 5已取消。
 *  文案按用户视角写（待发货 / 待收货），与「我的」页订单宫格保持同一套命名 */
const tabs = [
  { label: '全部', value: '' },
  { label: '待支付', value: 1 },
  { label: '待发货', value: 2 },
  { label: '待收货', value: 3 },
  { label: '已完成', value: 4 }
]

/** 列表页最多展示的商品行数，超出用「等 N 件商品」兜底，避免订单过长 */
const MAX_SHOW = 2

function showItems(o) {
  return (o.items || []).slice(0, MAX_SHOW)
}

/** 状态色：待支付用品牌红，其余按语义色推进，形成「进度感」 */
function statusClass(status) {
  return 's' + status
}

const status = ref('')
const list = ref([])
const pageNum = ref(1)
const noMore = ref(false)
const loading = ref(true)
const error = ref('')
/** 正在支付的订单 id，防止连点重复发起支付 */
const payingId = ref(null)
/** 正在确认收货的订单 id */
const confirmingId = ref(null)

async function load(reset = true) {
  if (reset) {
    pageNum.value = 1
    noMore.value = false
    loading.value = true
    error.value = ''
  }
  try {
    const params = { pageNum: pageNum.value, pageSize: 10 }
    if (status.value !== '') params.status = status.value
    const data = await pageOrders(params)
    const rows = data.list || []
    list.value = reset ? rows : list.value.concat(rows)
    noMore.value = pageNum.value >= (data.pages || 1)
    error.value = ''
  } catch (e) {
    if (reset) error.value = '加载失败'
  } finally {
    loading.value = false
  }
}

function retry() {
  load(true)
}

function switchTab(value) {
  if (status.value === value) return
  status.value = value
  load(true)
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/order/detail?id=' + id })
}

/**
 * 模拟支付。
 *
 * 防重两道：payingId 锁住当前订单 + 请求期间 showLoading mask，
 * 避免网络慢时用户连点造成重复支付回调。
 */
async function onPay(o) {
  if (payingId.value) return
  uni.showModal({
    title: '模拟支付',
    content: `确认支付 ¥${price(o.totalAmount)} 吗？（毕设演示，不涉及真实交易）`,
    success: async (res) => {
      if (!res.confirm) return
      payingId.value = o.id
      uni.showLoading({ title: '支付中', mask: true })
      try {
        await payOrder(o.id)
        uni.hideLoading()
        uni.showToast({ title: '支付成功', icon: 'success' })
        await load(true)
      } catch (e) {
        uni.hideLoading()
      } finally {
        payingId.value = null
      }
    }
  })
}

/** 确认收货：刷新列表即生效，订单会从「待收货」移出 */
async function onConfirm(o) {
  if (confirmingId.value) return
  uni.showModal({
    title: '确认收货',
    content: '请确认已收到商品，确认后订单将变更为「已完成」。',
    success: async (res) => {
      if (!res.confirm) return
      confirmingId.value = o.id
      uni.showLoading({ title: '处理中', mask: true })
      try {
        await confirmOrder(o.id)
        uni.hideLoading()
        uni.showToast({ title: '已确认收货', icon: 'success' })
        await load(true)
      } catch (e) {
        uni.hideLoading()
      } finally {
        confirmingId.value = null
      }
    }
  })
}

function goHome() {
  // 底部导航已改为自绘，原生 switchTab 失效，tab 级页面统一用 redirectTo
  uni.redirectTo({ url: '/pages/index/index' })
}

onLoad((options) => {
  // H5 可以用 URL 直达本页，未登录时先进登录页（登录后回跳），不要等接口 401
  if (!requireLogin()) return
  if (options && options.status !== undefined && options.status !== '') {
    status.value = Number(options.status)
  }
})

onShow(() => {
  if (!requireLogin()) return
  load(true)
})

onReachBottom(() => {
  if (noMore.value || loading.value) return
  pageNum.value += 1
  load(false)
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
}

.tabs {
  position: sticky;
  /* top 由脚本按导航高度动态内联（navTop），避开自绘导航的遮挡 */
  z-index: 9;
  display: flex;
  flex-direction: row;
  background-color: $bg-card;
  border-bottom: 1rpx solid $border-color;
}

.tab {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-base;
  color: $text-regular;
}

.tab.on {
  color: $brand;
  font-weight: 600;
  border-bottom: 4rpx solid $brand;
}

.list {
  padding: $gap-item $gap-page 0;
}

.card {
  padding: $gap-card;
  margin-bottom: $gap-item;
  border-radius: $radius-lg;
  background-color: $bg-card;
  border: 1rpx solid $border-color;
}

.card-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.order-no {
  flex: 1;
  min-width: 0;
  font-size: $fs-sm;
  color: $text-sub;
}

.status {
  flex-shrink: 0;
  margin-left: $sp-2;
  font-size: $fs-base;
  font-weight: 600;
  color: $text-regular;
}

/* 状态色：形成下单 → 支付 → 发货 → 完成的进度感 */
.status.s1 {
  color: $brand;
}

.status.s2 {
  color: $info;
}

.status.s3 {
  color: $warning;
}

.status.s4 {
  color: $success;
}

.status.s5 {
  color: $text-sub;
  font-weight: 400;
}

.goods {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: $sp-3;
}

.cover {
  width: 110rpx;
  height: 146rpx;
  flex-shrink: 0;
  border-radius: $radius-sm;
  background-color: $bg-skeleton;
}

.goods-info {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
}

.title {
  font-size: $fs-base;
  color: $text-main;
}

.sub {
  margin-top: 10rpx;
  font-size: $fs-sm;
  color: $text-sub;
}

.more-goods {
  display: block;
  margin-top: $sp-2;
  font-size: $fs-sm;
  color: $text-sub;
}

.card-bottom {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-top: $sp-3;
  padding-top: 18rpx;
  border-top: 1rpx solid $border-color;
}

.time {
  font-size: $fs-sm;
  color: $text-sub;
}

.total {
  font-size: $fs-base;
  color: $text-main;
}

.ops {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  margin-top: 18rpx;
}

.op-btn {
  height: 64rpx;
  min-width: 168rpx;
  padding: 0 $sp-4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-base;
  font-weight: 600;
  color: $brand;
  background-color: $brand-weak;
  border-radius: $radius-btn;
}

.op-btn.disabled {
  color: $color-white;
  background-color: $border-strong;
}
</style>
