<script>
/**
 * 应用级生命周期（应用生命周期只能写在 App.vue 中）。
 */
import { autoDetectBaseUrl } from '@/common/config'
// #ifdef H5
import { startTitleSync } from '@/common/web'
// #endif

export default {
  onLaunch() {
    // #ifndef H5
    // 真机启动自动探测后端：电脑 IP 可能因 WiFi 重连被重新分配，
    // 命中可用地址后写入缓存，后续请求自动走新地址，无需改代码重新打包。
    autoDetectBaseUrl()
    // #endif

    // #ifdef H5
    // 桌面浏览器多标签时，标题随页面变化才分得清；APP 无标签概念，不编译
    startTitleSync()
    // #endif
  }
}
</script>

<style lang="scss">
/* ============================================================
   全局样式：作用于每一个页面（写在 App.vue 中即为全局）
   Token 来源：uni.scss；规范：docs/线上书店App_UI设计规范.md
   ============================================================ */

/* Web 端宽屏（≥960px）的桌面布局覆盖，集中维护，不散落到各页面 */
@import './common/web.scss';

page {
  background-color: $bg-page;
  font-size: $fs-md;
  color: $text-main;
  /* 官方文档：跨端字体栈，避免各平台默认字体差异 */
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica,
    'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

/* ---- 布局工具类 ---- */
.flex-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.flex-between {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.flex-col {
  display: flex;
  flex-direction: column;
}

.flex-1 {
  flex: 1;
  min-width: 0;
}

/* ---- 文本 ---- */
.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ellipsis-2 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.price {
  color: $price-color;
  font-weight: 600;
}

/* ---- 卡片（页面内通用，带 24rpx 左右外边距） ---- */
.card {
  margin: 0 $gap-page $gap-item;
  padding: $gap-card;
  background-color: $bg-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
}

.card-flush {
  margin: 0 $gap-page $gap-item;
  padding: 0 $gap-card;
  background-color: $bg-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
  overflow: hidden;
}

.sec-title {
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-main;
}

.divider {
  height: 1rpx;
  background-color: $border-color;
}

/* ---- 按钮 ---- */
.btn-primary {
  height: $btn-h;
  min-width: 200rpx;
  padding: 0 $sp-4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: #ffffff;
  background-color: $brand;
  border-radius: $radius-pill;
}

/* 禁用用灰底而非降透明度：避免用户误以为仍可点击 */
.btn-primary.disabled {
  background-color: $border-strong;
}

.btn-outline {
  height: $btn-h-sm;
  min-width: 160rpx;
  padding: 0 $sp-4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $brand;
  background-color: $bg-card;
  border: 1rpx solid $brand;
  border-radius: $radius-pill;
}

.btn-text {
  height: 64rpx;
  min-width: 120rpx;
  padding: 0 $sp-3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-base;
  color: $brand;
}

/* 统一按压反馈：uni-app 用 hover-class 比 :active 可靠（App 端 webview 差异） */
.hover-opacity {
  opacity: 0.82;
}

.hover-scale {
  transform: scale(0.97);
}

.hover-dim {
  background-color: $bg-page;
}

/* ---- 胶囊标签 ---- */
.chip {
  flex-shrink: 0;
  height: 56rpx;
  padding: 0 28rpx;
  margin-right: $sp-2;
  display: flex;
  align-items: center;
  font-size: $fs-base;
  color: $text-regular;
  background-color: $bg-fill;
  border-radius: $radius-pill;
}

.chip.active {
  color: #ffffff;
  font-weight: 600;
  background-color: $brand;
}

.tag {
  flex-shrink: 0;
  padding: 4rpx 16rpx;
  font-size: $fs-xs;
  color: $brand;
  background-color: $brand-weak;
  border-radius: $radius-sm;
}

.tag-outline {
  flex-shrink: 0;
  padding: 3rpx 14rpx;
  font-size: $fs-xs;
  color: $brand;
  border: 1rpx solid $brand;
  border-radius: $radius-sm;
}

.tag-warn {
  flex-shrink: 0;
  padding: 3rpx 14rpx;
  font-size: $fs-xs;
  color: $warning;
  background-color: #fff4e5;
  border-radius: $radius-sm;
}

.tag-muted {
  flex-shrink: 0;
  padding: 3rpx 14rpx;
  font-size: $fs-xs;
  color: $text-sub;
  background-color: $bg-fill;
  border-radius: $radius-sm;
}

/* ---- 骨架屏 ---- */
.sk {
  background-color: $bg-skeleton;
  border-radius: $radius-sm;
}

.sk-anim {
  animation: sk-breathe 1.2s ease-in-out infinite;
}

@keyframes sk-breathe {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

/* ---- 列表加载 / 空 / 失败 三态（各页复用） ---- */
.state-box {
  padding: $sp-8 $gap-page;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.state-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  background-color: $bg-skeleton;
  color: #ffffff;
}

/* 失败态图标底色：比骨架屏更冷，暗示「出错了」而非「加载中」 */
.state-icon-error {
  background-color: $bg-icon-muted;
}

.state-title {
  margin-top: $sp-4;
  font-size: $fs-md;
  color: $text-regular;
}

.state-sub {
  margin-top: $sp-2;
  font-size: $fs-sm;
  color: $text-placeholder;
  text-align: center;
  line-height: 1.6;
}

.state-btn {
  margin-top: $sp-5;
  height: $btn-h-sm;
  min-width: 240rpx;
  padding: 0 $sp-6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  font-weight: 600;
  color: #ffffff;
  background-color: $brand;
  border-radius: $radius-pill;
}

.load-more {
  padding: $sp-5 0 $sp-8;
  text-align: center;
  font-size: $fs-sm;
  color: $text-sub;
}

/* ---- 固定底栏 ---- */
.float-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $sp-2 $gap-page;
  padding-bottom: calc(#{$sp-2} + #{$safe-bottom});
  background-color: $bg-card;
  box-shadow: $shadow-float;
  z-index: 20;
}

/* 固定底栏的占位高度，防止内容被遮挡 */
.bar-holder {
  height: calc(#{$btn-h} + #{$sp-5} + #{$safe-bottom});
}

/* ---- 输入框 ---- */
.input {
  flex: 1;
  min-width: 0;
  font-size: $fs-md;
  color: $text-main;
}

.ph {
  color: $text-placeholder;
}

/* ---- 次要信息 ---- */
.muted {
  font-size: $fs-sm;
  color: $text-sub;
}
</style>
