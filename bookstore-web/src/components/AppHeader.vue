<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()

const keyword = ref('')
const cartCount = computed(() => cartStore.totalCount)

onMounted(() => {
  if (userStore.isLogin) {
    cartStore.fetchCart().catch(() => {})
  }
})

function onSearch() {
  router.push({ path: '/books', query: { keyword: keyword.value } })
}

function goLogin() {
  localStorage.setItem('bookstore_web_redirect', route.fullPath)
  router.push('/login')
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    await userStore.logout()
    cartStore.reset()
    router.push('/')
  } catch (e) {
    /* 取消 */
  }
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="logo" @click="router.push('/')">
        <el-icon :size="28" color="#fff"><Reading /></el-icon>
        <span class="logo-text">线上书店</span>
      </div>

      <nav class="nav-menu">
        <router-link to="/" class="nav-item">首页</router-link>
        <router-link to="/books" class="nav-item">图书</router-link>
        <router-link to="/booklists" class="nav-item">书单</router-link>
        <router-link to="/search/advanced" class="nav-item">高级检索</router-link>
      </nav>

      <div class="search-box">
        <el-input
          v-model="keyword"
          placeholder="搜索书名或作者"
          clearable
          @keyup.enter="onSearch"
        >
          <template #append>
            <el-button @click="onSearch">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>

      <div class="header-right">
        <router-link to="/cart" class="cart-btn">
          <el-badge :value="cartCount" :hidden="cartCount === 0" :max="99">
            <el-icon :size="22"><ShoppingCart /></el-icon>
          </el-badge>
          <span>购物车</span>
        </router-link>

        <template v-if="userStore.isLogin">
          <el-dropdown>
            <span class="user-info">
              <el-icon :size="18"><User /></el-icon>
              {{ userStore.nickname }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/account')">个人中心</el-dropdown-item>
                <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
                <el-dropdown-item @click="router.push('/addresses')">收货地址</el-dropdown-item>
                <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button link type="primary" @click="goLogin">登录</el-button>
          <el-button link type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: var(--header-height);
  background: linear-gradient(135deg, var(--brand) 0%, var(--brand-dark) 100%);
  color: #fff;
  box-shadow: 0 2px 12px rgba(185, 85, 78, 0.25);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  max-width: var(--content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 20px;
  font-weight: 700;
}

.logo-text {
  letter-spacing: 2px;
}

.nav-menu {
  display: flex;
  gap: 8px;
}

.nav-item {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  color: rgba(255, 255, 255, 0.85);
  transition: all 0.2s;
}

.nav-item:hover,
.nav-item.router-link-active {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.search-box {
  flex: 1;
  max-width: 420px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.cart-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #fff;
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #fff;
}
</style>
