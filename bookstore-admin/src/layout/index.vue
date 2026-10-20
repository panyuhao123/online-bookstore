<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '232px'" class="layout-aside">
      <div class="logo">
        <span class="logo-mark"><el-icon><Reading /></el-icon></span>
        <span v-show="!collapsed" class="logo-text">
          线上书店
          <em>运营后台</em>
        </span>
      </div>

      <el-menu
        router
        :default-active="activePath"
        :collapse="collapsed"
        :collapse-transition="false"
        class="layout-menu"
      >
        <template v-for="group in MENU_GROUPS" :key="group.title">
          <li v-show="!collapsed" class="menu-group">{{ group.title }}</li>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              <span class="menu-label">{{ item.title }}</span>
              <span v-if="badgeOf(item.path) > 0" class="menu-badge">{{ badgeOf(item.path) }}</span>
            </template>
          </el-menu-item>
        </template>
      </el-menu>

      <div v-show="!collapsed" class="aside-foot">
        <span class="dot" :class="hostOk ? 'dot--ok' : 'dot--gray'"></span>
        <span class="aside-foot__text" :title="backendHost">{{ backendHost }}</span>
      </div>
    </el-aside>

    <el-container class="layout-body">
      <el-header class="layout-header" height="56px">
        <div class="layout-header-left">
          <button class="collapse-btn" type="button" :title="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
            <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </button>
          <span class="crumb">{{ groupTitle }}</span>
          <span class="crumb-sep">/</span>
          <span class="crumb crumb--now">{{ pageTitle }}</span>
        </div>

        <div class="layout-header-right">
          <el-input
            v-model="searchKeyword"
            class="global-search"
            placeholder="订单号 / 手机号 / 书名"
            clearable
            @keyup.enter="handleGlobalSearch"
          >
            <template #prepend>
              <el-select v-model="searchScope" class="global-scope">
                <el-option label="订单" value="order" />
                <el-option label="图书" value="book" />
              </el-select>
            </template>
          </el-input>

          <router-link
            v-if="pendingShip > 0"
            class="header-chip"
            to="/order?status=2"
            :title="'已支付但未发货的订单共 ' + pendingShip + ' 单'"
          >
            <span class="header-chip__dot"></span>
            待发货 <b class="num">{{ pendingShip }}</b>
          </router-link>

          <div class="account">
            <span class="avatar">{{ adminInitial }}</span>
            <span class="account-name">{{ adminName }}</span>
          </div>
          <el-button link type="primary" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdmin, clearToken, getBaseUrl } from '@/utils/auth'
import { logout, overview } from '@/api/admin'

/**
 * 菜单按「运营者的一天怎么走」分组，而不是按数据表分组：
 * 概览 → 运营（要处理的） → 商品（要维护的） → 用户与系统（低频的）。
 * 分组标题在折叠态下隐藏，但菜单项文案不变 —— 端到端脚本按文案断言菜单。
 */
const MENU_GROUPS = [
  {
    title: '概览',
    items: [{ path: '/dashboard', title: '首页总览', icon: 'DataLine' }]
  },
  {
    title: '运营',
    items: [
      { path: '/order', title: '订单管理', icon: 'Tickets' },
      { path: '/review', title: '评价管理', icon: 'ChatDotRound' },
      { path: '/stock-notice', title: '到货提醒', icon: 'Bell' }
    ]
  },
  {
    title: '商品',
    items: [
      { path: '/book', title: '图书管理', icon: 'Reading' },
      { path: '/category', title: '分类管理', icon: 'Menu' },
      { path: '/banner', title: '轮播图管理', icon: 'Picture' },
      { path: '/booklist', title: '书单管理', icon: 'Collection' }
    ]
  },
  {
    title: '用户与系统',
    items: [
      { path: '/user', title: '用户管理', icon: 'User' },
      { path: '/log', title: '操作日志', icon: 'Document' },
      { path: '/setting', title: '系统设置', icon: 'Setting' }
    ]
  }
]

const route = useRoute()
const router = useRouter()

const admin = getAdmin()
const collapsed = ref(false)
const pendingShip = ref(0)
const hostOk = ref(true)

const activePath = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
const pageTitle = computed(() => (route.meta && route.meta.title) || '线上书店后台')
const backendHost = computed(() => getBaseUrl())

const adminName = computed(() => (admin ? admin.realName || admin.username : '未登录') || '未登录')
const adminInitial = computed(() => adminName.value.slice(0, 1).toUpperCase())

/**
 * 顶栏全局搜索：不新增后端接口，而是把关键词带进目标列表页的 query。
 *
 * <p>为什么不做一个「聚合搜索页」：后台真正需要的是「找到这条记录并处理它」，
 * 结果落到可以点发货 / 点上下架的列表页才有用；给一个只读的搜索结果页反而多一次跳转。
 */
const searchKeyword = ref('')
const searchScope = ref('order')

function handleGlobalSearch() {
  const keyword = String(searchKeyword.value || '').trim()
  if (!keyword) {
    ElMessage.warning('请输入订单号、手机号或书名')
    return
  }
  const path = searchScope.value === 'book' ? '/book' : '/order'
  // 用 replace 而不是 push：搜索是「换条件」不是「进新页面」，
  // 不堆历史记录，浏览器后退才能回到真正上一页
  router.replace({ path, query: { keyword } })
}


