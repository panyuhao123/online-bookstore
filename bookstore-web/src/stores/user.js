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

  /**
   * 注册。
   *
   * <p>注册接口只返回 UserVO、不签发令牌；而登录接口的 captchaKey / captchaCode 是必填，
   * 注册页没有图形验证码，之前在这里「注册成功后顺手调一次 login」必然 422 失败 ——
   * 用户看到的是「注册成功，已自动登录」这句提示压根没执行。改为注册成功后引导去登录页。
   */
  async function register(form) {
    return registerApi(form)
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
