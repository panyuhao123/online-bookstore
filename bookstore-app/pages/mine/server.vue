<template>
  <view class="page">
    <view class="tip">
      本页仅供开发调试，不属于应用功能（由「我的」页连点头像 5 次进入）。
      手机访问的是电脑的局域网 IP，WiFi 重连后 IP 可能变化，
      导致 APP 提示「网络异常」。在这里可直接改地址，无需改代码重新打包。
    </view>

    <view class="card">
      <view class="card-title">当前地址</view>
      <text class="current">{{ currentUrl }}</text>
      <view class="state" :class="{ ok: state === 'ok', bad: state === 'bad' }">
        {{ stateText }}
      </view>
    </view>

    <view class="card">
      <view class="card-title">修改为</view>
      <input
        class="input"
        v-model="inputUrl"
        placeholder="http://192.168.1.100:8080"
        placeholder-class="ph"
      />
      <view class="btns">
        <view
          class="btn test"
          :class="{ disabled: testing }"
          hover-class="hover-opacity"
          @tap="onTest"
        >
          {{ testing ? '测试中...' : '测试连接' }}
        </view>
        <view class="btn save" hover-class="hover-opacity" @tap="onSave">保存并使用</view>
      </view>
    </view>

    <view class="card">
      <view class="card-title">候选地址（点击填入）</view>
      <view
        v-for="u in candidates"
        :key="u"
        class="cand"
        hover-class="hover-dim"
        @tap="inputUrl = u"
      >
        <text class="cand-text">{{ u }}</text>
        <text v-if="u === currentUrl" class="cand-tag">当前</text>
      </view>
      <view class="btn reset" hover-class="hover-opacity" @tap="onReset">恢复默认地址</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getBaseUrl,
  setBaseUrl,
  resetBaseUrl,
  candidateUrls,
  probe
} from '@/common/config'

const currentUrl = ref('')
const inputUrl = ref('')
const candidates = ref([])
const state = ref('') // '' | 'ok' | 'bad' | 'testing'
const stateText = ref('')
/** 测试锁：探测有 4 秒超时，期间禁止重复点击 */
const testing = ref(false)

function refresh() {
  currentUrl.value = getBaseUrl()
  if (!inputUrl.value) inputUrl.value = currentUrl.value
  candidates.value = candidateUrls()
  state.value = ''
  stateText.value = '未测试'
}

async function onTest() {
  if (testing.value) return
  const url = String(inputUrl.value || '').trim()
  if (!/^https?:\/\//.test(url)) {
    uni.showToast({ title: '格式应为 http://IP:8080', icon: 'none' })
    return
  }
  testing.value = true
  state.value = 'testing'
  stateText.value = '测试中…'
  try {
    const ok = await probe(url, 4000)
    state.value = ok ? 'ok' : 'bad'
    stateText.value = ok ? '连接成功，可保存' : '连接失败：确认后端已启动、手机与电脑同一 WiFi'
  } finally {
    testing.value = false
  }
}

function onSave() {
  const url = String(inputUrl.value || '').trim()
  if (!/^https?:\/\//.test(url)) {
    uni.showToast({ title: '格式应为 http://IP:8080', icon: 'none' })
    return
  }
  const save = () => {
    setBaseUrl(url)
    refresh()
    uni.showToast({ title: '已保存', icon: 'none' })
  }
  if (state.value === 'bad') {
    uni.showModal({
      title: '连接失败',
      content: '该地址测试未通过，仍要保存吗？',
      success: (r) => r.confirm && save()
    })
    return
  }
  save()
}

function onReset() {
  uni.showModal({
    title: '提示',
    content: '恢复为打包时写入的默认地址？',
    success: (r) => {
      if (!r.confirm) return
      resetBaseUrl()
      refresh()
      uni.showToast({ title: '已恢复默认', icon: 'none' })
    }
  })
}

onShow(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: $gap-item $gap-page calc(#{$sp-5} + #{$safe-bottom});
}

.tip {
  padding: $gap-card;
  font-size: $fs-base;
  line-height: 1.7;
  color: $text-regular;
  background-color: $warning-weak;
  border-radius: $radius-lg;
}

.card {
  margin-top: $gap-item;
  padding: $sp-4 $gap-card;
  border-radius: $radius-lg;
  background-color: $bg-card;
  border: 1rpx solid $border-color;
}

.card-title {
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-main;
}

.current {
  display: block;
  margin-top: 18rpx;
  font-size: $fs-lg;
  font-weight: 600;
  color: $brand;
}

.state {
  margin-top: 14rpx;
  font-size: $fs-base;
  color: $text-sub;
}

.state.ok {
  color: $success;
}

.state.bad {
  color: $brand;
}

.input {
  margin-top: 18rpx;
  padding: $sp-3;
  font-size: $fs-md;
  color: $text-main;
  background-color: $bg-page;
  border-radius: $radius-md;
}

.ph {
  color: $text-placeholder;
}

.btns {
  display: flex;
  flex-direction: row;
  margin-top: $sp-3;
}

.btn {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  font-weight: 600;
  border-radius: $radius-btn;
}

.test {
  margin-right: $sp-2;
  color: $text-main;
  background-color: $bg-fill;
}

.test.disabled {
  color: $text-sub;
}

.save {
  color: $color-white;
  background-color: $brand;
}

.cand {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $border-color;
}

.cand-text {
  font-size: $fs-base;
  color: $text-main;
}

.cand-tag {
  font-size: $fs-xs;
  font-weight: 600;
  color: $brand;
}

.reset {
  margin-top: $sp-3;
  color: $brand;
  background-color: $brand-weak;
}
</style>
