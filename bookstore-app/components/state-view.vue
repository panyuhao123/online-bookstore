<template>
  <!-- 加载中 / 空数据 / 请求失败 三态（样式复用 App.vue 全局 state-box） -->
  <view v-if="visible" class="state-box">
    <template v-if="type === 'loading'">
      <view class="dot-row">
        <view v-for="n in 3" :key="n" class="dot sk-anim" :style="{ animationDelay: n * 0.15 + 's' }" />
      </view>
      <text class="state-title">{{ text || '加载中...' }}</text>
    </template>

    <template v-else-if="type === 'empty'">
      <view class="circle">空</view>
      <text class="state-title">{{ text || '暂无内容' }}</text>
      <text v-if="subText" class="state-sub">{{ subText }}</text>
      <view v-if="btnText" class="state-btn" hover-class="hover-opacity" @tap="$emit('retry')">
        {{ btnText }}
      </view>
    </template>

    <template v-else>
      <view class="circle error">!</view>
      <text class="state-title">{{ text || '加载失败' }}</text>
      <text class="state-sub">{{ subText || '网络连接失败，请检查网络后重试' }}</text>
      <view v-if="btnText" class="state-btn" hover-class="hover-opacity" @tap="$emit('retry')">
        {{ btnText }}
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 三态反馈组件：type = loading / empty / error / none。
 * 骨架屏单独用 .sk 处理（列表结构已知时比整屏 loading 体验更好），
 * 这里负责「整块不知道长什么样」的场景。
 *
 * 只认三个值，其余（done / none / 未定义）一律不渲染：
 * 早期版本用 v-else 兜底 error，导致传 'done' 时会误显示「加载失败」。
 */
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: 'none' },
  text: { type: String, default: '' },
  subText: { type: String, default: '' },
  btnText: { type: String, default: '' }
})

defineEmits(['retry'])

const visible = computed(() => ['loading', 'empty', 'error'].includes(props.type))
</script>

<style lang="scss" scoped>
.dot-row {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
}

.dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background-color: $bg-skeleton;
}

.circle {
  width: 120rpx;
  height: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  color: $color-white;
  background-color: $bg-skeleton;
  border-radius: 50%;
}

.circle.error {
  background-color: $bg-icon-muted;
  color: $text-sub;
}
</style>
