<template>
  <view class="page">
    <page-nav title="低价优选" />

    <view class="body">
      <scroll-view
        class="scroller"
        scroll-y
        :show-scrollbar="false"
        refresher-enabled
        :refresher-triggered="refreshing"
        refresher-background="#f5f5f5"
        @refresherrefresh="onRefresh"
        @scrolltolower="loadMore"
      >
        <!-- ① 促销说明条 -->
        <view class="promo-bar">
          <view class="promo-item">
            <text class="promo-char">折</text>
            <text class="promo-text">原价划线对比</text>
          </view>
          <view class="promo-item">
            <text class="promo-char">减</text>
            <text class="promo-text">部分书目满减</text>
          </view>
          <view class="promo-item">
            <text class="promo-char">清</text>
            <text class="promo-text">清仓售完即止</text>
          </view>
        </view>

        <!-- ② 折扣筛选 -->
        <view class="filter-bar">
          <view class="filter-row">
            <text class="filter-label">折扣</text>
            <view
              v-for="d in discountOptions"
              :key="d.label"
              class="chip-item"
              :class="{ on: discountIndex === d.index }"
              hover-class="hover-opacity"
              @tap="onDiscountTap(d.index)"
            >
              {{ d.label }}
            </view>
          </view>

          <!-- ③ 价格区间 -->
          <view class="filter-row">
            <text class="filter-label">价格</text>
            <scroll-view class="row-scroll" scroll-x :show-scrollbar="false">
              <view class="row-inner">
                <view
                  v-for="(p, idx) in priceOptions"
                  :key="p.label"
                  class="chip-item"
                  :class="{ on: priceIndex === idx }"
                  hover-class="hover-opacity"
                  @tap="onPriceTap(idx)"
                >
                  {{ p.label }}
                </view>
              </view>
            </scroll-view>
          </view>

          <!-- ④ 排序：默认价格升序 -->
          <view class="filter-row">
            <text class="filter-label">排序</text>
            <view
              v-for="s in sortOptions"
              :key="s.value"
              class="chip-item"
              :class="{ on: sort === s.value }"
              hover-class="hover-opacity"
              @tap="onSortTap(s.value)"
            >
              {{ s.label }}
            </view>
          </view>
        </view>

        <!-- ⑤ 图书网格 -->
        <view v-if="firstState === 'loading'" class="grid-books">
          <view v-for="n in 4" :key="n" class="grid-cell">
            <view class="sk sk-anim sk-cover" />
            <view class="sk sk-anim sk-line" style="width: 90%" />
            <view class="sk sk-anim sk-line" style="width: 55%" />
          </view>
        </view>

        <state-view
          v-else-if="firstState === 'error'"
          type="error"
          text="特价图书加载失败"
          sub-text="请检查网络后重试"
          btn-text="重新加载"
          @retry="reload"
        />

        <state-view
          v-else-if="!books.length"
          type="empty"
          text="当前条件下没有特价书"
          sub-text="放宽折扣或价格区间试试"
          btn-text="重置筛选"
          @retry="resetFilter"
        />

        <template v-else>
          <view class="count-row">
            <text class="count-text">共 {{ total }} 本特价书</text>
            <text class="count-strong">最低 {{ discountText(lowestDiscount) }}</text>
          </view>
          <view class="grid-books">
            <view v-for="b in books" :key="b.id" class="grid-cell">
              <book-card
                :book="b"
                action="both"
                show-original
                @tap="goDetail(b)"
                @buy="onBuy"
              />
            </view>
          </view>
          <view class="load-more">
            <text v-if="loadingMore">加载中...</text>
            <text v-else-if="finished">— 没有更多了 —</text>
          </view>
        </template>
      </scroll-view>
    </view>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 低价优选：折扣筛选 + 价格区间 + 排序，卡片带「加购 / 立即购买」双按钮。
 *
 * 与首页「低价优选」楼层（那里只是按价格升序的普通网格）的区别在信息密度：
 * 这里每张卡片都要能直接回答「便宜多少」和「能不能马上买」，
 * 所以强制显示原价删除线、折扣角标与促销标签（特价 / 满减 / 清仓）。
 *
 * 立即购买复用 common/buy-now.js，与图书详情页走同一条链路
 * （加购 → 回查 cartId → 只结算这一条），不会出现两处行为不一致。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { pageBooks } from '@/common/mock'
import { discountText } from '@/common/cover'
import { buyNow } from '@/common/buy-now'
import PageNav from '@/components/page-nav.vue'
import BookCard from '@/components/book-card.vue'
import StateView from '@/components/state-view.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const PAGE_SIZE = 10

/** 折扣档位：maxDiscount 为「折扣不高于」，0.5 即 5 折及以下 */
const discountOptions = [
  { index: 0, label: '全部', maxDiscount: null },
  { index: 1, label: '5 折及以下', maxDiscount: 0.5 },
  { index: 2, label: '3 折专区', maxDiscount: 0.3 }
]

const priceOptions = [
  { label: '全部', min: null, max: null },
  { label: '30 以下', min: null, max: 30 },
  { label: '30-50', min: 30, max: 50 },
  { label: '50-100', min: 50, max: 100 },
  { label: '100 以上', min: 100, max: null }
]

const sortOptions = [
  { label: '价格↑', value: 'price_asc' },
  { label: '折扣最低', value: 'discount' },
  { label: '销量', value: 'sales' }
]

const discountIndex = ref(0)
const priceIndex = ref(0)
const sort = ref('price_asc')
const books = ref([])
const total = ref(0)
const pageNum = ref(1)
const finished = ref(false)
const firstState = ref('loading')
const loadingMore = ref(false)
const refreshing = ref(false)

