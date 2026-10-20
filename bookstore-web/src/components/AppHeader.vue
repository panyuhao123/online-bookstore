<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { useCompareStore } from '@/stores/compare'
import { pageBooks, getHotBooks } from '@/api/book'
import { getCategories } from '@/api/category'
import { saveRedirect } from '@/utils/storage'
import { formatPrice } from '@/utils/format'
import { ElMessageBox } from 'element-plus'

/**
 * 全站顶栏（当当风格三段式）。
 *
 * <p>结构：顶部工具条（登录态与快捷入口）→ 白底主 header（logo + 搜索 + 购物车）
 * → 橙色导航条（分类入口 + 主菜单）。三段各有明确分工：
 * 工具条放"我的"类低频入口，主 header 只放"找书"与"结算"两个高频动作，
 * 导航条负责横向分区。三段合一比原来那条"渐变红通栏塞五个胶囊"更符合电商的信息层级。
 */

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()
const compareStore = useCompareStore()

const keyword = ref('')
/** 搜索框左侧的分类下拉：0 表示不限分类（走纯关键词检索） */
const searchCat = ref(0)
const categories = ref([])
const hotWords = ref([])

const cartCount = computed(() => cartStore.totalCount)
// 对比栏此前只能靠右下角浮层发现，进不了对比页；这里给一个常驻入口
const compareCount = computed(() => compareStore.count)

