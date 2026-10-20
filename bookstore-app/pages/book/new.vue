<template>
  <view class="page">
    <page-nav title="新书上架" />

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
        <!-- ① 重磅新书 Banner -->
        <view v-if="banners.length" class="banner-wrap">
          <swiper class="banner" :autoplay="true" :interval="4500" :circular="true" @change="onBannerChange">
            <swiper-item v-for="(b, i) in banners" :key="b.id">
              <view
                class="banner-inner"
                :style="{ backgroundImage: bannerBg[i % bannerBg.length] }"
                hover-class="hover-scale"
                @tap="goDetail(b)"
              >
                <view class="b-cover">
                  <cover-box :book="b" />
                </view>
                <view class="b-info">
                  <text class="b-badge">重磅新书</text>
                  <text class="b-title ellipsis-2">{{ b.title }}</text>
                  <text class="b-author ellipsis">{{ b.author }} · {{ b.publisher }}</text>
                  <view class="b-price">
                    <text class="b-rmb">¥</text>
                    <text class="b-num">{{ price(b.price) }}</text>
                    <text v-if="b.originalPrice" class="b-origin">¥{{ price(b.originalPrice) }}</text>
                  </view>
                </view>
              </view>
            </swiper-item>
          </swiper>
          <view class="dots">
            <view v-for="(b, i) in banners" :key="b.id" class="dot" :class="{ on: i === bannerIndex }" />
          </view>
        </view>

        <!-- ② 筛选：决定数据范围 -->
        <view class="filter-bar">
          <view class="filter-row">
            <text class="filter-label">上架</text>
            <view
              v-for="r in rangeOptions"
              :key="r.value"
              class="chip-item"
              :class="{ on: range === r.value }"
              hover-class="hover-opacity"
              @tap="onRangeTap(r.value)"
            >
              {{ r.label }}
            </view>
          </view>
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

        <!-- ③ 图书网格 -->
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
          text="新书加载失败"
          sub-text="请检查网络后重试"
          btn-text="重新加载"
          @retry="reload"
        />

        <state-view
          v-else-if="!books.length"
          type="empty"
          :text="emptyText"
          sub-text="换个筛选条件看看"
          btn-text="查看全部新书"
          @retry="onRangeTap('latest')"
        />

        <template v-else>
          <view class="count-row">
            <text class="count-text">共 {{ total }} 本新书</text>
          </view>
          <view class="grid-books">
            <view v-for="b in books" :key="b.id" class="grid-cell">
              <book-card :book="b" corner-tag="新书" show-original @tap="goDetail(b)" />
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
 * 新书上架：重磅新书 Banner + 上架范围筛选 + 排序 + 双列网格。
 *
 * 筛选与排序拆成两个正交维度，避免「最新入库」既是筛选又隐含排序的歧义：
 *   筛选（范围）：最新入库=全量 / 本周上新=近 7 天 / 本月新书=近 30 天
 *   排序（顺序）：入库时间（默认）/ 出版时间 / 热度
 * 后者只影响「范围内怎么排」，前者只决定「哪些书进来」。
 *
 * 卡片统一走 book-card，用 cornerTag 打「新书」标、showOriginal 显示定价/售价，
 * 与低价优选页共用同一套卡片结构（封面 / 书名 / 作者 / 价格）。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { pageBooks } from '@/common/mock'
import { price } from '@/common/format'
import PageNav from '@/components/page-nav.vue'
import BookCard from '@/components/book-card.vue'
import CoverBox from '@/components/cover-box.vue'
import StateView from '@/components/state-view.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const PAGE_SIZE = 10

/** 筛选：与 mock 接口的 range 参数对应 */
const rangeOptions = [
  { label: '最新入库', value: 'latest' },
  { label: '本周上新', value: 'week' },
  { label: '本月新书', value: 'month' }
]

/** 排序：new/publish 为演示数据集扩展档，sales 与后端白名单一致 */
const sortOptions = [
  { label: '入库时间', value: 'new' },
  { label: '出版时间', value: 'publish' },
  { label: '热度', value: 'sales' }
]

/** Banner 底色：与 UI 规范的柔和浅色系一致。
 *  第 1 档 = 品牌弱底的浅/深两阶（$brand-weak → $brand-weak-deep），
 *  内联 style 取不到 SCSS Token，改主色时这里要手动同步。 */
