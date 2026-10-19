<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { useCompareStore } from '@/stores/compare'
import { pageBooks } from '@/api/book'
import { saveRedirect } from '@/utils/storage'
import { formatPrice } from '@/utils/format'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()
const compareStore = useCompareStore()

const keyword = ref('')
const cartCount = computed(() => cartStore.totalCount)
// 对比栏此前只能靠右下角浮层发现，进不了对比页；这里给一个常驻入口
const compareCount = computed(() => compareStore.count)

onMounted(() => {
  if (userStore.isLogin) {
    cartStore.fetchCart().catch(() => {})
  }
})

/**
 * 搜索联想（N1）。
 *
 * <p>复用图书分页接口而不是新开一个联想接口：`/api/book/page` 本来就支持 `keyword`
 * 且命中的是书名或作者，正好是联想需要的东西，没必要为它多加一条链路。
 * 每次取 8 条 —— 再多下拉框就成列表页了，用户反而找不到该点哪个。
 *
 * <p>请求序号 `seq` 用来丢弃过期响应：快速敲键时会并发多个请求，
 * 后发的可能先回，不校验就会出现"输入到第 5 个字，下拉里还是第 2 个字的结果"。
 */
let seq = 0
async function querySearch(queryString, cb) {
  const kw = (queryString || '').trim()
  if (!kw) {
    cb([])
    return
  }
  const mine = ++seq
  try {
    const data = await pageBooks({ keyword: kw, pageNum: 1, pageSize: 8 })
    if (mine !== seq) return
    cb(data?.list || [])
  } catch (e) {
    if (mine === seq) cb([])
  }
}

function onSelect(item) {
  if (!item || !item.id) return
  router.push('/books/' + item.id)
}

function onSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/books', query: kw ? { keyword: kw } : {} })
}

function goLogin() {
  saveRedirect(route.fullPath)
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
        <router-link to="/catalog" class="nav-item">商品目录</router-link>
        <router-link to="/books" class="nav-item">图书</router-link>
        <router-link to="/booklists" class="nav-item">书单</router-link>
        <router-link to="/search/advanced" class="nav-item">高级检索</router-link>
      </nav>

      <div class="search-box">
        <el-autocomplete
          v-model="keyword"
          :fetch-suggestions="querySearch"
          :debounce="250"
          :trigger-on-focus="false"
          placeholder="搜索书名或作者"
          clearable
          class="search-input"
          popper-class="search-suggest-popper"
          @select="onSelect"
          @keyup.enter="onSearch"
        >
          <template #default="{ item }">
            <div class="sug-item">
              <span class="sug-title">{{ item.title }}</span>
              <span class="sug-author">{{ item.author }}</span>
              <span class="sug-price">{{ formatPrice(item.price) }}</span>
            </div>
          </template>
          <template #footer>
            <div class="sug-footer" @click="onSearch">查看全部结果</div>
          </template>
        </el-autocomplete>
        <el-button class="search-btn" @click="onSearch">
          <el-icon><Search /></el-icon>
        </el-button>
      </div>

      <div class="header-right">
        <router-link to="/compare" class="cart-btn" title="图书对比">
          <el-badge :value="compareCount" :hidden="compareCount === 0" :max="4">
            <el-icon :size="22"><DataAnalysis /></el-icon>
          </el-badge>
          <span>对比</span>
        </router-link>

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
                <el-dropdown-item @click="router.push('/account/history')">浏览历史</el-dropdown-item>
                <el-dropdown-item @click="router.push('/booklists')">我的书单</el-dropdown-item>
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
  display: flex;
}

.search-input {
  flex: 1;
}

/* 输入框右侧与按钮拼接，去掉各自内侧圆角 */
.search-box :deep(.el-input__wrapper) {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.search-btn {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  border-left: none;
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

<style>
/* 联想下拉由 el-autocomplete teleport 到 body，scoped 样式够不着，只能写在全局块里 */
.search-suggest-popper .sug-item {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.4;
}

.search-suggest-popper .sug-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary, #303133);
}

.search-suggest-popper .sug-author {
  flex: none;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-sub, #909399);
}

.search-suggest-popper .sug-price {
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand, #b9554e);
}

.search-suggest-popper .sug-footer {
  padding: 6px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--brand, #b9554e);
  cursor: pointer;
}

.search-suggest-popper .sug-footer:hover {
  background: var(--bg-fill, #f5f7fa);
}
</style>
