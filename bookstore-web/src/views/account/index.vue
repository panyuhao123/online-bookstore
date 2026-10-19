<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { pageOrders } from '@/api/order'
import { maskPhone, formatDateTime, formatPrice, orderStatusText, orderStatusTagType } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const recentOrders = ref([])

const menuItems = [
  { label: '我的订单', path: '/orders', icon: 'Tickets' },
  { label: '收货地址', path: '/addresses', icon: 'Location' },
  { label: '我的书单', path: '/booklists', icon: 'Collection' },
  { label: '浏览历史', path: '/account/history', icon: 'Clock' },
  { label: '我的评价', path: '/account/reviews', icon: 'ChatDotRound' }
]

const quickStats = computed(() => [
  { label: '待支付', count: 0, status: 1 },
  { label: '待收货', count: 0, status: 3 },
  { label: '已完成', count: 0, status: 4 }
])

async function loadRecentOrders() {
  try {
    const res = await pageOrders({ pageNum: 1, pageSize: 5 })
    recentOrders.value = res.list || []
  } catch (e) {
    /* 静默 */
  }
}

onMounted(() => {
  loadRecentOrders()
})
</script>

<template>
  <div class="page-container account-page">
    <div class="account-layout">
      <!-- 左侧菜单 -->
      <aside class="sidebar card">
        <div class="user-profile">
          <el-avatar :size="64" class="avatar">{{ userStore.nickname?.charAt(0) || 'U' }}</el-avatar>
          <div class="profile-info">
            <p class="nickname">{{ userStore.nickname }}</p>
            <p class="phone">{{ maskPhone(userStore.userInfo?.phone) }}</p>
          </div>
        </div>
        <ul class="menu-list">
          <li v-for="item in menuItems" :key="item.path" @click="router.push(item.path)">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </li>
        </ul>
      </aside>

      <!-- 右侧内容 -->
      <div class="content">
        <!-- 账号信息卡 -->
        <div class="info-card card">
          <h3>账号信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">昵称</span>
              <span class="value">{{ userStore.userInfo?.nickname || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">手机号</span>
              <span class="value">{{ userStore.userInfo?.phone || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">注册时间</span>
              <span class="value">{{ formatDateTime(userStore.userInfo?.createTime) }}</span>
            </div>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="quick-card card">
          <h3>快捷入口</h3>
          <div class="quick-grid">
            <div v-for="item in menuItems" :key="item.path" class="quick-item" @click="router.push(item.path)">
              <el-icon :size="28" color="var(--brand)"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>

        <!-- 最近订单 -->
        <div class="orders-card card">
          <div class="card-header">
            <h3>最近订单</h3>
            <el-link type="primary" @click="router.push('/orders')">查看全部 ›</el-link>
          </div>
          <el-table :data="recentOrders" style="width: 100%" v-if="recentOrders.length > 0">
            <el-table-column label="订单号" prop="orderNo" min-width="200" />
            <el-table-column label="件数" width="80" align="center">
              <template #default="{ row }">{{ row.totalQuantity || 0 }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="center">
              <template #default="{ row }">
                <span class="price">{{ formatPrice(row.totalAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="orderStatusTagType(row.status)" size="small">{{ orderStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下单时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="router.push('/orders/' + row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无订单" :image-size="80" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.account-layout {
  display: flex;
  gap: 16px;
}

.sidebar {
  width: 240px;
  flex-shrink: 0;
  padding: 20px;
  align-self: flex-start;
  position: sticky;
  top: calc(var(--header-height) + 16px);
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 16px;
}

.avatar {
  background: var(--brand);
  color: #fff;
  font-size: 24px;
}

.nickname {
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 4px;
}

.phone {
  font-size: 12px;
  color: var(--text-sub);
}

.menu-list {
  padding: 0;
}

.menu-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-regular);
  transition: all 0.15s;
  margin-bottom: 4px;
}

.menu-list li:hover {
  background: var(--brand-weak);
  color: var(--brand);
}

.menu-list li .arrow {
  margin-left: auto;
  font-size: 12px;
  opacity: 0.5;
}

.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card,
.quick-card,
.orders-card {
  padding: 20px 24px;
}

.info-card h3,
.quick-card h3 {
  font-size: 16px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: 13px;
  color: var(--text-sub);
}

.value {
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 500;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
}

.quick-item:hover {
  background: var(--brand-weak);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.card-header h3 {
  font-size: 16px;
}

.price {
  color: var(--brand);
  font-weight: 600;
}
</style>
