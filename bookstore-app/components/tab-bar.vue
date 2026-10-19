<template>
  <!-- 自绘底部导航：5 个入口，购物车带实时角标（桌面由站点顶栏接管，此处不渲染） -->
  <view v-if="!isWide" class="tabbar">
    <view
      v-for="t in tabs"
      :key="t.key"
      class="tab-item"
      :class="{ active: active === t.key }"
      hover-class="hover-dim"
      @tap="onTap(t)"
    >
      <view class="icon-wrap">
        <image class="tab-icon" :src="active === t.key ? t.iconActive : t.icon" mode="aspectFit" />
        <view v-if="t.key === 'cart' && badge > 0" class="badge">{{ badge > 99 ? '99+' : badge }}</view>
      </view>
      <text class="tab-label">{{ t.label }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 自绘底部 Tab 栏。
 *
 * 为什么不用 pages.json 的原生 tabBar：
 *  1. 原生 tabBar 的角标只能靠 uni.setTabBarBadge 设置，无法随购物车实时联动；
 *  2. 示例 App 是 5 个入口，原生配置改起来要动应用骨架，自绘只改一个组件；
 *  3. 自绘后可以统一处理「切 tab 前先判断登录态」这类逻辑。
 *
 * 代价：页面间切换只能用 redirectTo（原生 switchTab 在去掉 tabBar 后会失效），
 * 页面栈不会无限增长，但返回键在根页会退出应用——与示例 App 行为一致。
 *
 * 桌面（≥960px）整条不渲染：宽屏底部再挂一条移动端 tabBar 会与站点顶栏功能重复，
 * 且固定在视口底部的通栏条在桌面观感很差。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { isWide } from '@/common/web'
import { cartCount, refreshCartCount, onCartChanged, offCartChanged } from '@/common/cart-badge'

const props = defineProps({
  /** 当前选中：home / category / read / cart / mine */
  active: { type: String, default: 'home' }
})

const tabs = [
  {
    key: 'home',
    label: '首页',
    url: '/pages/index/index',
    icon: '/static/tabbar/home.png',
    iconActive: '/static/tabbar/home_active.png'
  },
  {
    key: 'category',
    label: '分类',
    url: '/pages/category/category',
    icon: '/static/tabbar/category.png',
    iconActive: '/static/tabbar/category_active.png'
  },
  {
    key: 'read',
    label: '书单',
    url: '/pages/booklist/list',
    icon: '/static/tabbar/read.png',
    iconActive: '/static/tabbar/read_active.png'
  },
  {
    key: 'cart',
    label: '购物车',
    url: '/pages/cart/cart',
    icon: '/static/tabbar/cart.png',
    iconActive: '/static/tabbar/cart_active.png'
  },
  {
    key: 'mine',
    label: '我的',
    url: '/pages/mine/mine',
    icon: '/static/tabbar/mine.png',
    iconActive: '/static/tabbar/mine_active.png'
  }
]

const badge = ref(cartCount.value)

/** 购物车变更广播 → 刷新角标；$off 必须传同一个 handler 引用 */
const handler = () => {
  badge.value = cartCount.value
}

onMounted(() => {
  refreshCartCount().then(() => {
    badge.value = cartCount.value
  })
  onCartChanged(handler)
})

onUnmounted(() => offCartChanged(handler))

function onTap(t) {
  if (t.key === props.active) return
  uni.redirectTo({ url: t.url })
}
</script>

<style lang="scss" scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: row;
  background-color: $bg-card;
  box-shadow: $shadow-float;
  padding-bottom: $safe-bottom;
  z-index: 60;
}

.tab-item {
  flex: 1;
  height: 100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.icon-wrap {
  position: relative;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 150ms ease;
}

.tab-icon {
  width: 40rpx;
  height: 40rpx;
}

.tab-label {
  margin-top: 4rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.active .icon-wrap {
  background-color: $brand-weak;
  transform: scale(1.05);
}

.active .tab-icon {
  transform: scale(1.05);
}

.active .tab-label {
  color: $brand;
  font-weight: 700;
}

.badge {
  position: absolute;
  top: -12rpx;
  left: 26rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-pill;
}
</style>
