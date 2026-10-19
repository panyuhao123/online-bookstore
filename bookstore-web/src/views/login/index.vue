<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { takeRedirect } from '@/utils/storage'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '@/api/captcha'
import { Cellphone, Lock, Key as VKey, InfoFilled, Reading, RefreshLeft, EditPen } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()

const loading = ref(false)
const captchaLoading = ref(false)
const formRef = ref(null)

// 图形验证码状态
const captchaKey = ref('')
const captchaImg = ref('')
const captchaCode = ref('')

// 登录表单
const loginForm = reactive({
  phone: '',
  password: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

/** 刷新图形验证码 */
async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const res = await getCaptcha()
    captchaKey.value = res.captchaKey
    captchaImg.value = res.imageBase64
    captchaCode.value = ''
  } catch (e) {
    ElMessage.error('验证码获取失败，请检查后端是否启动')
  } finally {
    captchaLoading.value = false
  }
}

function fillDemo() {
  loginForm.phone = '13800138000'
  loginForm.password = '123456'
  ElMessage.success('已填充演示账号，请输入图形验证码后登录')
}

async function onSubmit() {
  if (!formRef.value) return
  if (!captchaCode.value) {
    ElMessage.warning('请输入图形验证码')
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await userStore.login({
      phone: loginForm.phone,
      password: loginForm.password,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value
    })
    ElMessage.success('登录成功')
    await cartStore.fetchCart().catch(() => {})
    const redirect = takeRedirect() || route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    // 错误已在 Axios 拦截器提示；若验证码错误自动刷新
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 从注册页跳过来时会带上手机号，省得再输一遍
  if (route.query.phone) loginForm.phone = String(route.query.phone)
  refreshCaptcha()
})
</script>

<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="login-card card">
        <div class="login-header">
          <el-icon :size="32" color="var(--brand)"><Reading /></el-icon>
          <h1>线上书店</h1>
          <p>欢迎登录 PC 端</p>
        </div>

        <el-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="onSubmit"
        >
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="loginForm.phone" placeholder="请输入手机号" :prefix-icon="Cellphone" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
          </el-form-item>

          <!-- 图形验证码 -->
          <el-form-item label="图形验证码">
            <div class="captcha-row">
              <el-input
                v-model="captchaCode"
                placeholder="请输入右侧验证码"
                :prefix-icon="VKey"
                maxlength="4"
                @keyup.enter="onSubmit"
              />
              <img
                :src="captchaImg"
                class="captcha-img"
                :title="'点击刷新'"
                @click="refreshCaptcha"
              />
              <el-tooltip content="看不清？换一张">
                <el-button
                  class="captcha-refresh"
                  circle
                  :icon="RefreshLeft"
                  :loading="captchaLoading"
                  @click="refreshCaptcha"
                />
              </el-tooltip>
            </div>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            :disabled="loading"
            @click="onSubmit"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>

        <div class="login-footer">
          <span>还没有账号？</span>
          <el-link type="primary" class="register-link" @click="router.push('/register')">立即注册</el-link>
        </div>

        <div class="demo-tip">
          <div class="demo-info">
            <el-icon><InfoFilled /></el-icon>
            <span>演示账号：13800138000 / 123456</span>
          </div>
          <el-button link type="primary" size="small" @click="fillDemo">
            <el-icon><EditPen /></el-icon> 一键填充
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #b9554e 0%, #7e3a35 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-bg {
  width: 100%;
  max-width: 440px;
}

.login-card {
  padding: 44px 36px 32px;
  border-radius: var(--radius-lg);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-header h1 {
  margin: 8px 0 4px;
  font-size: 24px;
  color: var(--text-primary);
  letter-spacing: 1px;
}

.login-header p {
  color: var(--text-sub);
  font-size: 13px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.captcha-row .el-input {
  flex: 1;
  min-width: 0;
}

.captcha-img {
  height: 40px;
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid var(--border);
  transition: opacity 0.15s;
  background: #fff;
  image-rendering: crisp-edges;
}

.captcha-img:hover {
  opacity: 0.85;
}

.captcha-refresh {
  flex-shrink: 0;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
  color: var(--text-regular);
}

.register-link {
  text-decoration: none;
  transition: text-decoration 0.2s;
}

.register-link:hover {
  text-decoration: underline !important;
}

.demo-tip {
  margin-top: 20px;
  padding: 10px 14px;
  background: var(--brand-weak);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--text-regular);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.demo-info {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
