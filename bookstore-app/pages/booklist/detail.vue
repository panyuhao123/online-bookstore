<template>
  <view class="page">
    <page-nav :title="detail.title || '书单详情'" />

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
        <!-- 加载骨架：结构已知，比整屏空白更能说明「正在取数据」 -->
        <view v-if="state === 'loading'" class="sk-wrap">
          <view class="card sk-head">
            <view class="sk sk-anim sk-mosaic" />
            <view class="sk-head-info">
              <view class="sk sk-anim sk-line" style="width: 70%" />
              <view class="sk sk-anim sk-line" style="width: 40%" />
              <view class="sk sk-anim sk-line" style="width: 88%" />
            </view>
          </view>
          <view v-for="n in 3" :key="n" class="card sk-row">
            <view class="sk sk-anim sk-cover" />
            <view class="sk-info">
              <view class="sk sk-anim sk-line" style="width: 80%" />
              <view class="sk sk-anim sk-line" style="width: 50%" />
            </view>
          </view>
        </view>

        <!-- ① 书单信息头 -->
        <view v-if="state === 'done'" class="card head">
          <view class="head-top">
            <view class="mosaic">
              <view v-for="(b, i) in detail.books.slice(0, 4)" :key="i" class="mosaic-cell">
                <cover-box :book="b" />
              </view>
            </view>
            <view class="head-info">
              <text class="head-title">{{ detail.title }}</text>
              <view class="head-stat">
                <text class="stat-num">{{ detail.bookCount }}</text>
                <text class="stat-unit">本</text>
                <text class="stat-dot">·</text>
                <text class="stat-collect">{{ collectText(detail.collectCount) }}人收藏</text>
              </view>
            </view>
          </view>

          <text class="head-desc">{{ detail.desc }}</text>

          <view class="head-tags">
            <text v-for="t in detail.tags" :key="t" class="bl-tag">#{{ t }}</text>
          </view>
        </view>

        <state-view
          v-if="state === 'error'"
          type="error"
          text="书单加载失败"
          sub-text="请检查网络后重试"
          btn-text="重新加载"
          @retry="load"
        />

        <!-- ② 书单内图书 -->
        <template v-if="state === 'done'">
          <view class="sec-title-row">
            <text class="sec-title">书单书目</text>
            <text class="sec-sub">共 {{ detail.bookCount }} 本</text>
          </view>

          <view
            v-for="b in shown"
            :key="b.id"
            class="card row"
            hover-class="hover-dim"
            @tap="goDetail(b)"
          >
            <view class="row-cover">
              <cover-box :book="b" />
            </view>
            <view class="row-info">
              <text class="row-title ellipsis-2">{{ b.title }}</text>
              <text class="row-author ellipsis">{{ b.author }} · {{ b.publisher }}</text>
              <view class="row-meta">
                <template v-if="b.avgRating">
                  <text class="star">★</text>
                  <text class="score">{{ Number(b.avgRating).toFixed(1) }}</text>
                  <text class="cnt">({{ b.reviewCount }})</text>
                </template>
                <text v-else class="no-rating">☆ 暂无评分</text>
              </view>
              <view class="row-bottom">
                <view class="price-wrap">
                  <text class="rmb">¥</text>
                  <text class="num">{{ price(b.price) }}</text>
                  <text v-if="b.originalPrice" class="origin">¥{{ price(b.originalPrice) }}</text>
                </view>
                <text v-if="Number(b.stock || 0) <= 0" class="sold">已售罄</text>
              </view>
            </view>
            <view
              class="row-add"
              :class="{ disabled: Number(b.stock || 0) <= 0 }"
              hover-class="hover-scale"
              @tap.stop="onAdd(b)"
            >
              {{ Number(b.stock || 0) <= 0 ? '售罄' : '加购' }}
            </view>
          </view>

          <view class="load-more">
            <text v-if="!finished">上拉加载更多</text>
            <text v-else>— 已展示全部 {{ detail.bookCount }} 本 —</text>
          </view>
        </template>
      </scroll-view>
    </view>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 书单详情：书单信息头 + 书单内全部图书。
 *
 * 书目由 getBooklistDetail 一次带回，分页在前端切片完成 ——
 * 书单里的书最多十几本，为一个已知很短的列表去设计后端分页接口并不划算。
 *
 * 加购走 common/cart-action 的统一入口（未登录存本机、登录后自动同步），
 * 与首页、分类、详情页的行为完全一致。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBooklistDetail } from '@/common/mock'
import { price } from '@/common/format'
import { addBookToCart } from '@/common/cart-action'
import PageNav from '@/components/page-nav.vue'
import CoverBox from '@/components/cover-box.vue'
import StateView from '@/components/state-view.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const PAGE_SIZE = 6

const detail = ref({})
const pageNum = ref(1)
const state = ref('loading') // loading / done / error
const refreshing = ref(false)

let navigating = false
let booklistId = ''

const allBooks = computed(() => detail.value.books || [])
const shown = computed(() => allBooks.value.slice(0, pageNum.value * PAGE_SIZE))
const finished = computed(() => shown.value.length >= allBooks.value.length)