let navigating = false

/** 已加载图书中的最低折扣，用于顶部「最低 X 折」的即时反馈 */
const lowestDiscount = computed(() =>
  books.value.reduce((m, b) => Math.min(m, Number(b.discount) || 1), 1)
)

function params() {
  const p = { pageNum: pageNum.value, pageSize: PAGE_SIZE, sort: sort.value }
  const d = discountOptions.find((o) => o.index === discountIndex.value)
  if (d && d.maxDiscount != null) p.maxDiscount = d.maxDiscount
  const range = priceOptions[priceIndex.value]
  if (range.min != null) p.minPrice = range.min
  if (range.max != null) p.maxPrice = range.max
  return p
}

async function query(reset) {
  if (reset) {
    pageNum.value = 1
    finished.value = false
  } else {
    if (loadingMore.value || finished.value) return
    loadingMore.value = true
  }
  try {
    const page = await pageBooks(params())
    books.value = reset ? page.list || [] : books.value.concat(page.list || [])
    total.value = page.total || 0
    firstState.value = 'done'
    finished.value = books.value.length >= page.total
    if (!finished.value) pageNum.value += 1
  } catch (e) {
    if (reset) {
      books.value = []
      firstState.value = 'error'
    }
  } finally {
    loadingMore.value = false
  }
}

function onDiscountTap(index) {
  if (discountIndex.value === index) return
  discountIndex.value = index
  query(true)
}

function onPriceTap(idx) {
  if (priceIndex.value === idx) return
  priceIndex.value = idx
  query(true)
}

function onSortTap(value) {
  if (sort.value === value) return
  sort.value = value
  query(true)
}

function resetFilter() {
  discountIndex.value = 0
  priceIndex.value = 0
  query(true)
}

async function onRefresh() {
  refreshing.value = true
  await query(true)
  refreshing.value = false
}

function loadMore() {
  query(false)
}

function reload() {
  query(true)
}

async function onBuy(book) {
  if (navigating) return
  navigating = true
  try {
    await buyNow(book, 1)
  } finally {
    navigating = false
  }
}

function goDetail(book) {
  if (navigating) return
  navigating = true
  setTimeout(() => {
    navigating = false
  }, 800)
  uni.navigateTo({ url: '/pages/book/detail?id=' + book.id })
}

onLoad(() => {
  query(true)
})
</script>

<style lang="scss" scoped>
.page {
  height: 100vh;
  height: 100dvh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.body {
  flex: 1;
  height: 0;
}

.scroller {
  height: 100%;
}

/* ---- 促销说明条 ---- */
.promo-bar {
  margin: $sp-3 $gap-page 0;
  padding: $sp-3 $sp-2;
  display: flex;
  flex-direction: row;
  /* 红→橙的暖色浅渐变：呼应楼层竖条与 PC 楼层标题条，别再回到朱砂粉 */
  background: linear-gradient(120deg, $brand-weak 0%, $orange-weak 100%);
  border-radius: $radius-lg;
}

.promo-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}

.promo-char {
  flex-shrink: 0;
  width: 40rpx;
  height: 40rpx;
  margin-right: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-xs;
  font-weight: 700;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-sm;
}

.promo-text {
  font-size: $fs-xs;
  color: $text-regular;
}

/* ---- 筛选 ---- */
.filter-bar {
  margin-top: $sp-3;
  padding: $sp-2 0;
  background-color: $bg-card;
  border-bottom: 1rpx solid $border-color;
}

.filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $sp-1 $gap-page;
}

.filter-label {
  flex-shrink: 0;
  width: 64rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.row-scroll {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

.row-inner {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
}

.chip-item {
  flex-shrink: 0;
  height: 56rpx;
  padding: 0 24rpx;
  margin-right: $sp-2;
  display: flex;
  align-items: center;
  font-size: $fs-sm;
  color: $text-regular;
  background-color: $bg-fill;
  border-radius: $radius-btn;
}

.chip-item.on {
  color: $color-white;
  font-weight: 600;
  background-color: $brand;
}

/* ---- 网格 ---- */
.count-row {
  padding: $sp-3 $gap-page 0;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.count-text {
  flex: 1;
  min-width: 0;
  font-size: $fs-xs;
  color: $text-sub;
}

.count-strong {
  flex-shrink: 0;
  font-size: $fs-xs;
  font-weight: 600;
  color: $brand;
}

.grid-books {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  padding: $sp-3 $gap-page 0;
  gap: $gap-item;
}

.grid-cell {
  width: calc((100% - #{$gap-item}) / 2);
  display: flex;
}

.sk-cover {
  height: 0;
  padding-top: 133.33%;
  border-radius: $radius-lg;
}

.sk-line {
  height: 26rpx;
  margin-top: 14rpx;
}

.load-more {
  padding: $sp-5 0 $sp-8;
  text-align: center;
  font-size: $fs-sm;
  color: $text-sub;
}

/* 桌面：固定 5 列，列宽自适应容器，避免大屏下只显示一两列造成右侧大片空白 */
@media (min-width: 960px) {
  .promo-bar {
    max-width: 1240px;
    margin: 16px auto 0;
  }

  .filter-bar {
    max-width: 1240px;
    margin: 16px auto 0;
    border-radius: 6px;
  }

  .count-row {
    max-width: 1240px;
    margin: 0 auto;
    padding: 16px 24px 0;
  }

  .grid-books {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 20px;
    max-width: 1240px;
    margin: 0 auto;
    padding: 16px 24px 0;
  }

  .grid-cell {
    width: 100%;
    min-width: 0;
  }

  .load-more {
    max-width: 1240px;
    margin: 0 auto;
  }
}
</style>
