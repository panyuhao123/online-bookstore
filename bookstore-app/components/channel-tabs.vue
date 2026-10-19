<template>
  <!-- 横向滚动频道条：选中红字 + 底部红线，选中项自动滚动到可视区 -->
  <scroll-view
    class="channel-bar"
    scroll-x
    :show-scrollbar="false"
    :scroll-into-view="'tab_' + String(active)"
    scroll-with-animation
    :style="{ top: top + 'px' }"
  >
    <view class="inner">
      <view
        v-for="c in channels"
        :key="String(c.id)"
        :id="'tab_' + String(c.id)"
        class="item"
        :class="{ on: String(active) === String(c.id) }"
        hover-class="hover-opacity"
        @tap="$emit('change', c.id)"
      >
        {{ c.name }}
      </view>
    </view>
  </scroll-view>
</template>

<script setup>
/**
 * 频道条：切的是「货品池」，与底部 Tab（切的是任务场景）是两套导航。
 * top 需要传自定义导航的总高度（状态栏 + 88rpx 导航行），否则会盖住内容。
 */
defineProps({
  channels: { type: Array, required: true },
  active: { type: [String, Number], default: '' },
  /** 距视口顶部的 px */
  top: { type: Number, default: 0 }
})

defineEmits(['change'])
</script>

<style lang="scss" scoped>
.channel-bar {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 45;
  background-color: $bg-card;
  white-space: nowrap;
  border-bottom: 1rpx solid $border-color;
}

.inner {
  display: flex;
  flex-direction: row;
  padding: 0 $gap-page;
}

.item {
  flex-shrink: 0;
  padding: 20rpx 28rpx;
  font-size: $fs-md;
  color: $text-main;
  border-bottom: 4rpx solid transparent;
}

.item.on {
  color: $brand;
  font-weight: 600;
  border-bottom-color: $brand;
}
</style>
