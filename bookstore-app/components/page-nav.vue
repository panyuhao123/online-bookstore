<template>
  <view>
    <!-- 桌面：先挂站点顶栏（一级导航 / 搜索），返回标题行排在它下面 -->
    <web-header v-if="isWide" />
    <!-- 固定导航：状态栏占位 + 返回键 + 居中标题 -->
    <view class="page-nav">
      <view class="status-bar" :style="{ height: statusH + 'px' }" />
      <view class="bar-row" :style="{ height: rowHeight + 'px' }">
        <view class="back" hover-class="hover-opacity" @tap="goBack">
          <text class="back-char">‹</text>
        </view>
        <text class="nav-title ellipsis">{{ title }}</text>
        <view class="holder" />
      </view>
    </view>
    <!-- 流内占位：与导航同高，页面内容自然从导航下方开始，各页不必再算安全区 -->
    <view :style="{ height: totalHeight + 'px' }" />
  </view>
</template>

<script setup>
/**
 * 二级页自绘导航（页面需在 pages.json 配 navigationStyle: custom）。
 *
 * 不复用 nav-bar：那是首页系的「Logo + 搜索 + 入口」形态，二级页只需要「返回 + 标题」。
 * 登录页可能作为启动首屏（页面栈为空），此时 navigateBack 会失败，回落到首页。
 *
 * 桌面（≥960px）：上方叠加 web-header，占位高度要把它算进去，
 * 同时行高不能再用 windowWidth 折算 88rpx——宽屏下 rpx 固定 375 基准，
 * 那样会算出 100+px 的行高，与真实渲染高度不符。
 */
import { ref, computed } from 'vue'
import { isWide, WEB_HEADER_H, NAV_ROW_H } from '@/common/web'
import WebHeader from '@/components/web-header.vue'

defineProps({
  title: { type: String, default: '' }
})

const sys = uni.getSystemInfoSync()
const statusBarHeight = ref(sys.statusBarHeight || 0)
/** 88rpx 按 750 设计稿折算 px，与行内 rpx 布局保持一致；宽屏直接用 44px */
const rowHeight = computed(() =>
  isWide.value ? NAV_ROW_H : Math.round((88 * (sys.windowWidth || 375)) / 750)
)
/** 桌面无状态栏，且要额外让出站点顶栏的高度 */
const statusH = computed(() => (isWide.value ? 0 : statusBarHeight.value))
/** 桌面下「返回+标题」行整体隐藏（站点顶栏已承担导航，返回走浏览器/面包屑），
 *  流内占位只需要让出顶栏高度 */
const totalHeight = computed(() =>
  isWide.value ? WEB_HEADER_H : statusH.value + rowHeight.value
)

function goBack() {
  uni.navigateBack({
    fail: () => uni.reLaunch({ url: '/pages/index/index' })
  })
}
</script>

<style lang="scss" scoped>
.page-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  background-color: $bg-card;
  border-bottom: 1rpx solid $border-color;
}

.bar-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.back {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.back-char {
  font-size: 48rpx;
  line-height: 1;
  color: $text-main;
}

.nav-title {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-main;
}

.holder {
  width: 88rpx;
  flex-shrink: 0;
}

/* ---- 桌面：移动端「返回+标题」行整体隐藏，只保留模板里单独挂的 web-header ---- */
@media (min-width: 960px) {
  .page-nav {
    display: none;
  }
}
</style>
