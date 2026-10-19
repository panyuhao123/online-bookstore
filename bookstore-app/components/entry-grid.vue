<template>
  <!-- 功能宫格：4 列，首页 8 个 / 个人中心 8 个（紧凑） -->
  <view class="grid" :class="{ compact: compact }">
    <view
      v-for="e in entries"
      :key="e.id"
      class="cell"
      hover-class="hover-dim"
      @tap="onTap(e)"
    >
      <!-- 图形图标 + 完整中文名称：不再用「榜 / 类 / 服」这类单字缩写 -->
      <view class="icon" :class="'tone-' + e.tone">
        <entry-icon :name="e.icon" :tone="e.tone" />
      </view>
      <text class="label">{{ e.label }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 功能宫格。
 *
 * 入口一律是「图形图标 + 完整中文名称」：
 * 单字缩写（如「服」代表服务器）表意依赖用户猜，成熟电商的做法是图形表意、
 * 文字写全称。图标由 components/entry-icon.vue 用 CSS 绘制，不引入切图资源。
 *
 * 每个入口的 url 都是真实页面；needLogin 的入口会在点击前先判断登录态，
 * 未登录直接引导去登录，而不是跳进去再被拦截器弹回来。
 */
import { isLogin } from '@/common/auth'
import EntryIcon from '@/components/entry-icon.vue'

const props = defineProps({
  entries: { type: Array, required: true },
  /** 个人中心用紧凑排版 */
  compact: { type: Boolean, default: false },
  /** 默认跳转方式：tab 级页面用 redirect，二级页用 navigate */
  mode: { type: String, default: 'navigate' }
})

function onTap(e) {
  if (e.needLogin && !isLogin()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 500)
    return
  }
  if (!e.url) return
  // 单个 entry 的 mode 优先，其次用组件默认值
  const redirect = e.mode ? e.mode === 'redirect' : props.mode === 'redirect'
  if (redirect) {
    uni.redirectTo({ url: e.url })
    return
  }
  uni.navigateTo({ url: e.url })
}
</script>

<style lang="scss" scoped>
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin: $gap-item $gap-page 0;
  padding: $gap-card $sp-2;
  background-color: $bg-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
}

.compact {
  padding: $sp-3 $sp-2;
}

.cell {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $sp-2 0;
}

/* ---- 桌面：4 格宫格改成 PC 书城的横向快捷入口条 ----
   移动端竖向堆叠的宫格在 1200px 容器里会变成四个巨大色块，
   桌面更适合「一行横向入口」，视觉重心让给下方的商品楼层。 */
@media (min-width: 960px) {
  .grid {
    margin: 16px 24px 0;
    padding: 0;
    border-radius: 8px;
    flex-wrap: nowrap;
  }

  .cell {
    flex: 1;
    width: auto;
    flex-direction: row;
    justify-content: center;
    /* 桌面入口条要「呼吸感」：内边距比移动端宫格大，行高接近 64px，
       鼠标点击目标也更大，不像挤在一条窄带上 */
    padding: 22px 0;
  }

  .cell + .cell {
    border-left: 1px solid $border-color;
  }

  .icon {
    width: 40px;
    height: 40px;
    margin-bottom: 0;
    margin-right: 12px;
    border-radius: 10px;
  }

  .label {
    font-size: 15px;
  }
}

/* 桌面鼠标：整格给悬停底色，明确「这一格可以点」 */
@media (hover: hover) and (pointer: fine) {
  .cell {
    cursor: pointer;
    transition: background-color 0.15s ease;
  }

  .cell:hover {
    background-color: $bg-page;
  }

  .cell:hover .label {
    color: $brand;
  }
}

.icon {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $sp-1;
  border-radius: 24rpx;
}

/* 低饱和底 + 同系深字，避免高饱和大色块抢走商品图的注意力 */
.tone-orange { background-color: #fff1e8; }
.tone-red    { background-color: $brand-weak; }
.tone-blue   { background-color: #e6f1fb; }
.tone-green  { background-color: #eaf3de; }
.tone-purple { background-color: #eeedfe; }
.tone-amber  { background-color: #faeeda; }
.tone-teal   { background-color: #e1f5ee; }

.label {
  font-size: $fs-xs;
  color: $text-main;
}
</style>
