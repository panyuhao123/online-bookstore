<template>
  <view class="page">
    <page-nav title="发表评价" />
    <!-- ① 评价对象 -->
    <view class="card target">
      <text class="sec-title">评价图书</text>
      <text class="book-title ellipsis-2">{{ bookTitle || '图书 ' + bookId }}</text>
    </view>

    <!-- ② 星级：唯一必填项 -->
    <view class="card">
      <text class="sec-title">打分</text>
      <view class="stars-row">
        <text
          v-for="n in 5"
          :key="n"
          class="star"
          :class="{ on: n <= rating }"
          hover-class="hover-scale"
          @tap="setRating(n)"
        >
          {{ n <= rating ? '★' : '☆' }}
        </text>
        <text class="star-text">{{ ratingText }}</text>
      </view>
    </view>

    <!-- ③ 评价内容（选填） -->
    <view class="card">
      <text class="sec-title">评价内容</text>
      <textarea
        class="area"
        v-model="content"
        :maxlength="500"
        placeholder="说说这本书的内容、印刷或物流体验吧（选填，500 字以内）"
        placeholder-class="ph"
      />
      <text class="counter">{{ content.length }}/500</text>
    </view>

    <!-- ④ 匿名开关 -->
    <view class="card switch-card">
      <view class="switch-text">
        <text class="switch-title">匿名评价</text>
        <text class="switch-sub">开启后，评价列表显示「匿名用户」</text>
      </view>
      <!-- switch 的 color 只接受字符串字面量，用常量绑定避免色值散落在模板里 -->
      <switch :checked="anonymous" :color="SWITCH_COLOR" @change="onSwitch" />
    </view>

    <view class="bar-holder" />

    <!-- ⑤ 提交：固定在底部，避免键盘弹起时找不到按钮 -->
    <view class="float-bar">
      <view
        class="submit-btn"
        :class="{ disabled: !rating || submitting }"
        hover-class="hover-opacity"
        @tap="submit"
      >
        {{ submitting ? '提交中...' : '提交评价' }}
      </view>
    </view>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createReview } from '@/api/review'
import { requireLogin } from '@/common/guard'
import PageNav from '@/components/page-nav.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

/** 开关选中色，与 $brand 保持一致 */
const SWITCH_COLOR = '#C8362F'

/** 星级文案：给纯图形一个可读锚点，比只让用户看星星更好判断 */
const RATING_TEXTS = ['', '很差', '较差', '一般', '满意', '非常满意']

const orderId = ref(null)
const bookId = ref(null)
const bookTitle = ref('')
const rating = ref(0)
const content = ref('')
const anonymous = ref(false)
const submitting = ref(false)

const ratingText = computed(() => RATING_TEXTS[rating.value] || '点击星星打分')

function setRating(n) {
  rating.value = n
}

function onSwitch(e) {
  anonymous.value = !!e.detail.value
}

async function submit() {
  if (submitting.value) return
  if (!rating.value) {
    uni.showToast({ title: '请先选择星级', icon: 'none' })
    return
  }

  submitting.value = true
  uni.showLoading({ title: '提交中', mask: true })
  try {
    await createReview({
      orderId: Number(orderId.value),
      bookId: Number(bookId.value),
      rating: rating.value,
      content: content.value.trim(),
      anonymous: anonymous.value
    })
    uni.hideLoading()
    uni.showToast({ title: '评价成功', icon: 'success' })
    // 返回后由订单详情页的 onShow 重新拉取可评价状态，不依赖全局事件总线
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    uni.hideLoading()
  } finally {
    submitting.value = false
  }
}

onLoad((options) => {
  if (!requireLogin()) return
  orderId.value = (options && options.orderId) || null
  bookId.value = (options && options.bookId) || null
  // uni-app 不会自动解码 URL 参数，中文书名必须手动 decode
  bookTitle.value = options && options.title ? decodeURIComponent(options.title) : ''

  if (!orderId.value || !bookId.value) {
    uni.showToast({ title: '评价参数缺失', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
}

.card {
  margin: $gap-item $gap-page;
  padding: $gap-card;
  border-radius: $radius-lg;
  background-color: $bg-card;
  box-shadow: $shadow-card;
}

.book-title {
  margin-top: $sp-2;
  font-size: $fs-md;
  font-weight: 600;
  line-height: 1.5;
  color: $text-main;
}

/* ---- 星级 ---- */
.stars-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: $sp-3;
}

.star {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  line-height: 1;
  color: $border-strong;
}

.star.on {
  color: $warning;
}

.star-text {
  margin-left: $sp-2;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-regular;
}

/* ---- 内容 ---- */
.area {
  width: 100%;
  height: 240rpx;
  margin-top: $sp-2;
  padding: $sp-2;
  box-sizing: border-box;
  font-size: $fs-md;
  line-height: 1.7;
  color: $text-main;
  background-color: $bg-fill;
  border-radius: $radius-md;
}

.counter {
  display: block;
  margin-top: $sp-1;
  text-align: right;
  font-size: $fs-xs;
  color: $text-sub;
}

/* ---- 匿名 ---- */
.switch-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.switch-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.switch-title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-main;
}

.switch-sub {
  margin-top: 6rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

/* ---- 提交 ---- */
.submit-btn {
  height: $btn-h;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-pill;
}

.submit-btn.disabled {
  background-color: $border-strong;
}
</style>
