<template>
  <view class="page">
    <page-nav title="确认订单" />
    <!-- ① 收货地址：有地址 / 空态引导两种形态 -->
    <view class="card addr-card" hover-class="hover-dim" @tap="chooseAddress">
      <template v-if="address.id">
        <view class="addr-top">
          <text class="receiver">{{ address.receiver }}</text>
          <text class="phone">{{ address.phone }}</text>
        </view>
        <text class="addr-detail">{{ address.fullAddress }}</text>
      </template>
      <view v-else class="addr-empty">
        <text class="addr-empty-title">请选择收货地址</text>
        <text class="addr-empty-sub">下单需要收货地址，还没有就先去新增一个</text>
      </view>
      <text class="arrow">›</text>
    </view>

    <!-- 加载骨架 -->
    <view v-if="loading" class="card goods-card">
      <view class="sk sk-anim" style="height: 32rpx; width: 30%; margin: 26rpx 0" />
      <view v-for="n in 2" :key="n" class="goods-item">
        <view class="cover sk sk-anim" />
        <view class="info">
          <view class="sk sk-anim" style="height: 28rpx; width: 85%" />
          <view class="sk sk-anim" style="height: 28rpx; width: 45%; margin-top: 16rpx" />
        </view>
      </view>
    </view>

    <!-- ② 商品清单 -->
    <view v-else class="card goods-card">
      <view class="card-title">商品清单</view>
      <view v-for="item in goods" :key="item.id" class="goods-item">
        <image class="cover" :src="coverSrc(item.coverUrl)" mode="aspectFill" />
        <view class="info">
          <text class="title ellipsis-2">{{ item.title }}</text>
          <view class="goods-bottom">
            <text class="price">¥{{ price(item.price) }}</text>
            <text class="qty">×{{ item.quantity }}</text>
          </view>
        </view>
      </view>
      <view class="sum-row">
        <text class="sum-label">共 {{ totalQuantity }} 件商品</text>
        <text class="price sum-price">¥{{ price(totalAmount) }}</text>
      </view>
    </view>

    <view class="bar-holder" />

    <!-- ③ 底部提交栏 -->
    <view class="footer">
      <view class="footer-sum">
        <text class="sum-label">应付：</text>
        <text class="price sum-price">¥{{ price(totalAmount) }}</text>
      </view>
      <view
        class="submit"
        :class="{ disabled: !canSubmit }"
        hover-class="hover-opacity"
        @tap="onSubmit"
      >
        {{ submitting ? '提交中...' : '提交订单' }}
      </view>
    </view>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { getCartList } from '@/api/cart'
import { getAddressList } from '@/api/address'
import PageNav from '@/components/page-nav.vue'
import { createOrder } from '@/api/order'
import { requireLogin } from '@/common/guard'
import { coverSrc } from '@/common/config'
import { price } from '@/common/format'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

const goods = ref([])
const address = ref({})
const loading = ref(true)
const submitting = ref(false)
/** 用户是否在地址页手动选过（区别于自动回填的默认地址） */
const addressPicked = ref(false)
/**
 * 「立即购买」定向结算的购物车条目 id 集合。
 * 详情页跳过来时携带，只结算这几条，避免把用户其他已勾选商品一起下单。
 */
const fixedCartIds = ref([])

const totalQuantity = computed(() => goods.value.reduce((s, i) => s + (i.quantity || 0), 0))

const totalAmount = computed(() =>
  goods.value.reduce((s, i) => s + Number(i.price || 0) * (i.quantity || 0), 0).toFixed(2)
)

const canSubmit = computed(
  () => !!address.value.id && goods.value.length > 0 && !submitting.value && !loading.value
)