const bannerBg = [
  'linear-gradient(120deg, #fff1f0 0%, #ffe3e3 100%)',
  'linear-gradient(120deg, #e9eef7 0%, #d5e0f0 100%)',
  'linear-gradient(120deg, #ecf2ea 0%, #dae7d7 100%)'
]

const range = ref('latest')
const sort = ref('new')
const books = ref([])
const total = ref(0)
const pageNum = ref(1)
const finished = ref(false)
const firstState = ref('loading')
const loadingMore = ref(false)
const refreshing = ref(false)

const banners = ref([])
const bannerIndex = ref(0)

let navigating = false

const emptyText = computed(() => {
  const r = rangeOptions.find((o) => o.value === range.value)
  return (r ? r.label : '该条件') + '暂无新书'
})

function params() {
  const p = { pageNum: pageNum.value, pageSize: PAGE_SIZE, sort: sort.value }
  if (range.value !== 'latest') p.range = range.value
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

/** Banner 取入库最新且销量最高的 3 本，与筛选状态无关（始终是新书中最重磅的） */
async function loadBanners() {
  try {
    const page = await pageBooks({ pageNum: 1, pageSize: 12, sort: 'new' })
    banners.value = (page.list || [])
      .slice()
      .sort((a, b) => b.sales - a.sales)
      .slice(0, 3)
  } catch (e) {
    banners.value = []
  }
}

function onBannerChange(e) {
  bannerIndex.value = e.detail.current
}

function onRangeTap(value) {
  if (range.value === value) return
  range.value = value
  query(true)
}

function onSortTap(value) {
  if (sort.value === value) return
  sort.value = value
  query(true)
}

async function onRefresh() {
  refreshing.value = true
  await Promise.all([query(true), loadBanners()])
  refreshing.value = false
}

function loadMore() {
  query(false)
}

function reload() {
  query(true)
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
  loadBanners()
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

/* ---- Banner ---- */
.banner-wrap {
  padding: $sp-3 $gap-page 0;
}

.banner {
  height: 260rpx;
  border-radius: $radius-lg;
  overflow: hidden;
}

.banner-inner {
  height: 260rpx;
  padding: $sp-3;
  display: flex;
  flex-direction: row;
  align-items: center;
  border-radius: $radius-lg;
}

.b-cover {
  flex-shrink: 0;
  width: 156rpx;
  height: 208rpx;
  border-radius: $radius-md;
  overflow: hidden;
  box-shadow: 0 4rpx 12rpx rgba(31, 33, 38, 0.12);
}

.b-info {
  flex: 1;
  min-width: 0;
  padding-left: $sp-3;
  /* 撑成 flex 列，让下方「重磅新书」角标用 align-self 收窄到文字宽度，
     否则它会像块级元素一样铺满整行，看起来像一条色带 */
  display: flex;
  flex-direction: column;
}

.b-badge {
  align-self: flex-start;
  padding: 3rpx 14rpx;
  font-size: $fs-xs;
  font-weight: 600;
  color: $color-white;
  background: linear-gradient(135deg, $brand-light 0%, $brand-dark 100%);
  border-radius: $radius-sm;
}

.b-title {
  display: block;
  margin-top: $sp-2;
  font-size: $fs-lg;
  font-weight: 700;
  line-height: 1.45;
  color: $text-main;
}

.b-author {
  display: block;
  margin-top: $sp-1;
  font-size: $fs-xs;
  color: $text-sub;
}

.b-price {
  margin-top: $sp-2;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.b-rmb {
  font-size: $fs-xs;
  font-weight: 600;
  color: $price-color;
}

.b-num {
  font-size: $fs-xl;
  font-weight: 700;
  color: $price-color;
}

.b-origin {
  margin-left: $sp-1;
  font-size: $fs-xs;
  color: $text-placeholder;
  text-decoration: line-through;
}

.dots {
  margin-top: $sp-2;
  display: flex;
  flex-direction: row;
  justify-content: center;
  gap: 10rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: $radius-pill;
  background-color: $border-strong;
  transition: width 150ms ease;
}

.dot.on {
  width: 28rpx;
  background-color: $brand;
}

/* ---- 筛选 / 排序 ---- */
.filter-bar {
  margin: $sp-3 0 0;
  padding: $sp-1 0;
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
}

.count-text {
  font-size: $fs-xs;
  color: $text-sub;
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
  .banner-wrap {
    max-width: 1240px;
    margin: 0 auto;
    padding: 16px 24px 0;
  }

  .banner,
  .banner-inner {
    height: 220px;
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
