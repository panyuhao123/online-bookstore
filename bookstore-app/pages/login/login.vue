<template>
  <view class="page">
    <page-nav title="登录" />
    <view class="logo">
      <text class="logo-title">线上书店</text>
      <text class="logo-sub">好 book，在这里</text>
    </view>

    <view class="form">
      <view class="field" :class="{ 'field-error': errors.phone }">
        <text class="label">手机号</text>
        <input
          class="input"
          v-model="phone"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="ph"
          @input="errors.phone = ''"
        />
      </view>
      <text v-if="errors.phone" class="err-tip">{{ errors.phone }}</text>

      <view class="field" :class="{ 'field-error': errors.password }">
        <text class="label">密码</text>
        <input
          class="input"
          v-model="password"
          :password="!showPwd"
          maxlength="20"
          placeholder="请输入密码"
          placeholder-class="ph"
          confirm-type="go"
          @input="errors.password = ''"
          @confirm="onLogin"
        />
        <text class="eye" @tap="showPwd = !showPwd">{{ showPwd ? '🙂' : '🙈' }}</text>
      </view>
      <text v-if="errors.password" class="err-tip">{{ errors.password }}</text>

      <view
        class="submit"
        :class="{ disabled: submitting || !filled }"
        hover-class="hover-opacity"
        @tap="onLogin"
      >
        {{ submitting ? '登录中...' : '登 录' }}
      </view>

      <view class="links">
        <text class="link" @tap="goRegister">没有账号？去注册</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { login } from '@/api/user'
import { mergeLocalCartAfterLogin } from '@/common/cart-local'
import { setToken, setUser } from '@/common/auth'
import { takeRedirect, clearRedirect } from '@/common/guard'
import PageNav from '@/components/page-nav.vue'

const phone = ref('')
const password = ref('')
const showPwd = ref(false)
const submitting = ref(false)
/** 字段级错误提示，随输入清除，比 toast 更贴近表单填写场景 */
const errors = reactive({ phone: '', password: '' })
/** 从购物车等页面跳来时，登录成功后返回原页面 */
let fromStack = false
/** 登录后要回跳的页面（H5 由 ?redirect= 传入，已做站内路径校验） */
const redirect = ref('')

/** 两项都填了才允许提交，避免用户空点后才发现没输入 */
const filled = computed(
  () => phone.value.trim().length === 11 && password.value.length >= 6
)

/** 逐项校验并写回 errors，返回是否通过 */
function validate() {
  errors.phone = ''
  errors.password = ''
  let ok = true
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    errors.phone = '请输入正确的 11 位手机号'
    ok = false
  }
  if (password.value.length < 6 || password.value.length > 20) {
    errors.password = '密码长度需为 6~20 位'
    ok = false
  }
  return ok
}

async function onLogin() {
  if (submitting.value) return
  if (!validate()) {
    uni.showToast({ title: errors.phone || errors.password, icon: 'none' })
    return
  }
  submitting.value = true
  uni.showLoading({ title: '登录中', mask: true })
  try {
    const data = await login({ phone: phone.value, password: password.value })
    setToken(data.token)
    setUser(data.user)
    // 登录成功后把未登录期间的本地购物车合并到账号；合并失败不影响登录结果
    const merged = await mergeLocalCartAfterLogin()
    uni.hideLoading()
    if (merged && merged.mergedCount > 0) {
      const skipped = merged.skipped && merged.skipped.length
        ? `，${merged.skipped.length} 种未同步` : ''
      uni.showToast({ title: `已同步 ${merged.mergedCount} 种商品到购物车${skipped}`, icon: 'none' })
    } else {
      uni.showToast({ title: '登录成功', icon: 'success' })
    }
    setTimeout(() => {
      if (redirect.value) {
        // H5 URL 直达场景：回到用户原本想进的那一页
        clearRedirect()
        uni.reLaunch({ url: '/' + redirect.value })
      } else if (fromStack) {
        uni.navigateBack()
      } else {
        // 底部导航已改为自绘，原生 switchTab 失效，tab 级页面统一用 redirectTo
        uni.redirectTo({ url: '/pages/index/index' })
      }
    }, 600)
  } catch (e) {
    uni.hideLoading()
  } finally {
    submitting.value = false
  }
}

function goRegister() {
  // 回跳目标存在本地，注册页自己会取，不需要挂在 URL 上
  uni.navigateTo({ url: '/pages/login/register' })
}

onLoad(() => {
  // 页面栈大于 1 说明是从其他页面跳转过来的
  fromStack = getCurrentPages().length > 1
  // 由登录守卫或接口 401 写入：登录后回到用户原本想进的那一页
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
  padding: 120rpx 0 $sp-10;
}

.logo-title {
  font-size: 52rpx;
  font-weight: 700;
  color: $brand;
  letter-spacing: 6rpx;
}

.logo-sub {
  margin-top: $sp-2;
  font-size: $fs-base;
  color: $text-sub;
  letter-spacing: 4rpx;
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
  width: 150rpx;
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
