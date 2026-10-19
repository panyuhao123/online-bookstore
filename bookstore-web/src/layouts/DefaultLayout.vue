<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import AppFooter from '@/components/AppFooter.vue'
import { useUserStore } from '@/stores/user'
import { useHistoryStore } from '@/stores/history'
import { useFreightStore } from '@/stores/freight'

const route = useRoute()
// 首页也显示面包屑（只有 1 项也展示）
const showBreadcrumb = computed(() => Array.isArray(route.meta?.breadcrumb) && route.meta.breadcrumb.length >= 1)

/**
 * 登录态变化时同步浏览历史。
 *
 * <p>放在布局层而不是浏览历史页：用户登录后大概率先去逛书，而不是先打开历史页；
 * 等他真去看历史时，"登录前看过的书"应该已经在列表里了。
 * 退出登录则把服务端数据从内存清掉 —— 同一台电脑换人登录时不该看到上一个人的记录。
 */
const userStore = useUserStore()
const historyStore = useHistoryStore()
const freightStore = useFreightStore()

/**
 * 运费规则在这里统一拉一次。
 *
 * <p>不放在 BookCard 里：一屏就有二三十张卡片，逐张去请求规则毫无意义；
 * 也不做 localStorage 持久化 —— 后台把包邮门槛改了之后，用户拿到的必须是新规则。
 * 只在本会话内存里存一份，进一次页面拉一次。
 */
freightStore.ensure().catch(() => {})

watch(
  () => userStore.isLogin,
  (isLogin) => {
    if (isLogin) historyStore.syncLocal()
    else historyStore.reset()
  },
  { immediate: true }
)
</script>

<template>
  <div class="layout">
    <AppHeader />
    <div class="layout-body">
      <AppSidebar />
      <main class="layout-main">
        <!-- 面包屑 -->
        <div v-if="showBreadcrumb" class="breadcrumb-bar">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(item, idx) in route.meta.breadcrumb"
              :key="idx"
              :to="idx === 0 ? '/' : undefined"
            >
              {{ item }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
    <AppFooter />
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-body {
  flex: 1;
  display: flex;
  width: 100%;
}

.layout-main {
  flex: 1;
  min-width: 0;
  background: var(--bg-page);
}

.breadcrumb-bar {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 16px 20px 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
