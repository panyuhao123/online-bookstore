<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-title">
        <span class="login-mark"><el-icon><Reading /></el-icon></span>
        <span>线上书店管理后台</span>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入管理员账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <el-divider>后端服务</el-divider>
      <el-input v-model="baseUrl" size="small" placeholder="如 http://192.168.1.5:8080">
        <template #append>
          <el-button :loading="testing" @click="handleTest">测试</el-button>
        </template>
      </el-input>
      <p class="app-muted login-tip">
        账号密码错误或后端未启动都会在此提示；换电脑/换网络联调时先改这里的地址。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, getInfo } from '@/api/admin'
import { setToken, setAdmin, getBaseUrl, setBaseUrl } from '@/utils/auth'
import request, { setRequestBaseUrl } from '@/api/request'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const testing = ref(false)
const baseUrl = ref(getBaseUrl())

const form = reactive({
  username: 'admin',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 测试后端连通性：用分类接口（免登录且会查库），能返回即说明后端与数据库都正常 */
async function handleTest() {
  testing.value = true
  setRequestBaseUrl(baseUrl.value)
  try {
    await request.get('/api/category/list')
    setBaseUrl(baseUrl.value)
    ElMessage.success('连接成功，已保存该地址')
  } catch (e) {
    ElMessage.error('连接失败，请检查后端是否已启动、地址是否正确')
  } finally {
    testing.value = false
  }
}

function handleLogin() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      setRequestBaseUrl(baseUrl.value)
      const data = await login({ username: form.username, password: form.password })
      setToken(data.token)
      setBaseUrl(baseUrl.value)
      // 同时拉取管理员信息，顶栏要显示真实姓名
      // 注意：/info 的 data 就是 AdminVO 本身，而登录接口的 data 才是 { token, admin } 结构
      const info = await getInfo()
      setAdmin(info.admin || info)
      ElMessage.success('登录成功')
      router.replace('/dashboard')
    } catch (e) {
      // 错误提示统一由请求拦截器弹出，这里无需重复处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  /* 与侧栏同源的紫，避免登录页是蓝、进去是紫的两套语言 */
  background:
    radial-gradient(1100px 520px at 18% -8%, rgba(255, 255, 255, 0.16), transparent 62%),
    linear-gradient(135deg, #7e63f5 0%, #5236c4 52%, #33207e 100%);
}

.login-box {
  width: 380px;
  padding: 32px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 60px -24px rgba(24, 16, 60, 0.55);
}

.login-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 24px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: var(--ink-1);
}

.login-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: #fff;
  font-size: 18px;
  background: linear-gradient(135deg, var(--brand), var(--brand-active));
  box-shadow: 0 6px 14px -6px rgba(108, 77, 242, 0.7);
}

.login-btn {
  width: 100%;
}

.login-tip {
  margin: 10px 0 0;
  line-height: 1.6;
}
</style>
