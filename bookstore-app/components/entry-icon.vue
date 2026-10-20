<template>
  <!--
    功能宫格图标：纯 view + CSS 绘制的线性图形，不引入任何图片资源。

    为什么不用单字：单字（榜 / 类 / 服）本质是「文字缩写」，表意依赖用户猜；
    为什么不用 PNG：一套切图要随屏幕密度出 2x/3x，且改色要重新出图。
    CSS 图形随 tone 的 currentColor 自动变色，缩放无损，且完全原创。
  -->
  <view class="entry-icon" :class="['ic-' + tone, size]">
    <!-- 分类导航：四宫格 -->
    <view v-if="name === 'category'" class="glyph g-grid">
      <view v-for="n in 4" :key="n" class="cell" />
    </view>

    <!-- 书单：三条长度递减的榜单条 -->
    <view v-else-if="name === 'rank'" class="glyph">
      <view class="g-bar" style="top: 11rpx; width: 38rpx" />
      <view class="g-bar" style="top: 22rpx; width: 29rpx" />
      <view class="g-bar" style="top: 33rpx; width: 20rpx" />
    </view>

    <!-- 我的订单：单据 + 三行条目 -->
    <view v-else-if="name === 'order'" class="glyph">
      <view class="g-paper" />
      <view class="g-line" style="top: 14rpx" />
      <view class="g-line" style="top: 23rpx" />
      <view class="g-line" style="top: 32rpx" />
    </view>

    <!-- 个人中心：人形轮廓 -->
    <view v-else-if="name === 'mine'" class="glyph">
      <view class="g-head" />
      <view class="g-shoulder" />
    </view>

    <!-- 收货地址：定位针 -->
    <view v-else-if="name === 'address'" class="glyph">
      <view class="g-pin" />
      <view class="g-pin-dot" />
      <view class="g-tail" />
    </view>

    <!-- 新书上架：书本 + 书脊 -->
    <view v-else-if="name === 'new'" class="glyph">
      <view class="g-book" />
      <view class="g-spine" />
    </view>

    <!-- 低价优选：价格标签 + ¥ -->
    <view v-else-if="name === 'price'" class="glyph">
      <view class="g-tagbox" />
      <text class="g-yen">¥</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  /** 图形名，见模板分支 */
  name: { type: String, required: true },
  /** 配色档位，与 entry-grid 的 tone 一致，决定图标线条颜色 */
  tone: { type: String, default: 'red' },
  /** 尺寸：normal 用于宫格，small 用于顶部导航等狭小位置 */
  size: { type: String, default: 'normal' }
})
</script>

<style lang="scss" scoped>
/* 48rpx 是图标的绘制坐标系：所有子元素按这个方框绝对定位 */
.entry-icon {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.entry-icon.small {
  width: 48rpx;
  height: 48rpx;
}

.entry-icon.small .glyph {
  transform: scale(0.58);
}

.glyph {
  position: relative;
  width: 48rpx;
  height: 48rpx;
}

/* 线条统一走 currentColor：换 tone 只改一个 color，图形自动跟随 */
.g-grid .cell,
.g-bar,
.g-line,
.g-pin-dot,
.g-spine,
.g-head,
.g-shoulder {
  background-color: currentColor;
}

.g-paper,
.g-pin,
.g-book,
.g-tagbox {
  border-color: currentColor;
}

.g-yen {
  color: currentColor;
}

/* ---- 分类：四宫格 ---- */
.g-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 5rpx;
}

.g-grid .cell {
  width: 20rpx;
  height: 20rpx;
  border-radius: 4rpx;
}

/* ---- 书单：榜单条 ---- */
.g-bar {
  position: absolute;
  left: 5rpx;
  height: 6rpx;
  border-radius: 3rpx;
}

/* ---- 订单 ---- */
.g-paper {
  position: absolute;
  left: 9rpx;
  top: 4rpx;
  width: 30rpx;
  height: 40rpx;
  box-sizing: border-box;
  border-width: 4rpx;
  border-style: solid;
  border-radius: 6rpx;
}

.g-line {
  position: absolute;
  left: 16rpx;
  width: 16rpx;
  height: 4rpx;
  border-radius: 2rpx;
}

/* ---- 我的 ---- */
.g-head {
  position: absolute;
  left: 14rpx;
  top: 4rpx;
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
}

.g-shoulder {
  position: absolute;
  left: 6rpx;
  top: 28rpx;
  width: 36rpx;
  height: 16rpx;
  border-radius: 10rpx 10rpx 0 0;
}

/* ---- 地址：定位针 ---- */
.g-pin {
  position: absolute;
  left: 10rpx;
  top: 4rpx;
  width: 28rpx;
  height: 28rpx;
  box-sizing: border-box;
  border-width: 4rpx;
  border-style: solid;
  border-radius: 50%;
}

.g-pin-dot {
  position: absolute;
  left: 20rpx;
  top: 14rpx;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
}

/* 针尖：宽高为 0 + 左右透明边框，只留上边框的实心三角 */
.g-tail {
  position: absolute;
  left: 16rpx;
  top: 30rpx;
  width: 0;
  height: 0;
  border-left-width: 8rpx;
  border-right-width: 8rpx;
  border-top-width: 14rpx;
  border-top-style: solid;
  border-top-color: currentColor;
  border-left-color: transparent;
  border-right-color: transparent;
}

/* ---- 新书：书本 ---- */
.g-book {
  position: absolute;
  left: 8rpx;
  top: 6rpx;
  width: 32rpx;
  height: 36rpx;
  box-sizing: border-box;
  border-width: 4rpx;
  border-style: solid;
  border-radius: 4rpx;
}

.g-spine {
  position: absolute;
  left: 12rpx;
  top: 6rpx;
  width: 4rpx;
  height: 36rpx;
}

/* ---- 低价：价格标签 ---- */
.g-tagbox {
  position: absolute;
  left: 4rpx;
  top: 10rpx;
  width: 40rpx;
  height: 28rpx;
  box-sizing: border-box;
  border-width: 4rpx;
  border-style: solid;
  border-radius: 10rpx;
}

.g-yen {
  position: absolute;
  left: 0;
  top: 15rpx;
  width: 48rpx;
  text-align: center;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1;
}

/* ---- 配色档位：与 entry-grid 的色块同源（低饱和块 + 同系深字） ---- */
.ic-red { color: $brand; }
.ic-orange { color: $orange-dark; }
.ic-blue { color: #185fa5; }
.ic-green { color: #3b6d11; }
.ic-purple { color: #534ab7; }
.ic-amber { color: #854f0b; }
.ic-teal { color: #0f6e56; }
/* 中性档：顶栏这类「顺带提供」的入口用它，把橙色让给搜索与购物车 */
.ic-grey { color: $text-regular; }
</style>
