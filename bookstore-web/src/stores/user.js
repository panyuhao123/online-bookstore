import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, logout as logoutApi, getUserInfo, register as registerApi } from '@/api/user'
import { getToken, setToken, clearToken, getUser, setUser } from '@/utils/storage'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const userInfo = ref(getUser())

  const isLogin = computed(() => !!token.value)
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.phone || '未登录')

  async function login(form) {
    const res = await loginApi(form)
    token.value = res.token
    userInfo.value = res.user
    setToken(res.token)
    setUser(res.user)
    return res
  }

  async function register(form) {
    const res = await registerApi(form)
    // 注册成功后自动登录
    const loginRes = await loginApi({ phone: form.phone, password: form.password })
    token.value = loginRes.token
    userInfo.value = loginRes.user
    setToken(loginRes.token)
    setUser(loginRes.user)
    return res
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    const res = await getUserInfo()
    userInfo.value = res
    setUser(res)
    return res
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 退出接口失败不影响本地清令牌
    }
    token.value = ''
    userInfo.value = null
    clearToken()
  }

  return { token, userInfo, isLogin, nickname, login, register, fetchUserInfo, logout }
})
