<template>
  <!-- 桌面顶栏：只在宽屏（≥960px）渲染，移动端由 nav-bar / page-nav / tab-bar 承担导航 -->
  <view class="wh">
    <!-- ① 顶部窄条：站点说明 + 个人入口。PC 商城的标准两段式，移动端没有这一层 -->
    <view class="wh-topbar">
      <view class="wh-topbar-inner">
        <text class="wh-slogan">线上书店 · 软件工程毕业设计演示</text>
        <view class="wh-top-links">
          <text class="wh-top-link" @tap="goOrder">我的订单</text>
          <text class="wh-top-sep">|</text>
          <text class="wh-top-link" @tap="goMine">{{ logined ? '个人中心' : '登录 / 注册' }}</text>
        </view>
      </view>
    </view>

    <!-- ② 主导航条：Logo + 一级导航 + 搜索 + 购物车 -->
    <view class="wh-main">
      <view class="wh-inner">
        <view class="wh-brand" hover-class="hover-opacity" @tap="go(SITE_NAV[0])">
          <text class="wh-mark">书</text>
          <text class="wh-name">线上书店</text>
        </view>

        <view class="wh-nav">
          <text
            v-for="n in SITE_NAV"
            :key="n.key"
            class="wh-nav-item"
            :class="{ on: activeKey === n.key }"
            @tap="go(n)"
          >
            {{ n.label }}
          </text>
        </view>

        <view class="wh-search">
          <input
            class="wh-input"
            v-model="keyword"
            placeholder="搜索图书 / 作者 / 出版社"
            placeholder-class="wh-ph"
            confirm-type="search"
            @confirm="submit"
          />
          <view class="wh-search-btn" @tap="submit">搜索</view>
        </view>

        <view class="wh-cart" @tap="goCart">
          <text class="wh-cart-text">购物车</text>
          <view v-if="badge > 0" class="wh-badge">{{ badge > 99 ? '99+' : badge }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * Web 桌面顶栏（两段式）。
 *
 * 只在一级导航区做「站点级」入口：顶部窄条（站点说明 + 个人入口）+ 主导航条
 * （Logo、首页/分类/书单、搜索、购物车）。这是 PC 书城的通行版式，
 * 与移动端单条 44px 导航是两套完全不同的形态。
 *
 * 尺寸用 px 而非 rpx：本组件只在 ≥960px 生效，而该区间 rpx 已固定 375 基准
 * （1rpx = 0.5px），用 px 表达桌面尺寸更直观，也不会被移动端样式牵连。
 *
 * 总高度必须与 common/web.js 的 WEB_HEADER_H 一致（页面占位靠它计算）。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { SITE_NAV, goSearch, currentRoute } from '@/common/web'
import { cartCount, refreshCartCount, onCartChanged, offCartChanged } from '@/common/cart-badge'
import { isLogin } from '@/common/auth'

const keyword = ref('')
const badge = ref(cartCount.value)
const logined = ref(isLogin())

/** 当前所在页面 route → 高亮对应一级导航 */
const activeKey = computed(() => {
  const route = currentRoute()
  const hit = SITE_NAV.find((n) => n.route === route)
  return hit ? hit.key : ''
})

const handler = () => {
  badge.value = cartCount.value
}

onMounted(() => {
  logined.value = isLogin()
  refreshCartCount().then(() => {
    badge.value = cartCount.value
  })
  onCartChanged(handler)
})

onUnmounted(() => offCartChanged(handler))

function submit() {
  goSearch(keyword.value)
}

/** 一级导航：与底部 tabBar 同级，redirectTo 保持一致（自绘 tabBar 下 switchTab 失效） */
function go(n) {
  uni.redirectTo({ url: '/' + n.route })
}

function goCart() {
  uni.redirectTo({ url: '/pages/cart/cart' })
}

function goMine() {
  uni.redirectTo({ url: '/pages/mine/mine' })
}

function goOrder() {
  uni.navigateTo({ url: '/pages/order/list' })
}
</script>

<style lang="scss" scoped>
.wh {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 60;
  background-color: $bg-card;
  border-bottom: 1px solid $border-color;
}

/* 内容区与页面主容器同宽（1200px）居中，通栏白条保留桌面观感 */
.wh-inner {
  max-width: 1200px;
  height: 76px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  flex-direction: row;
  align-items: center;
}

/* ---- ① 顶部窄条 ---- */
.wh-topbar {
  height: 36px;
  background-color: $bg-page;
  border-bottom: 1px solid $border-color;
}

.wh-topbar-inner {
  max-width: 1200px;
  height: 36px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.wh-slogan {
  font-size: 12px;
  color: $text-placeholder;
}

.wh-top-links {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.wh-top-link {
  font-size: 12px;
  color: $text-regular;
}

.wh-top-sep {
  margin: 0 10px;
  font-size: 12px;
  color: $border-color;
}

/* ---- ② 主导航条 ---- */
.wh-main {
  height: 76px;
  background-color: $bg-card;
}

.wh-brand {
  flex-shrink: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-right: 32px;
}

.wh-mark {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: $color-white;
  background-color: $brand;
  border-radius: 6px;
}

.wh-name {
  margin-left: 8px;
  font-size: 16px;
  font-weight: 700;
  color: $text-main;
}

.wh-nav {
  flex-shrink: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.wh-nav-item {
  padding: 6px 14px;
  font-size: 15px;
  color: $text-regular;
  border-radius: 6px;
}

.wh-nav-item.on {
  color: $brand;
  font-weight: 600;
  background-color: $brand-weak;
}

.wh-search {
  flex: 1;
  min-width: 0;
  max-width: 460px;
  height: 40px;
  margin: 0 28px;
  padding: 0 4px 0 16px;
  display: flex;
  flex-direction: row;
  align-items: center;
  background-color: $bg-card;
  border: 2px solid $brand;
  border-radius: 4px;
}

.wh-search:focus-within {
  border-color: $brand-dark;
}

.wh-input {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: $text-main;
}

/* 搜索按钮：PC 书城的搜索框带实心按钮，移动端只有输入框 */
.wh-search-btn {
  flex-shrink: 0;
  width: 72px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: 2px;
}

.wh-ph {
  color: $text-placeholder;
}

/* 购物车：PC 上是实心按钮而不是文字链接 */
.wh-cart {
  position: relative;
  flex-shrink: 0;
  height: 40px;
  padding: 0 20px;
  display: flex;
  flex-direction: row;
  align-items: center;
  font-size: 14px;
  color: $color-white;
  background-color: $brand-weak;
  border: 1px solid $brand;
  border-radius: 4px;
}

.wh-cart-text {
  color: $brand;
  font-weight: 600;
}

.wh-badge {
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: $color-white;
  background-color: $brand;
  border-radius: 999px;
}

/* ---- 桌面指针交互 ----
   hover-class 走 touch 事件，鼠标悬停不触发；这里补原生 :hover + cursor，
   否则鼠标用户看不出导航项可点（触屏设备由媒体查询排除，避免悬停态粘住）。 */
@media (hover: hover) and (pointer: fine) {
  .wh-brand,
  .wh-nav-item,
  .wh-cart,
  .wh-search-btn,
  .wh-top-link {
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease;
  }

  .wh-brand:hover .wh-name {
    color: $brand;
  }

  .wh-nav-item:hover {
    color: $text-main;
    background-color: $bg-page;
  }

  /* 选中项的悬停保持品牌弱底，只做轻微加深，避免颜色跳变 */
  .wh-nav-item.on:hover {
    color: $brand;
    background-color: #f9d7d4;
  }

  .wh-cart:hover {
    background-color: $brand-weak;
    border-color: $brand-dark;
  }

  .wh-search-btn:hover {
    background-color: $brand-dark;
  }

  .wh-top-link:hover {
    color: $brand;
  }
}
</style>