onMounted(async () => {
  if (userStore.isLogin) {
    cartStore.fetchCart().catch(() => {})
  }
  // 分类下拉与热搜词都是"锦上添花"，失败就留空，不能连累搜索本身
  try {
    categories.value = (await getCategories()) || []
  } catch (e) {
    categories.value = []
  }
  try {
    const hot = (await getHotBooks(5)) || []
    // 热搜词取自真实书名：点了必然有结果，比硬编码词表稳
    hotWords.value = hot.map((b) => b.title).filter(Boolean)
  } catch (e) {
    hotWords.value = []
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
  const query = {}
  if (kw) query.keyword = kw
  if (searchCat.value) query.categoryId = searchCat.value
  router.push({ path: '/books', query })
}

function searchWord(word) {
  keyword.value = word
  onSearch()
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
    <!-- ===== 一、顶部工具条：登录态 + 我的入口（低频动作） ===== -->
    <div class="topbar">
      <div class="container topbar-inner">
        <div class="topbar-left">
          <span class="topbar-welcome">欢迎来到线上书店</span>
          <template v-if="!userStore.isLogin">
            <el-button link class="topbar-link" @click="goLogin">登录</el-button>
            <span class="sep">|</span>
            <el-button link class="topbar-link" @click="router.push('/register')">免费注册</el-button>
          </template>
          <span class="sep">|</span>
          <a class="topbar-link" @click="router.push('/orders')">我的订单</a>
          <span class="sep">|</span>
          <a class="topbar-link" @click="router.push('/booklists')">我的书单</a>
          <span class="sep">|</span>
          <a class="topbar-link" @click="router.push('/account/history')">浏览历史</a>
          <span class="sep">|</span>
          <a class="topbar-link" @click="router.push('/addresses')">收货地址</a>
        </div>

        <!-- 既有类名 header-right 被端到端脚本依赖（`.header-right .user-info` /
             `.header-right button` 的「登录」），重构版式时不要改名 -->
        <div class="topbar-right header-right">
          <a class="topbar-link" @click="router.push('/compare')">
            图书对比<span v-if="compareCount" class="num">({{ compareCount }})</span>
          </a>
          <span class="sep">|</span>
          <a class="topbar-link" @click="router.push('/search/advanced')">高级检索</a>
          <span class="sep">|</span>
          <a class="topbar-link" @click="router.push('/catalog')">全部图书</a>
          <span class="sep">|</span>

          <template v-if="userStore.isLogin">
            <el-dropdown>
              <span class="user-info">
                <el-icon :size="13"><User /></el-icon>
                {{ userStore.nickname }}
                <el-icon :size="12"><ArrowDown /></el-icon>
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
          <span v-else class="topbar-hint">登录后享更多优惠</span>
        </div>
      </div>
    </div>

    <!-- ===== 二、主 header：logo + 搜索 + 购物车（高频动作） ===== -->
    <div class="site-header">
      <div class="container site-header-inner">
        <div class="logo" @click="router.push('/')">
          <span class="logo-mark">
            <el-icon :size="20"><Reading /></el-icon>
          </span>
          <span class="logo-word">线上书店</span>
          <span class="logo-slogan">好书 好价 好服务</span>
        </div>

        <div class="search-area">
          <!-- 分类下拉刻意放在 .search-box 之外：端到端脚本用 `.search-box input`
               取搜索框，里面多一个 input 会取到下拉的输入框，联想断言就会失效 -->
          <div class="search-wrap">
            <!-- 用原生 select 而不是 el-select：这一格只有 96px，
                 组件库的 padding + 箭头会把「全部图书」压成「全部图…」 -->
            <select v-model="searchCat" class="search-cat">
              <option :value="0">全部图书</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>

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
            </div>

            <button class="search-btn" type="button" @click="onSearch">
              <el-icon :size="16"><Search /></el-icon>
              <span>搜索</span>
            </button>
          </div>

          <div class="hot-words" v-if="hotWords.length">
            <span class="hot-label">热搜：</span>
            <a v-for="w in hotWords" :key="w" class="hot-word" :title="w" @click="searchWord(w)">
              {{ w.length > 8 ? w.slice(0, 8) : w }}
            </a>
          </div>
        </div>

        <div class="cart-area">
          <div class="cart-btn" @click="router.push('/cart')">
            <el-icon :size="18"><ShoppingCart /></el-icon>
            <span class="cart-text">购物车</span>
            <span class="cart-count">{{ cartCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 三、橙色导航条：分区入口 ===== -->
    <nav class="nav-bar">
      <div class="container nav-inner">
        <div class="nav-all" @click="router.push('/catalog')">
          <el-icon :size="14"><Menu /></el-icon>
          <span>全部商品分类</span>
        </div>
        <div class="nav-menu">
          <!-- 高亮用 route.path 显式判断：router-link 自带的 active 类是前缀匹配，
               进 /books 时「首页」也会亮；exact-active 在嵌套路由下又不可靠 -->
          <router-link to="/" class="nav-item" :class="{ active: route.path === '/' }">首页</router-link>
          <router-link
            to="/books"
            class="nav-item"
            :class="{ active: route.path === '/books' || route.path.startsWith('/books/') }"
          >图书</router-link>
          <router-link
            to="/catalog"
            class="nav-item"
            :class="{ active: route.path === '/catalog' }"
          >商品目录</router-link>
          <router-link
            to="/booklists"
            class="nav-item"
            :class="{ active: route.path.startsWith('/booklists') }"
          >书单广场</router-link>
          <router-link
            to="/compare"
            class="nav-item"
            :class="{ active: route.path === '/compare' }"
          >图书对比</router-link>
          <router-link
            to="/search/advanced"
            class="nav-item"
            :class="{ active: route.path === '/search/advanced' }"
          >高级检索</router-link>
        </div>
        <div class="nav-promo" @click="router.push({ path: '/books', query: { sort: 'price_asc' } })">
          限时特惠
        </div>
      </div>
    </nav>
  </header>
</template>

<style scoped>
/* sticky 用负偏移：滚动时工具条被推出视口，主 header 与导航条持续吸顶。
   若把 sticky 加在内部 div 上，父容器只有 header 那么高，滚过就失效了。 */
.app-header {
  position: sticky;
  top: -32px;
  z-index: 200;
}

/* ===== 工具条 ===== */
.topbar {
  height: 32px;
  background: var(--bg-fill);
  border-bottom: 1px solid var(--border);
  color: var(--text-regular);
  font-size: 12px;
}

.topbar-inner {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-welcome {
  color: var(--text-sub);
}

.sep {
  color: #dddddd;
}

.topbar-link {
  color: var(--text-regular);
  cursor: pointer;
  white-space: nowrap;
}

.topbar-link:hover {
  color: var(--brand);
}

/* el-button link 默认带内边距，会把 32px 的工具条撑高 */
.topbar-link.el-button {
  height: auto;
  padding: 0;
  font-size: 12px;
  color: var(--text-regular);
}

.topbar-link.el-button:hover {
  color: var(--brand);
}

.num {
  color: var(--brand);
}

.topbar-hint {
  color: var(--text-sub);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  color: var(--text-regular);
  outline: none;
}

.user-info:hover {
  color: var(--brand);
}

/* ===== 主 header ===== */
.site-header {
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.site-header-inner {
  height: 96px;
  display: flex;
  align-items: center;
  gap: 30px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
  width: 220px;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-word {
  font-size: 24px;
  font-weight: 700;
  color: var(--brand);
  letter-spacing: 1px;
}

.logo-slogan {
  display: none;
  font-size: 12px;
  color: var(--text-sub);
}

/* 搜索区：三个零件拼成一条，只有外侧有圆角 */
.search-area {
  flex: 1;
  min-width: 0;
  padding-top: 6px;
}

.search-wrap {
  display: flex;
  height: 38px;
  border: 2px solid var(--orange);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: #fff;
}

.search-cat {
  width: 96px;
  flex-shrink: 0;
  height: 34px;
  border: none;
  border-right: 1px solid var(--border);
  background-color: var(--bg-fill);
  /* 原生箭头在 Windows 上又大又丑，用背景图自绘一个 */
  background-image: url("data:image/svg+xml;charset=utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 5 5-5' stroke='%23999' fill='none' stroke-width='1.5'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 7px center;
  appearance: none;
  -webkit-appearance: none;
  padding: 0 18px 0 8px;
  font-size: 12px;
  color: var(--text-regular);
  cursor: pointer;
  outline: none;
}

.search-cat:hover {
  color: var(--brand);
}

.search-box {
  flex: 1;
  min-width: 0;
  display: flex;
}

/* 输入框自身不要边框，撑满整条搜索条的高度 */
.search-box :deep(.el-input__wrapper) {
  height: 34px;
  box-shadow: none !important;
  border-radius: 0;
  padding: 0 10px;
}

.search-box :deep(.el-input__inner) {
  font-size: 13px;
}

.search-btn {
  width: 92px;
  flex-shrink: 0;
  border: none;
  background: var(--orange);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.search-btn:hover {
  background: var(--orange-dark);
}

.hot-words {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 26px;
  font-size: 12px;
  color: var(--text-sub);
  overflow: hidden;
  white-space: nowrap;
}

.hot-label {
  color: var(--text-sub);
  flex-shrink: 0;
}

.hot-word {
  cursor: pointer;
  color: var(--text-regular);
}

.hot-word:hover {
  color: var(--brand);
}

/* ===== 购物车按钮 ===== */
.cart-area {
  flex-shrink: 0;
}

.cart-btn {
  width: 168px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: linear-gradient(90deg, var(--orange) 0%, var(--orange-light) 100%);
  color: #fff;
  font-size: 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  position: relative;
}

.cart-btn:hover {
  filter: brightness(1.05);
}

.cart-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 600;
}

/* ===== 橙色导航条 ===== */
.nav-bar {
  height: 36px;
  background: var(--orange);
}

.nav-inner {
  height: 36px;
  display: flex;
  align-items: center;
}

.nav-all {
  width: 220px;
  height: 36px;
  flex-shrink: 0;
  background: var(--brand-dark);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
}

.nav-all:hover {
  background: #c9111f;
}

.nav-menu {
  display: flex;
  align-items: center;
  height: 100%;
  flex: 1;
}

.nav-item {
  height: 100%;
  padding: 0 18px;
  display: flex;
  align-items: center;
  color: #fff;
  font-size: 14px;
}

/* 高亮由模板里的 :class="{ active }" 显式控制，不再依赖 router-link 的 active 类 */
.nav-item:hover,
.nav-item.active {
  background: var(--orange-light);
  color: #fff;
}

.nav-promo {
  height: 26px;
  padding: 0 14px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  cursor: pointer;
}

/* ===== 响应式 ===== */
@media (max-width: 1199px) {
  .logo {
    width: auto;
  }
  .logo-slogan {
    display: none;
  }
}

@media (max-width: 991px) {
  .site-header-inner {
    gap: 16px;
  }
  .hot-words {
    display: none;
  }
  .cart-btn {
    width: 120px;
  }
  .cart-text {
    display: none;
  }
  .nav-all {
    display: none;
  }
}

@media (max-width: 767px) {
  .topbar-left .topbar-link:nth-of-type(n + 4) {
    display: none;
  }
  .logo-word {
    font-size: 18px;
  }
  .nav-menu {
    overflow-x: auto;
  }
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