/** 购物车条目（勾选的，或「立即购买」指定的） */
async function loadGoods() {
  loading.value = true
  try {
    const data = await getCartList()
    const list = data.list || []

    if (fixedCartIds.value.length) {
      goods.value = list.filter((i) => fixedCartIds.value.includes(String(i.id)))
    } else {
      goods.value = list.filter((i) => i.checked === 1)
    }

    // 同时校验库存，避免点了提交才发现缺货
    const notEnough = goods.value.find((i) => i.stockEnough === false)
    if (notEnough) {
      uni.showToast({ title: '部分商品库存不足，请返回调整', icon: 'none' })
    }

    if (!goods.value.length) {
      uni.showModal({
        title: '提示',
        content: '没有可结算的商品，请先勾选或重新加购',
        showCancel: false,
        success: () => uni.navigateBack()
      })
    }
  } catch (e) {
    goods.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 每次进入本页都会执行：手动选过且仍有效的地址优先保留，
 * 否则回落到默认地址——这样新增/删除地址后返回本页能自动带上最新结果。
 */
async function loadAddress() {
  try {
    const list = (await getAddressList()) || []
    const picked = addressPicked.value
      ? list.find((i) => i.id === address.value.id)
      : null
    if (picked) {
      address.value = picked
      return
    }
    address.value = list.find((i) => i.isDefault === 1) || list[0] || {}
  } catch (e) {}
}

function chooseAddress() {
  uni.navigateTo({ url: '/pages/address/list?from=order' })
}

async function onSubmit() {
  if (!address.value.id) {
    uni.showToast({ title: '请先选择收货地址', icon: 'none' })
    return
  }
  if (!goods.value.length) {
    uni.showToast({ title: '没有可结算的商品', icon: 'none' })
    return
  }
  if (submitting.value) return

  submitting.value = true
  uni.showLoading({ title: '提交中', mask: true })
  try {
    const order = await createOrder({
      addressId: address.value.id,
      cartIds: goods.value.map((i) => i.id)
    })
    uni.hideLoading()
    uni.showToast({ title: '下单成功', icon: 'success' })
    // 用 redirectTo：下单成功后不允许退回本页重复提交
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/order/detail?id=' + order.id + '&from=create' })
    }, 600)
  } catch (e) {
    uni.hideLoading()
    submitting.value = false
  }
}

function onAddressSelected(item) {
  address.value = item
  addressPicked.value = true
}

onLoad((options) => {
  if (!requireLogin()) return
  const q = options || {}
  // 详情页「立即购买」：/pages/order/confirm?cartIds=12,13
  if (q.cartIds) {
    fixedCartIds.value = String(q.cartIds).split(',').filter(Boolean)
  }
  loadGoods()
  uni.$on('addressSelected', onAddressSelected)
})

onShow(() => {
  loadAddress()
})

onUnload(() => {
  // 必须带回调移除，避免影响其他页面注册的同类监听
  uni.$off('addressSelected', onAddressSelected)
  submitting.value = false
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding: $gap-item 0 0;
  box-sizing: border-box;
}

.card {
  margin: 0 $gap-page $gap-item;
  padding: $gap-card;
  border-radius: $radius-lg;
  background-color: $bg-card;
  box-shadow: $shadow-card;
}

/* ---- 地址卡 ---- */
.addr-card {
  position: relative;
  padding-right: 72rpx;
  min-height: 152rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.addr-top {
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

.addr-detail {
  margin-top: 12rpx;
  font-size: $fs-base;
  line-height: 1.6;
  color: $text-regular;
}

.addr-empty {
  display: flex;
  flex-direction: column;
}

.addr-empty-title {
  font-size: $fs-lg;
  font-weight: 600;
  color: $brand;
}

.addr-empty-sub {
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-sub;
}

.arrow {
  position: absolute;
  right: $sp-3;
  top: 50%;
  transform: translateY(-50%);
  font-size: 42rpx;
  color: $text-placeholder;
}

/* ---- 商品清单 ---- */
.goods-card {
  padding: 0 $gap-card;
}

.card-title {
  padding: $sp-3 0;
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-main;
}

.goods-item {
  display: flex;
  flex-direction: row;
  padding: $sp-2 0;
  border-top: 1rpx solid $border-color;
}

.cover {
  width: 130rpx;
  height: 173rpx;
  flex-shrink: 0;
  border-radius: $radius-sm;
  background-color: $bg-skeleton;
}

.info {
  flex: 1;
  min-width: 0;
  margin-left: $sp-2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.title {
  font-size: $fs-md;
  line-height: 1.4;
  color: $text-main;
}

.goods-bottom {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10rpx;
}

.price {
  font-size: $fs-lg;
}

.qty {
  font-size: $fs-base;
  color: $text-sub;
}

.sum-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  padding: $sp-3 0;
  border-top: 1rpx solid $border-color;
}

.sum-label {
  font-size: $fs-base;
  color: $text-regular;
}

.sum-price {
  font-size: 34rpx;
  font-weight: 700;
}

/* ---- 底部提交栏 ---- */
.footer {
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

.footer-sum {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: flex-end;
  margin-right: $sp-3;
}

.submit {
  flex-shrink: 0;
  min-width: 220rpx;
  height: 76rpx;
  padding: 0 $sp-4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-pill;
}

.submit.disabled {
  background-color: $border-strong;
}
</style>
