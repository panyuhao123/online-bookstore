<template>
  <!-- 运营轮播：3 张静态运营位，每张对应一个真实可达的列表页 -->
  <view class="banner-wrap">
    <swiper
      class="banner"
      :autoplay="true"
      :interval="4000"
      :circular="true"
      @change="onChange"
    >
      <swiper-item v-for="b in banners" :key="b.title">
        <view class="banner-inner" hover-class="hover-scale" @tap="onTap(b)">
          <view class="texts">
            <text class="title">{{ b.title }}</text>
            <text class="sub">{{ b.sub }}</text>
          </view>
          <view class="deco">
            <text class="deco-char">{{ b.char }}</text>
          </view>
        </view>
      </swiper-item>
    </swiper>
    <view class="dots">
      <view v-for="(b, i) in banners" :key="b.title" class="dot" :class="{ on: i === current }" />
    </view>
  </view>
</template>

<script setup>
/**
 * 首页运营位。
 *
 * 用 swiper 而不是静态图：示例 App 的 Banner 是会轮播的，而本项目没有运营图
 * 下发能力，所以用渐变色块 + 文案代替图片，但跳转目标都是真实存在的列表页，
 * 不做「点了只弹提示」的假按钮。
 */
import { ref } from 'vue'

const banners = [
  {
    title: '新书上架',
    sub: '每周更新 · 抢先读',
    char: '新',
    url: '/pages/book/new'
  },
  {
    title: '书单',
    sub: '编辑精选 · 按主题挑书',
    char: '单',
    url: '/pages/booklist/list'
  },
  {
    title: '低价优选',
    sub: '折扣专区 · 学生党友好',
    char: '惠',
    url: '/pages/book/cheap'
  }
]

const current = ref(0)

function onChange(e) {
  current.value = e.detail.current
}

function onTap(b) {
  uni.navigateTo({ url: b.url })
}
</script>

<style lang="scss" scoped>
.banner {
  position: relative;
  height: 240rpx;
  margin: $gap-item $gap-page 0;
  border-radius: $radius-lg;
  overflow: hidden;
}

.banner-inner {
  position: relative;
  height: 240rpx;
  padding: 0 $gap-card;
  display: flex;
  flex-direction: row;
  align-items: center;
  border-radius: $radius-lg;
  background: linear-gradient(120deg, $brand-light 0%, $brand 55%, $brand-dark 100%);
}

.texts {
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 44rpx;
  font-weight: 700;
  color: $color-white;
}

.sub {
  margin-top: $sp-1;
  font-size: $fs-sm;
  color: $color-white;
  opacity: 0.9;
}

.deco {
  position: absolute;
  right: 32rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 140rpx;
  height: 140rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(255, 255, 255, 0.18);
  border-radius: 50%;
}

.deco-char {
  font-size: 64rpx;
  font-weight: 700;
  color: $color-white;
}

.dots {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16rpx;
  display: flex;
  flex-direction: row;
  justify-content: center;
  gap: 10rpx;
  z-index: 2;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: $radius-pill;
  background-color: rgba(255, 255, 255, 0.55);
  box-shadow: 0 1rpx 3rpx rgba(0, 0, 0, 0.15);
  transition: width 150ms ease;
}

.dot.on {
  width: 28rpx;
  background-color: $color-white;
}

/* ---- 桌面：Banner 拉高到 PC 书城的主视觉尺寸 ---- */
@media (min-width: 960px) {
  .banner,
  .banner-inner {
    height: 300px;
    border-radius: 8px;
  }

  .banner {
    margin: 24px;
  }

  .banner-inner {
    padding: 0 48px;
  }

  .title {
    font-size: 34px;
  }

  .sub {
    margin-top: 12px;
    font-size: 16px;
  }

  .deco {
    right: 72px;
    width: 180px;
    height: 180px;
  }

  .deco-char {
    font-size: 84px;
  }

  .dots {
    bottom: 20px;
  }
}

@media (hover: hover) and (pointer: fine) {
  .banner-inner {
    cursor: pointer;
  }
}
</style>
