<template>
  <view class="page">
    <page-nav :title="from === 'order' ? '选择收货地址' : '收货地址'" />
    <!-- 加载骨架 -->
    <view v-if="loading" class="list">
      <view v-for="n in 2" :key="n" class="card">
        <view class="sk sk-anim" style="height: 32rpx; width: 40%" />
        <view class="sk sk-anim" style="height: 28rpx; width: 85%; margin-top: 18rpx" />
        <view class="sk sk-anim" style="height: 28rpx; width: 60%; margin-top: 40rpx" />
      </view>
    </view>

    <view v-else-if="list.length" class="list">
      <view
        v-for="item in list"
        :key="item.id"
        class="card"
        hover-class="hover-dim"
        @tap="onPick(item)"
      >
        <view class="row-1">
          <text class="receiver">{{ item.receiver }}</text>
          <text class="phone">{{ item.phone }}</text>
          <text v-if="item.isDefault === 1" class="tag">默认</text>
        </view>
        <text class="addr">{{ item.fullAddress }}</text>
        <view class="ops">
          <view class="op-radio" hover-class="hover-opacity" @tap.stop="onSetDefault(item)">
            <text class="dot">{{ item.isDefault === 1 ? '●' : '○' }}</text>
            <text class="op-text" :class="{ on: item.isDefault === 1 }">
              {{ item.isDefault === 1 ? '默认地址' : '设为默认' }}
            </text>
          </view>
          <view class="op-btns">
            <text class="op-text" @tap.stop="onEdit(item)">编辑</text>
            <text class="op-text danger" @tap.stop="onDelete(item)">删除</text>
          </view>
        </view>
      </view>
      <view class="list-holder" />
    </view>

    <!-- 空态：直接给新增入口，而不是只丢一句文案 -->
    <view v-else class="state-box">
      <view class="state-icon">📍</view>
      <text class="state-title">还没有收货地址</text>
      <text class="state-sub">新增一个，下单时就能直接选</text>
      <view class="state-btn" hover-class="hover-opacity" @tap="onAdd">+ 新增收货地址</view>
    </view>

    <view v-if="list.length" class="add-btn" hover-class="hover-opacity" @tap="onAdd">
      + 新增收货地址
    </view>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  getAddressList,
  removeAddress,
  setDefaultAddress
} from '@/api/address'
import PageNav from '@/components/page-nav.vue'
import { requireLogin } from '@/common/guard'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

/** from=order 时表示从确认订单页进入，点击地址即选中返回 */
const from = ref('')
const list = ref([])
const loading = ref(true)
/** 操作锁：默认地址切换与删除都不允许并发，避免列表顺序错乱 */
const busy = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = (await getAddressList()) || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onAdd() {
  uni.navigateTo({ url: '/pages/address/edit' })
}

function onEdit(item) {
  uni.navigateTo({ url: '/pages/address/edit?id=' + item.id })
}

function onDelete(item) {
  if (busy.value) return
  uni.showModal({
    title: '提示',
    content: `确定删除「${item.receiver}」的收货地址吗？`,
    success: async (res) => {
      if (!res.confirm) return
      busy.value = true
      try {
        await removeAddress(item.id)
        uni.showToast({ title: '已删除', icon: 'none' })
        await load()
      } catch (e) {
      } finally {
        busy.value = false
      }
    }
  })
}

async function onSetDefault(item) {
  if (busy.value || item.isDefault === 1) return
  busy.value = true
  uni.showLoading({ title: '设置中', mask: true })
  try {
    await setDefaultAddress(item.id)
    uni.hideLoading()
    uni.showToast({ title: '已设为默认地址', icon: 'none' })
    await load()
  } catch (e) {
    uni.hideLoading()
  } finally {
    busy.value = false
  }
}

/** 选择模式：把选中的地址回传给确认订单页 */
function onPick(item) {
  if (from.value !== 'order') return
  uni.$emit('addressSelected', item)
  uni.navigateBack()
}

onLoad((options) => {
  if (!requireLogin()) return
  // 标题由 page-nav 的 :title 随 from 响应式切换，不再用原生导航栏 API
  from.value = (options && options.from) || ''
})

onShow(() => {
  if (!requireLogin()) return
  load()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-top: $gap-item;
  box-sizing: border-box;
}

.list {
  padding: 0 $gap-page;
}

.card {
  padding: $sp-4 $gap-card;
  margin-bottom: $gap-item;
  border-radius: $radius-lg;
  background-color: $bg-card;
  box-shadow: $shadow-card;
}

.row-1 {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.receiver {
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-main;
}

.phone {
  margin-left: $sp-2;
  font-size: $fs-md;
  color: $text-regular;
}

.tag {
  margin-left: $sp-2;
}

.addr {
  display: block;
  margin-top: 14rpx;
  font-size: $fs-base;
  line-height: 1.6;
  color: $text-regular;
}

.ops {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-top: 22rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $border-color;
}

.op-radio {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 88rpx;
  margin: -20rpx 0;
  padding-right: $sp-4;
}

.dot {
  width: 32rpx;
  text-align: center;
  font-size: 26rpx;
  color: $border-strong;
}

.op-text {
  margin-left: 10rpx;
  font-size: $fs-base;
  color: $text-sub;
}

.op-text.on {
  color: $brand;
  font-weight: 600;
}

.op-btns {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.op-btns .op-text {
  height: 88rpx;
  margin: -20rpx 0;
  padding: 0 $sp-2;
  display: flex;
  align-items: center;
}

.danger {
  color: $brand;
}

.list-holder {
  height: 160rpx;
}

.add-btn {
  position: fixed;
  left: $gap-page;
  right: $gap-page;
  bottom: calc(#{$gap-page} + #{$safe-bottom});
  height: $btn-h;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-pill;
  z-index: 20;
}
</style>
