<template>
  <view class="page">
    <page-nav title="书单" />
    <channel-tabs :channels="tabs" :active="tabId" :top="navBarHeight" @change="onTab" />
    <view :style="{ height: tabRowHeight + 'px' }" />

    <view class="body" :style="{ paddingBottom: tabBarHeight + 'px' }">
      <scroll-view
        class="scroller"
        scroll-y
        :show-scrollbar="false"
        refresher-enabled
        :refresher-triggered="refreshing"
        refresher-background="#F4F5F7"
        @refresherrefresh="onRefresh"
        @scrolltolower="loadMore"
      >
        <!-- 首屏骨架 -->
        <view v-if="firstState === 'loading'" class="sk-wrap">
          <view v-for="n in 3" :key="n" class="bl-card sk-card">
            <view class="sk sk-anim sk-mosaic" />
            <view class="sk-info">
              <view class="sk sk-anim sk-line" style="width: 70%" />
              <view class="sk sk-anim sk-line" style="width: 92%" />
              <view class="sk sk-anim sk-line" style="width: 44%" />
            </view>
          </view>
        </view>

        <state-view
          v-else-if="firstState === 'error'"
          type="error"
          text="书单加载失败"
          sub-text="请检查网络后重试"
          btn-text="重新加载"
          @retry="reload"
        />

        <state-view
          v-else-if="!list.length"
          type="empty"
          text="该分类下暂无书单"
          sub-text="换个频道看看吧"
        />

        <template v-else>
          <view
            v-for="item in list"
            :key="item.id"
            class="bl-card"
            hover-class="hover-dim"
            @tap="goDetail(item)"
          >
            <view class="bl-top">
              <view class="bl-info">
                <text class="bl-title ellipsis-2">{{ item.title }}</text>
                <text class="bl-desc ellipsis-2">{{ item.desc }}</text>
                <view class="bl-stat">
                  <text class="stat-num">{{ item.bookCount }}</text>
                  <text class="stat-unit">本</text>
                  <text class="stat-dot">·</text>
                  <text class="stat-collect">{{ collectText(item.collectCount) }}人收藏</text>
                </view>
              </view>

              <!-- 四本书缩略图拼接封面 -->
              <view class="mosaic">
                <view v-for="(b, i) in item.preview" :key="i" class="mosaic-cell">
                  <cover-box :book="b" />
                </view>
              </view>
            </view>

            <view class="bl-tags">
              <text v-for="t in item.tags" :key="t" class="bl-tag">#{{ t }}</text>
            </view>
          </view>

          <view class="load-more">
            <text v-if="loadingMore">加载中...</text>
            <text v-else-if="finished">— 没有更多了 —</text>
          </view>
        </template>
      </scroll-view>
    </view>

    <tab-bar active="read" />
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 书单：四个频道 Tab + 卡片流。
 *
 * 卡片的信息密度按「用户为什么点开一个书单」来排：
 * 先看标题和一句话说明判断主题，再看书目数与收藏人数判断分量，
 * 四宫格拼接封面负责直观感受，底部话题标签给出内容属性。
 *
 * 分页用 scroll-view 的自定义下拉刷新：本页是 custom 导航，
 * 页面级原生刷新圈会浮在自绘导航之上把导航盖住。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBooklistTabs, pageBooklists } from '@/common/mock'
import { navBarHeight, tabBarHeight, rpx } from '@/common/layout'
import PageNav from '@/components/page-nav.vue'
import TabBar from '@/components/tab-bar.vue'
import ChannelTabs from '@/components/channel-tabs.vue'
import CoverBox from '@/components/cover-box.vue'
import StateView from '@/components/state-view.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const PAGE_SIZE = 4

const tabs = ref([])
const tabId = ref('hot')
const list = ref([])
const pageNum = ref(1)
const finished = ref(false)
const firstState = ref('loading')
const loadingMore = ref(false)
const refreshing = ref(false)

/** 频道条高度：与首页频道条一致（88rpx） */
const tabRowHeight = rpx(88)

let navigating = false

function collectText(n) {
  const v = Number(n) || 0
  return v >= 10000 ? (v / 10000).toFixed(1) + ' 万' : String(v)
}

async function loadTabs() {
  try {
    tabs.value = await getBooklistTabs()
    if (tabs.value.length) tabId.value = tabs.value[0].id
  } catch (e) {
    tabs.value = []
  }
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
    const page = await pageBooklists({ tabId: tabId.value, pageNum: pageNum.value, pageSize: PAGE_SIZE })
    list.value = reset ? page.list || [] : list.value.concat(page.list || [])
    firstState.value = 'done'
    finished.value = list.value.length >= page.total
    if (!finished.value) pageNum.value += 1
  } catch (e) {
    if (reset) {
      list.value = []
      firstState.value = 'error'
    }
  } finally {
    loadingMore.value = false
  }
}

function onTab(id) {
  if (tabId.value === id) return
  tabId.value = id
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
  loadTabs()
  query(true)
}

function goDetail(item) {
  if (navigating) return
  navigating = true
  setTimeout(() => {
    navigating = false
  }, 800)
  uni.navigateTo({ url: '/pages/booklist/detail?id=' + item.id })
}

onLoad(async () => {
  await loadTabs()
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
  box-sizing: border-box;
}

.scroller {
  height: 100%;
}

/* ---- 书单卡片 ---- */
.bl-card {
  margin: 0 $gap-page $gap-item;
  padding: $gap-card;
  background-color: $bg-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
}

.bl-top {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.bl-info {
  flex: 1;
  min-width: 0;
  padding-right: $sp-3;
}

.bl-title {
  font-size: $fs-lg;
  font-weight: 700;
  line-height: 1.45;
  color: $text-main;
}

.bl-desc {
  margin-top: $sp-2;
  font-size: $fs-sm;
  line-height: 1.6;
  color: $text-sub;
}

.bl-stat {
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

/* 四宫格拼接封面 */
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

.bl-tags {
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

/* ---- 骨架 ---- */
.sk-wrap {
  padding-top: $sp-3;
}

.sk-card {
  display: flex;
  flex-direction: row;
}

.sk-mosaic {
  flex-shrink: 0;
  width: 200rpx;
  height: 200rpx;
  border-radius: $radius-md;
}

.sk-info {
  flex: 1;
  min-width: 0;
  padding-right: $sp-3;
}

.sk-line {
  height: 28rpx;
  margin-bottom: $sp-3;
}

.load-more {
  padding: $sp-4 0 $sp-6;
  text-align: center;
  font-size: $fs-sm;
  color: $text-sub;
}
</style>
