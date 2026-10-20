<template>
  <!-- 楼层标题：左红竖条 + 标题 + 右「更多」 -->
  <view class="floor-head">
    <view class="left">
      <view class="bar" />
      <text class="title">{{ title }}</text>
      <text v-if="sub" class="sub">{{ sub }}</text>
    </view>
    <view v-if="showMore" class="more" hover-class="hover-opacity" @tap="$emit('more')">
      <text class="more-text">{{ moreText }}</text>
      <text class="arrow">›</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  /** 标题右侧的小字补充（如榜单口径说明） */
  sub: { type: String, default: '' },
  moreText: { type: String, default: '更多' },
  showMore: { type: Boolean, default: true }
})

defineEmits(['more'])
</script>

<style lang="scss" scoped>
.floor-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin: $gap-block $gap-page $sp-2;
}

.left {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-width: 0;
}

.bar {
  flex-shrink: 0;
  width: 8rpx;
  height: 32rpx;
  margin-right: $sp-2;
  /* 红→橙渐变，对应 PC 端楼层标题条的红橙渐变底色；
     移动端标题不能铺渐变底（会压过书名），于是把渐变收到竖条上 */
  background: linear-gradient(180deg, $brand 0%, $orange 100%);
  border-radius: $radius-sm;
}

.title {
  flex-shrink: 0;
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-main;
}

.sub {
  margin-left: $sp-2;
  font-size: $fs-xs;
  color: $text-sub;
}

.more {
  flex-shrink: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $sp-1 0;
}

.more-text {
  font-size: $fs-xs;
  color: $text-sub;
}

.arrow {
  margin-left: 4rpx;
  font-size: $fs-base;
  color: $text-sub;
}

/* ---- 桌面：楼层标题放大成 PC 书城的栏目标题条 ---- */
@media (min-width: 960px) {
  .floor-head {
    /* 24px 与 web.scss 的 $web-pad 对齐（楼层栅格也是这个边距） */
    margin: 32px 24px 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid $border-color;
  }

  .bar {
    width: 4px;
    height: 20px;
  }

  .title {
    font-size: 20px;
  }

  .sub {
    font-size: 13px;
  }

  .more-text,
  .arrow {
    font-size: 13px;
  }
}

@media (hover: hover) and (pointer: fine) {
  .more {
    cursor: pointer;
  }

  .more:hover .more-text,
  .more:hover .arrow {
    color: $brand;
  }
}
</style>