const groupTitle = computed(() => {
  const hit = MENU_GROUPS.find((g) => g.items.some((i) => i.path === activePath.value))
  return hit ? hit.title : '概览'
})

/** 侧栏角标只挂在「点进去就能处理」的入口上，避免每个菜单都长数字 */
function badgeOf(path) {
  if (path === '/order') return pendingShip.value
  return 0
}

onMounted(async () => {
  try {
    const res = await overview()
    pendingShip.value = Number((res && res.pendingShipCount) || 0)
    hostOk.value = true
  } catch (e) {
    // 概览拉不到不影响导航：角标退化为不显示，底部状态点转灰
    pendingShip.value = 0
    hostOk.value = false
  }
})

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  })
    .then(async () => {
      // 先在服务端吊销令牌（让这枚令牌立即失效），再清本地登录态。
      // 接口本身幂等，失败也不该拦住退出动作 —— 清本地令牌至少能保证本机登出，
      // 因此这里 catch 掉异常，与 APP 端「silent 退出」的处理方式保持一致。
      try {
        await logout()
      } catch (e) {
        // 忽略：网络异常/令牌已失效时仍继续清理本地登录态
      }
      clearToken()
      ElMessage.success('已退出登录')
      router.replace('/login')
    })
    .catch(() => {})
}
</script>

<style scoped>
.layout {
  height: 100%;
  background: var(--bg);
}

/* ------------------------------ 侧栏 ------------------------------ */
.layout-aside {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--line);
  transition: width 0.18s ease;
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  flex: none;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 9px;
  color: #fff;
  font-size: 17px;
  background: linear-gradient(135deg, var(--brand), var(--brand-active));
  box-shadow: 0 4px 10px -4px rgba(108, 77, 242, 0.55);
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-1);
  white-space: nowrap;
}

.logo-text em {
  font-style: normal;
  font-size: 11px;
  font-weight: 500;
  color: var(--ink-3);
  letter-spacing: 0.06em;
}

.layout-menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-right: none;
  padding: 6px 0 12px;
}

.layout-menu:not(.el-menu--collapse) {
  width: 100%;
}

.menu-group {
  list-style: none;
  padding: 14px 20px 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--ink-4);
}

.layout-menu :deep(.el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin: 2px 8px;
  padding-left: 14px !important;
  border-radius: var(--r-btn);
  color: var(--ink-2);
  font-size: 13px;
}

.layout-menu :deep(.el-menu-item .el-icon) {
  font-size: 16px;
  color: var(--ink-3);
  margin-right: 8px;
}

.layout-menu :deep(.el-menu-item:hover) {
  background: var(--gray-weak);
  color: var(--ink-1);
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: var(--brand-weak);
  color: var(--brand-active);
  font-weight: 600;
}

.layout-menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--brand);
}

.menu-label {
  flex: 1;
}

.menu-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* 折叠态：只剩图标，强制居中（否则 padding-left 会让图标偏右） */
.layout-menu.el-menu--collapse :deep(.el-menu-item) {
  padding: 0 !important;
  justify-content: center;
}

.layout-menu.el-menu--collapse :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.aside-foot {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 18px;
  border-top: 1px solid var(--line);
  font-size: 11px;
  color: var(--ink-4);
}

.aside-foot__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
}

.dot--ok {
  background: var(--ok);
}

.dot--gray {
  background: var(--ink-4);
}

/* ------------------------------ 顶栏 ------------------------------ */
.layout-body {
  min-width: 0;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid var(--line);
  padding: 0 20px 0 14px;
}

.layout-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.collapse-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-size: 16px;
}

.collapse-btn:hover {
  background: var(--gray-weak);
  color: var(--ink-1);
}

.crumb {
  font-size: 13px;
  color: var(--ink-3);
  white-space: nowrap;
}

.crumb-sep {
  color: var(--ink-4);
  font-size: 12px;
}

.crumb--now {
  color: var(--ink-1);
  font-weight: 600;
}

.layout-header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 顶栏搜索：宽度克制在 250px，避免把「待发货」角标挤到换行 */
.global-search {
  width: 250px;
}

.global-search :deep(.el-input-group__prepend) {
  padding: 0;
  background: var(--gray-weak);
  box-shadow: none;
}

.global-scope {
  width: 76px;
}

.global-scope :deep(.el-select__wrapper) {
  box-shadow: none;
  background: transparent;
  font-size: 12px;
}

.header-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 11px;
  border-radius: var(--r-chip);
  background: var(--warn-weak);
  color: var(--warn-text);
  font-size: 12px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
}

.header-chip:hover {
  background: #fdecd2;
}

.header-chip b {
  font-weight: 700;
}

.header-chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warn);
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--brand-weak);
  color: var(--brand-active);
  font-size: 12px;
  font-weight: 700;
}

.account-name {
  font-size: 13px;
  color: var(--ink-2);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ------------------------------ 内容区 ------------------------------ */
.layout-main {
  padding: 18px 20px 28px;
  overflow-y: auto;
}
</style>
