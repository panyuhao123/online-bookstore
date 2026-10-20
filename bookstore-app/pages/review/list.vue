<template>
  <view class="page">
    <page-nav :title="isBookMode ? '用户评价' : '我的评价'" />
    <!-- ① 评分汇总：仅「某本书的评价」模式显示 -->
    <view v-if="isBookMode" class="card summary">
      <template v-if="summaryLoading">
        <view class="sk sk-anim" style="height: 90rpx; width: 40%" />
        <view class="sk sk-anim" style="height: 28rpx; width: 90%; margin-top: 20rpx" />
      </template>

      <template v-else>
        <view class="sum-top">
          <view class="sum-score">
            <text class="score">{{ hasReview ? avgText : '—' }}</text>
            <text class="stars stars-lg">{{ starStr(roundRating) }}</text>
            <text class="sum-total">{{ summary.reviewCount || 0 }} 条评价</text>
          </view>
          <view class="dist">
            <view v-for="d in distList" :key="d.rating" class="dist-row">
              <text class="dist-label">{{ d.rating }}星</text>
              <view class="dist-track">
                <view class="dist-fill" :style="{ width: barWidth(d.count) }" />
              </view>
              <text class="dist-num">{{ d.count }}</text>
            </view>
          </view>
        </view>
      </template>
    </view>

    <!-- ② 列表加载骨架 -->
    <view v-if="loading" class="card">
      <view v-for="n in 3" :key="n" class="rv-sk">
        <view class="sk sk-anim avatar-sk" />
        <view class="rv-sk-body">
          <view class="sk sk-anim" style="height: 28rpx; width: 35%" />
          <view class="sk sk-anim" style="height: 26rpx; width: 90%; margin-top: 16rpx" />
        </view>
      </view>
    </view>

    <!-- ③ 加载失败 -->
    <view v-else-if="loadError" class="state-box">
      <view class="state-icon state-icon-error">!</view>
      <text class="state-title">评价加载失败</text>
      <text class="state-sub">网络不稳定，请稍后重试</text>
      <view class="state-btn" hover-class="hover-opacity" @tap="reload">重新加载</view>
    </view>

    <!-- ④ 空态 -->
    <view v-else-if="!list.length" class="state-box">
      <view class="state-icon">{{ isBookMode ? '☆' : '✎' }}</view>
      <text class="state-title">{{ isBookMode ? '还没有人评价这本书' : '你还没有评价过图书' }}</text>
      <text class="state-sub">
        {{ isBookMode ? '购买并完成订单后即可发表评价' : '在已完成订单里给买过的书打个分吧' }}
      </text>
      <view
        v-if="!isBookMode"
        class="state-btn"
        hover-class="hover-opacity"
        @tap="goOrders"
      >
        去看已完成订单
      </view>
    </view>

    <!-- ⑤ 评价列表 -->
    <view v-else class="card list-card">
      <view
        v-for="r in list"
        :key="r.id"
        class="rv"
        :class="{ tappable: !isBookMode }"
        hover-class="hover-dim"
        @tap="onItemTap(r)"
      >
        <!-- 我的评价模式：带出行卡，便于回跳图书详情 -->
        <view v-if="!isBookMode" class="rv-book">
          <image class="rv-cover" :src="coverSrc(r.bookCoverUrl)" mode="aspectFill" />
          <text class="rv-book-title ellipsis">{{ r.bookTitle || '图书已下架' }}</text>
        </view>

        <view class="rv-head">
          <view class="avatar">{{ displayName(r)[0] }}</view>
          <view class="rv-who">
            <text class="rv-name">{{ displayName(r) }}</text>
            <text class="rv-time">{{ dateText(r.createTime) }}</text>
          </view>
          <text class="stars">{{ starStr(r.rating) }}</text>
        </view>

        <text v-if="r.content" class="rv-content">{{ r.content }}</text>
        <text v-else class="rv-content rv-content-empty">该用户未填写评价内容</text>
      </view>

      <view v-if="noMore" class="load-more">没有更多了</view>
    </view>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import {
  pageBookReviews,
  getReviewSummary,
  pageMyReviews
} from '@/api/review'
import { coverSrc } from '@/common/config'
import { getUser } from '@/common/auth'
import PageNav from '@/components/page-nav.vue'
import { dateText } from '@/common/format'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const PAGE_SIZE = 10

const bookId = ref(null)
const list = ref([])
const loading = ref(true)
const loadError = ref(false)
const pageNum = ref(1)
const pages = ref(1)
const noMore = ref(false)
/** 上拉分页请求锁：慢网络下 onReachBottom 会连续触发 */
const loadingMore = ref(false)

const summary = ref({})
const summaryLoading = ref(true)

/** 带 bookId 进入 = 某本书的评价；不带 = 我的评价 */
const isBookMode = computed(() => !!bookId.value)

const hasReview = computed(() => (summary.value.reviewCount || 0) > 0)

/** 平均分可能来自 BigDecimal（数字或字符串），统一转数值再展示 */
const avgText = computed(() => {
  const n = Number(summary.value.avgRating)
  return isNaN(n) ? '—' : n.toFixed(1)
})

/** 平均分四舍五入到整星再画：4.5 分画 5 颗实心星，视觉上比半星更好读 */
const roundRating = computed(() => Math.round(Number(summary.value.avgRating) || 0))

/** 后端固定返回 5→1 星五个元素；缺失时按 0 处理，保证分布条始终是 5 行 */
const distList = computed(() => {
  const raw = summary.value.distribution || []
  return [5, 4, 3, 2, 1].map((rating) => {
    const hit = raw.find((d) => Number(d.rating) === rating)
    return { rating, count: hit ? Number(hit.count) || 0 : 0 }
  })
})

