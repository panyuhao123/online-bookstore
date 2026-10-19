<template>
  <el-container class="layout">
    <el-aside width="210px" class="layout-aside">
      <div class="logo">
        <el-icon><Reading /></el-icon>
        <span>线上书店后台</span>
      </div>
      <el-menu router :default-active="activePath" class="layout-menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>首页总览</span>
        </el-menu-item>
        <el-menu-item index="/book">
          <el-icon><Reading /></el-icon>
          <span>图书管理</span>
        </el-menu-item>
        <el-menu-item index="/category">
          <el-icon><Menu /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/order">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/review">
          <el-icon><ChatDotRound /></el-icon>
          <span>评价管理</span>
        </el-menu-item>
        <el-menu-item index="/user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/log">
          <el-icon><Document /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
        <el-menu-item index="/setting">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header" height="56px">
        <div class="layout-header-left">
          <span class="app-title">{{ pageTitle }}</span>
          <span class="app-muted">{{ backendHost }}</span>
        </div>
        <div class="layout-header-right">
          <el-icon><UserFilled /></el-icon>
          <span class="admin-name">{{ admin ? admin.realName || admin.username : '未登录' }}</span>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdmin, clearToken, getBaseUrl } from '@/utils/auth'

const route = useRoute()
const router = useRouter()

const admin = getAdmin()

const activePath = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
const pageTitle = computed(() => (route.meta && route.meta.title) || '线上书店后台')
const backendHost = computed(() => '后端：' + getBaseUrl())

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  })
    .then(() => {
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
}

.layout-aside {
  background: #fff;
  border-right: 1px solid #e6e6e6;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 56px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
}

.layout-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.layout-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.layout-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
}

.admin-name {
  margin-right: 8px;
}

.layout-main {
  padding: 16px;
  overflow-y: auto;
}
</style>
