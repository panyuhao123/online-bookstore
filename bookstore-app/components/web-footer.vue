<template>
  <!-- 桌面页脚：只在宽屏渲染。移动端没有这一层（底部是自绘 tabBar） -->
  <view class="wf">
    <view class="wf-inner">
      <view class="wf-brand">
        <view class="wf-logo">
          <text class="wf-mark">书</text>
          <text class="wf-name">线上书店</text>
        </view>
        <text class="wf-desc">软件工程本科毕业设计演示项目</text>
      </view>

      <!-- 三列站内导航：只放真实存在的页面，不做点不开的占位链接 -->
      <view class="wf-cols">
        <view v-for="col in COLUMNS" :key="col.title" class="wf-col">
          <text class="wf-col-title">{{ col.title }}</text>
          <text v-for="i in col.items" :key="i.label" class="wf-link" @tap="go(i)">
            {{ i.label }}
          </text>
        </view>
      </view>

      <view class="wf-note">
        <text class="wf-note-title">演示说明</text>
        <text class="wf-note-text">本站为毕业设计演示，支付为模拟流程，不涉及真实交易。</text>
      </view>
    </view>

    <view class="wf-bottom">
      <view class="wf-bottom-inner">
        <text class="wf-copy">© 2026 线上书店 · 毕业设计演示</text>
        <text class="wf-tech">uni-app + Spring Boot + MySQL</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * Web 桌面页脚。
 *
 * PC 书城版式的收尾组件，移动端不渲染（移动端底部是自绘 tabBar）。
 * 链接只放真实存在的页面——项目规范⑧「不做无后端支撑的空壳功能」，
 * 摆一排点不开的「帮助中心 / 招商合作」反而是扣分项。
 */
import { SITE_NAV } from '@/common/web'

const COLUMNS = [
  {
    title: '选购图书',
    items: [
      { label: '分类导航', url: '/pages/category/category', mode: 'redirect' },
      { label: '新书上架', url: '/pages/book/new' },
      { label: '低价优选', url: '/pages/book/cheap' }
    ]
  },
  {
    title: '我的交易',
    items: [
      { label: '我的订单', url: '/pages/order/list' },
      { label: '购物车', url: '/pages/cart/cart', mode: 'redirect' },
      { label: '收货地址', url: '/pages/address/list' }
    ]
  },
  {
    title: '逛一逛',
    items: [
      { label: '书单', url: '/pages/booklist/list', mode: 'redirect' },
      { label: '个人中心', url: '/pages/mine/mine', mode: 'redirect' },
      { label: SITE_NAV[0].label, url: '/pages/index/index', mode: 'redirect' }
    ]
  }
]

/**
 * 跳转方式：底部 tabBar 是自绘的，原生 switchTab 失效，
 * tab 级页面必须 redirectTo（与 tab-bar.vue / web-header.vue 保持一致）。
 */
function go(item) {
  if (item.mode === 'redirect') {
    uni.redirectTo({ url: item.url })
  } else {
    uni.navigateTo({ url: item.url })
  }
}
</script>

<style lang="scss" scoped>
.wf {
  margin-top: 32px;
  background-color: $bg-card;
  border-top: 1px solid $border-color;
}

.wf-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.wf-brand {
  flex-shrink: 0;
  width: 220px;
}

.wf-logo {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.wf-mark {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  color: $color-white;
  background-color: $brand;
  border-radius: 6px;
}

.wf-name {
  margin-left: 8px;
  font-size: 16px;
  font-weight: 700;
  color: $text-main;
}

.wf-desc {
  display: block;
  margin-top: 10px;
  font-size: 12px;
  color: $text-placeholder;
}

.wf-cols {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: row;
}

.wf-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.wf-col-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: $text-main;
}

.wf-link {
  margin-bottom: 8px;
  font-size: 13px;
  color: $text-regular;
}

.wf-note {
  flex-shrink: 0;
  width: 230px;
  padding: 12px 14px;
  background-color: $bg-page;
  border-radius: 6px;
}

.wf-note-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: $text-main;
}

.wf-note-text {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: $text-placeholder;
}

.wf-bottom {
  border-top: 1px solid $border-color;
}

.wf-bottom-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 14px 24px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.wf-copy,
.wf-tech {
  font-size: 12px;
  color: $text-placeholder;
}

@media (hover: hover) and (pointer: fine) {
  .wf-link {
    cursor: pointer;
    transition: color 0.15s ease;
  }

  .wf-link:hover {
    color: $brand;
  }
}
</style>
