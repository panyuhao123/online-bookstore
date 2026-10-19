<template>
  <!-- 宽屏（桌面）：交给站点顶栏，Logo / 一级导航 / 搜索 / 购物车 都在这里 -->
  <web-header v-if="isWide" />
  <!-- 窄屏（APP / 手机浏览器）：状态栏占位 + Logo + 搜索区 + 右侧两个入口 -->
  <view v-else class="nav-bar">
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />
    <view class="bar-row">
      <text class="logo">书</text>

      <!-- 可编辑态（列表页）：内嵌输入框，回车即搜索 -->
      <view v-if="editable" class="search-box">
        <text class="search-icon">⌕</text>
        <input
          class="search-input"
          v-model="inputText"
          :placeholder="placeholder"
          placeholder-class="ph"
          confirm-type="search"
          :focus="autofocus"
          @confirm="submit"
        />
        <text v-if="inputText" class="clear-btn" @tap="clear">×</text>
      </view>
      <!-- 只读态（首页等）：整块点击跳搜索页，避免在每个页面重复写跳转 -->
      <view v-else class="search-box" hover-class="hover-dim" @tap="$emit('search-tap')">
        <text class="search-icon">⌕</text>
        <text class="ph ellipsis">{{ keyword || placeholder }}</text>
      </view>

      <view class="icon-btn" hover-class="hover-scale" @tap="goOrders">
        <entry-icon name="order" tone="orange" size="small" />
      </view>
      <view class="icon-btn" hover-class="hover-scale" @tap="goMine">
        <entry-icon name="mine" tone="teal" size="small" />
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 顶部导航条（页面需在 pages.json 里配 navigationStyle: custom）。
 *
 * custom 之后系统状态栏与标题栏都会消失，所以顶部安全区必须自己补，
 * 否则内容会被状态栏压住（真机上尤其明显）。
 *
 * 右侧两个入口沿用示例 App 的「图标位」结构，但绑定的是本项目真实存在的页面
 * （我的订单 / 我的），不做点不开的占位按钮。
 *
 * 桌面（≥960px）直接换成 web-header：一级导航、搜索、购物车角标都在站点顶栏里，
 * 这里再渲染一套移动端导航会重复占位。
 */
import { ref, watch } from 'vue'
import { isLogin } from '@/common/auth'
import { isWide } from '@/common/web'
import EntryIcon from '@/components/entry-icon.vue'
import WebHeader from '@/components/web-header.vue'

const props = defineProps({
  /** 只读态回显的关键词 */
  keyword: { type: String, default: '' },
  placeholder: { type: String, default: '搜索图书 / 作者 / 出版社' },
  /** true 时渲染成可输入的表单（列表页用） */
  editable: { type: Boolean, default: false },
  /** 首次进入是否自动聚焦（从分类页跳过来时用） */
  autofocus: { type: Boolean, default: false }
})

const emit = defineEmits(['search-tap', 'search'])

const inputText = ref(props.keyword)
watch(
  () => props.keyword,
  (v) => {
    inputText.value = v
  }
)

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getSystemInfoSync().statusBarHeight || 0
} catch (e) {
  statusBarHeight.value = 0
}

function submit() {
  // H5 没有软键盘可收，uni.hideKeyboard 在该平台不存在（调用只会报错）
  // #ifndef H5
  uni.hideKeyboard()
  // #endif
  emit('search', (inputText.value || '').trim())
}

function clear() {
  inputText.value = ''
  emit('search', '')
}

/** 需登录的入口统一引导，避免用户点了才发现没登录 */
function goIfLogin(url) {
  if (!isLogin()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 500)
    return
  }
  uni.navigateTo({ url })
}

function goOrders() {
  goIfLogin('/pages/order/list')
}

function goMine() {
  uni.redirectTo({ url: '/pages/mine/mine' })
}
</script>

<style lang="scss" scoped>
.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  background-color: $bg-card;
}

.bar-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 88rpx;
  padding: 0 $gap-page;
}

.logo {
  flex-shrink: 0;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: $sp-2;
  font-size: $fs-lg;
  font-weight: 700;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-md;
}

.search-box {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 $sp-3;
  background-color: $bg-page;
  border: 1rpx solid $brand;
  border-radius: $radius-pill;
}

.search-icon {
  flex-shrink: 0;
  margin-right: $sp-2;
  font-size: $fs-lg;
  color: $brand;
}

.search-input {
  flex: 1;
  min-width: 0;
  font-size: $fs-base;
  color: $text-main;
}

.clear-btn {
  flex-shrink: 0;
  padding: 0 8rpx;
  font-size: 34rpx;
  color: $text-placeholder;
}

.icon-btn {
  flex-shrink: 0;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: $sp-2;
}

</style>
