<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import AppFooter from '@/components/AppFooter.vue'

const route = useRoute()
// 首页也显示面包屑（只有 1 项也展示）
const showBreadcrumb = computed(() => Array.isArray(route.meta?.breadcrumb) && route.meta.breadcrumb.length >= 1)
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