function collectText(n) {
  const v = Number(n) || 0
  return v >= 10000 ? (v / 10000).toFixed(1) + ' 万' : String(v)
}

async function load() {
  state.value = 'loading'
  try {
    detail.value = await getBooklistDetail(booklistId)
    pageNum.value = 1
    state.value = 'done'
  } catch (e) {
    detail.value = {}
    state.value = 'error'
  }
}

async function onRefresh() {
  refreshing.value = true
  await load()
  refreshing.value = false
}

function loadMore() {
  if (finished.value) return
  pageNum.value += 1
}

async function onAdd(book) {
  if (Number(book.stock || 0) <= 0) return
  await addBookToCart(book, 1)
}

function goDetail(book) {
  if (navigating) return
  navigating = true
  setTimeout(() => {
    navigating = false
  }, 800)
  uni.navigateTo({ url: '/pages/book/detail?id=' + book.id })
}

onLoad((options) => {
  booklistId = (options && options.id) || ''
  load()
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

/* ---- 信息头 ---- */
.head {
  margin-top: $sp-3;
}

.head-top {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.mosaic {
  flex-shrink: 0;
  width: 200rpx;
  height: 200rpx;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 4rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.mosaic-cell {
  width: calc((100% - 4rpx) / 2);
  height: calc((100% - 4rpx) / 2);
  overflow: hidden;
}

.head-info {
  flex: 1;
  min-width: 0;
  padding-left: $sp-3;
}

.head-title {
  font-size: $fs-2xl;
  font-weight: 700;
  line-height: 1.4;
  color: $text-main;
}

.head-stat {
  margin-top: $sp-3;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.stat-num {
  font-size: $fs-md;
  font-weight: 700;
  color: $brand;
}

.stat-unit {
  margin-left: 4rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.stat-dot {
  margin: 0 $sp-2;
  font-size: $fs-xs;
  color: $text-placeholder;
}

.stat-collect {
  font-size: $fs-xs;
  color: $text-sub;
}

.head-desc {
  display: block;
  margin-top: $sp-3;
  font-size: $fs-base;
  line-height: 1.7;
  color: $text-regular;
}

.head-tags {
  margin-top: $sp-3;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: $sp-1;
}

.bl-tag {
  padding: 4rpx 16rpx;
  font-size: $fs-xs;
  color: $brand;
  background-color: $brand-weak;
  border-radius: $radius-sm;
}

/* ---- 书目列表 ---- */
.sec-title-row {
  padding: $sp-4 $gap-page $sp-2;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.sec-title {
  flex: 1;
  min-width: 0;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-main;
}

.sec-sub {
  flex-shrink: 0;
  font-size: $fs-xs;
  color: $text-sub;
}

.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $sp-3;
}

.row-cover {
  flex-shrink: 0;
  width: 140rpx;
  height: 186rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.row-info {
  flex: 1;
  min-width: 0;
  padding: 0 $sp-3;
}

.row-title {
  font-size: $fs-md;
  line-height: 1.5;
  color: $text-main;
}

.row-author {
  display: block;
  margin-top: $sp-1;
  font-size: $fs-xs;
  color: $text-sub;
}

.row-meta {
  margin-top: $sp-1;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.star {
  font-size: $fs-xs;
  color: $warning;
}

.score {
  margin-left: 4rpx;
  font-size: $fs-xs;
  font-weight: 600;
  color: $text-regular;
}

.cnt {
  margin-left: 4rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.no-rating {
  font-size: $fs-xs;
  color: $text-placeholder;
}

.row-bottom {
  margin-top: $sp-2;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.price-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.rmb {
  font-size: $fs-xs;
  font-weight: 600;
  color: $price-color;
}

.num {
  font-size: $fs-xl;
  font-weight: 700;
  color: $price-color;
}

.origin {
  margin-left: $sp-1;
  font-size: $fs-xs;
  color: $text-placeholder;
  text-decoration: line-through;
}

.sold {
  flex-shrink: 0;
  font-size: $fs-xs;
  color: $text-placeholder;
}

.row-add {
  flex-shrink: 0;
  height: 64rpx;
  padding: 0 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-sm;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-btn;
}

.row-add.disabled {
  background-color: $border-strong;
}

/* ---- 骨架 ---- */
.sk-wrap {
  padding-top: $sp-3;
}

.sk-head {
  display: flex;
  flex-direction: row;
}

.sk-mosaic {
  flex-shrink: 0;
  width: 200rpx;
  height: 200rpx;
  border-radius: $radius-md;
}

.sk-head-info {
  flex: 1;
  min-width: 0;
  padding-left: $sp-3;
}

.sk-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.sk-cover {
  flex-shrink: 0;
  width: 140rpx;
  height: 186rpx;
  border-radius: $radius-md;
}

.sk-info {
  flex: 1;
  min-width: 0;
  padding-left: $sp-3;
}

.sk-line {
  height: 28rpx;
  margin-bottom: $sp-2;
}

.load-more {
  padding: $sp-4 0 $sp-8;
  text-align: center;
  font-size: $fs-sm;
  color: $text-sub;
}
</style>