/**
 * 展示名。
 *
 * 「某本书的评价」由服务端 JOIN 并在库内完成匿名替换，直接用 nickname；
 * 「我的评价」接口不回 nickname（自己的评价没必要再查一次用户表），
 * 这里按 anonymous 决定显示「匿名用户」还是本机昵称。
 */
function displayName(r) {
  if (r.nickname) return r.nickname
  return Number(r.anonymous) === 1 ? '匿名用户' : (getUser() || {}).nickname || '我'
}

function barWidth(count) {
  const total = Number(summary.value.reviewCount) || 0
  if (!total || !count) return '0%'
  return Math.round((count / total) * 100) + '%'
}

/** 实心星 + 空心星字符串：比渲染 10 个节点更省，跨端也无字体差异 */
function starStr(n) {
  const v = Math.max(0, Math.min(5, Number(n) || 0))
  return '★'.repeat(v) + '☆'.repeat(5 - v)
}

async function loadSummary() {
  if (!bookId.value) return
  summaryLoading.value = true
  try {
    summary.value = (await getReviewSummary(bookId.value)) || {}
  } catch (e) {
    summary.value = {}
  } finally {
    summaryLoading.value = false
  }
}

async function fetchPage(page) {
  const params = { pageNum: page, pageSize: PAGE_SIZE }
  return isBookMode.value ? pageBookReviews(bookId.value, params) : pageMyReviews(params)
}

async function load(page = 1) {
  if (page === 1) {
    loading.value = true
    loadError.value = false
  }
  try {
    const data = await fetchPage(page)
    const rows = (data && data.list) || []
    list.value = page === 1 ? rows : list.value.concat(rows)
    pageNum.value = (data && data.pageNum) || page
    pages.value = (data && data.pages) || 1
    noMore.value = pageNum.value >= pages.value
  } catch (e) {
    if (page === 1) loadError.value = true
  } finally {
    loading.value = false
  }
}

function reload() {
  load(1)
  if (isBookMode.value) loadSummary()
}

async function loadMore() {
  if (loadingMore.value || loading.value || noMore.value) return
  loadingMore.value = true
  await load(pageNum.value + 1)
  loadingMore.value = false
}

/** 我的评价：点整条卡片回跳图书详情（图书模式无意义，故不响应） */
function onItemTap(r) {
  if (isBookMode.value || !r.bookId) return
  uni.navigateTo({ url: '/pages/book/detail?id=' + r.bookId })
}

function goOrders() {
  uni.navigateTo({ url: '/pages/order/list?status=4' })
}

onLoad((options) => {
  const id = options && options.bookId
  bookId.value = id || null
  // 同一页面两种用途：标题由 page-nav 的 :title 随 isBookMode 响应式切换
  load(1)
  if (isBookMode.value) loadSummary()
})

onReachBottom(() => {
  loadMore()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: $sp-6;
}

.card {
  margin: $gap-item $gap-page;
  padding: $gap-card;
  border-radius: $radius-lg;
  background-color: $bg-card;
  border: 1rpx solid $border-color;
}

/* ---- 评分汇总 ---- */
.sum-top {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.sum-score {
  flex-shrink: 0;
  width: 200rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score {
  font-size: 60rpx;
  font-weight: 700;
  line-height: 1.1;
  color: $brand;
}

.sum-total {
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-sub;
}

.stars {
  margin-top: 6rpx;
  font-size: $fs-sm;
  letter-spacing: 2rpx;
  color: $warning;
}

.stars-lg {
  font-size: 30rpx;
}

.dist {
  flex: 1;
  min-width: 0;
}

.dist-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 34rpx;
}

.dist-label {
  flex-shrink: 0;
  width: 56rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.dist-track {
  flex: 1;
  min-width: 0;
  height: 12rpx;
  border-radius: $radius-pill;
  background-color: $bg-fill;
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  border-radius: $radius-pill;
  background-color: $warning;
}

.dist-num {
  flex-shrink: 0;
  width: 60rpx;
  text-align: right;
  font-size: $fs-xs;
  color: $text-sub;
}

/* ---- 骨架 ---- */
.rv-sk {
  display: flex;
  flex-direction: row;
  padding: $sp-2 0;
}

.rv-sk + .rv-sk {
  border-top: 1rpx solid $border-color;
}

.avatar-sk {
  flex-shrink: 0;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
}

.rv-sk-body {
  flex: 1;
  min-width: 0;
  margin-left: $sp-2;
}

/* ---- 列表 ---- */
.list-card {
  padding: 0 $gap-card;
}

.rv {
  padding: $sp-3 0;
  border-top: 1rpx solid $border-color;
}

.rv:first-child {
  border-top: none;
}

.rv.tappable:active {
  background-color: $bg-page;
}

.rv-book {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $sp-2;
  margin-bottom: $sp-2;
  border-radius: $radius-md;
  background-color: $bg-page;
}

.rv-cover {
  flex-shrink: 0;
  width: 72rpx;
  height: 96rpx;
  border-radius: $radius-sm;
  background-color: $bg-skeleton;
}

.rv-book-title {
  flex: 1;
  min-width: 0;
  margin-left: $sp-2;
  font-size: $fs-base;
  color: $text-regular;
}

.rv-head {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.avatar {
  flex-shrink: 0;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $color-white;
  background-color: $brand-light;
  border-radius: 50%;
}

.rv-who {
  flex: 1;
  min-width: 0;
  margin-left: $sp-2;
  display: flex;
  flex-direction: column;
}

.rv-name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-main;
}

.rv-time {
  margin-top: 4rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.rv-content {
  margin-top: $sp-2;
  font-size: $fs-base;
  line-height: 1.7;
  color: $text-regular;
}

.rv-content-empty {
  color: $text-placeholder;
}
</style>
