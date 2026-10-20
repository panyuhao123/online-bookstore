import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layout/index.vue'
import { getToken } from '@/utils/auth'

/**
 * 路由表。
 *
 * 使用 hash 模式的原因：后台最终是静态部署（vite build 后直接开 nginx 或本地预览），
 * hash 模式不需要服务端额外配置 URL 转发，刷新也不会 404。
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页总览', icon: 'DataLine' }
      },
      {
        path: 'book',
        name: 'Book',
        component: () => import('@/views/book/index.vue'),
        meta: { title: '图书管理', icon: 'Reading' }
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/category/index.vue'),
        meta: { title: '分类管理', icon: 'Menu' }
      },
      {
        path: 'banner',
        name: 'Banner',
        component: () => import('@/views/banner/index.vue'),
        meta: { title: '轮播图管理', icon: 'Picture' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理', icon: 'Tickets' }
      },
      {
        path: 'review',
        name: 'Review',
        component: () => import('@/views/review/index.vue'),
        meta: { title: '评价管理', icon: 'ChatDotRound' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'booklist',
        name: 'Booklist',
        component: () => import('@/views/booklist/index.vue'),
        meta: { title: '书单管理', icon: 'Collection' }
      },
      {
        path: 'stock-notice',
        name: 'StockNotice',
        component: () => import('@/views/stock-notice/index.vue'),
        meta: { title: '到货提醒', icon: 'Bell' }
      },
      {
        path: 'log',
        name: 'Log',
        component: () => import('@/views/log/index.vue'),
        meta: { title: '操作日志', icon: 'Document' }
      },
      {
        path: 'setting',
        name: 'Setting',
        component: () => import('@/views/setting/index.vue'),
        meta: { title: '系统设置', icon: 'Setting' }
      }
    ]
  },
  // 兜底：未知路径回到首页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 全局守卫：未登录一律回登录页（令牌失效由接口拦截器负责二次兜底）
router.beforeEach((to) => {
  document.title = to.meta && to.meta.title ? to.meta.title + ' - 线上书店后台' : '线上书店后台'
  if (to.path !== '/login' && !getToken()) {
    return '/login'
  }
  if (to.path === '/login' && getToken()) {
    return '/dashboard'
  }
  return true
})

export default router
