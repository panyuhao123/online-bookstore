<template>
  <view class="page">
    <page-nav title="注册" />
    <view class="logo">
      <text class="logo-title">注册账号</text>
      <text class="logo-sub">注册后即可下单购书</text>
    </view>

    <view class="form">
      <view class="field" :class="{ 'field-error': errors.phone }">
        <text class="label">手机号</text>
        <input
          class="input"
          v-model="phone"
          type="number"
          maxlength="11"
          placeholder="请输入 11 位手机号"
          placeholder-class="ph"
          @input="errors.phone = ''"
        />
      </view>
      <text v-if="errors.phone" class="err-tip">{{ errors.phone }}</text>

      <view class="field">
        <text class="label">昵称</text>
        <input
          class="input"
          v-model="nickname"
          maxlength="20"
          placeholder="选填，不填自动生成"
          placeholder-class="ph"
        />
      </view>

      <view class="field" :class="{ 'field-error': errors.password }">
        <text class="label">密码</text>
        <input
          class="input"
          v-model="password"
          :password="!showPwd"
          maxlength="20"
          placeholder="6~20 位密码"
          placeholder-class="ph"
          @input="onPasswordInput"
        />
        <text class="eye" @tap="showPwd = !showPwd">{{ showPwd ? '🙂' : '🙈' }}</text>
      </view>
      <text v-if="errors.password" class="err-tip">{{ errors.password }}</text>

      <view class="field" :class="{ 'field-error': errors.confirm }">
        <text class="label">确认密码</text>
        <input
          class="input"
          v-model="confirmPassword"
          :password="!showPwd"
          maxlength="20"
          placeholder="请再次输入密码"
          placeholder-class="ph"
          confirm-type="done"
          @confirm="onRegister"
        />
          @input="errors.confirm = ''"
        />
      </view>
      <text v-if="errors.confirm" class="err-tip">{{ errors.confirm }}</text>

      <view
        class="submit"
        :class="{ disabled: submitting || !filled }"
        hover-class="hover-opacity"
        @tap="onRegister"
      >
        {{ submitting ? '注册中...' : '注 册' }}
      </view>

      <view class="links">
        <text class="link" @tap="goBack">已有账号？去登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { register, login } from '@/api/user'
import { mergeLocalCartAfterLogin } from '@/common/cart-local'
import { setToken, setUser } from '@/common/auth'
import { takeRedirect, clearRedirect } from '@/common/guard'
import PageNav from '@/components/page-nav.vue'

const phone = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const showPwd = ref(false)
const submitting = ref(false)
const errors = reactive({ phone: '', password: '', confirm: '' })
/** 注册完成后要回跳的页面（由登录页透传，H5 URL 直达场景用） */
const redirect = ref('')

/** 必填项都填了才放开按钮 */
const filled = computed(
  () => phone.value.trim().length === 11 && password.value.length >= 6 && confirmPassword.value.length > 0
)

/** 改密码时同步清掉「两次不一致」，否则用户改了却还挂着旧错误 */
function onPasswordInput() {
  errors.password = ''
  errors.confirm = ''
}

function validate() {
  errors.phone = ''
  errors.password = ''
  errors.confirm = ''
  let ok = true
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    errors.phone = '请输入正确的 11 位手机号'
    ok = false
  }
  if (password.value.length < 6 || password.value.length > 20) {
    errors.password = '密码长度需为 6~20 位'
    ok = false
  }
  if (password.value !== confirmPassword.value) {
    errors.confirm = '两次输入的密码不一致'
    ok = false
  }
  return ok
}

async function onRegister() {
  if (submitting.value) return
  if (!validate()) {
    uni.showToast({ title: errors.phone || errors.password || errors.confirm, icon: 'none' })
    return
  }
  submitting.value = true
  uni.showLoading({ title: '注册中', mask: true })
  try {
    const data = { phone: phone.value, password: password.value }
    if (nickname.value.trim()) data.nickname = nickname.value.trim()
    await register(data)

    // 注册成功后自动登录，省去再输一遍
    const loginData = await login({ phone: phone.value, password: password.value })
    setToken(loginData.token)
    setUser(loginData.user)
    // 与登录同样处理：把未登录期间的本地购物车合并到新账号
    const merged = await mergeLocalCartAfterLogin()
    uni.hideLoading()
    if (merged && merged.mergedCount > 0) {
      uni.showToast({ title: `注册成功，已同步 ${merged.mergedCount} 种商品到购物车`, icon: 'none' })
    } else {
      uni.showToast({ title: '注册成功', icon: 'success' })
    }
    setTimeout(() => {
      if (redirect.value) {
        clearRedirect()
        uni.reLaunch({ url: '/' + redirect.value })
      } else {
        uni.navigateBack()
      }
    }, 600)
  } catch (e) {
    uni.hideLoading()
  } finally {
    submitting.value = false
  }
}

function goBack() {
  uni.navigateBack()
}

onLoad(() => {
  // 与登录页共用同一份回跳目标（登录 → 注册的链路也要能回到来源页）
  redirect.value = takeRedirect()
})
</script>

<style lang="scss" scoped>
.page {
  padding: 0 $sp-8;
}

.logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 0 $sp-8;
}

.logo-title {
  font-size: 44rpx;
  font-weight: 700;
  color: $text-main;
}

.logo-sub {
  margin-top: 14rpx;
  font-size: $fs-base;
  color: $text-sub;
}

.form {
  padding: 0 $sp-4;
  border-radius: $radius-lg;
  background-color: $bg-card;
  box-shadow: $shadow-card;
}

.field {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 112rpx;
  border-bottom: 1rpx solid $border-color;
}

.field-error {
  border-bottom-color: $brand;
}

.label {
  width: 170rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $text-main;
}

.input {
  flex: 1;
  min-width: 0;
  font-size: $fs-md;
  color: $text-main;
}

.eye {
  flex-shrink: 0;
  padding: 0 $sp-2;
  font-size: 30rpx;
}

.err-tip {
  display: block;
  padding: 8rpx 0 0;
  font-size: $fs-xs;
  color: $brand;
}

.submit {
  margin-top: $sp-8;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-pill;
  font-size: 32rpx;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
}

.submit.disabled {
  background-color: $border-strong;
}

.links {
  padding: $sp-4 0 $sp-5;
  display: flex;
  flex-direction: row;
  justify-content: center;
}

.link {
  font-size: $fs-base;
  color: $brand;
}
</style>
