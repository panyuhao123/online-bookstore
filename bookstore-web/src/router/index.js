import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { saveRedirect } from '@/utils/storage'

/**
 * 路由表。
 *
 * - 使用 hash 模式：静态部署刷新不 404；
 * - meta.requiresAuth 控制登录权限；
 * - meta.title 用于面包屑与 document.title；
 * - meta.breadcrumb 自定义面包屑层级（可选）。
 */
const routes = [
  // ======== 兼容旧路径的 redirect（历史版本可能用了 /user/address、/address 等）========
  { path: '/user/address', redirect: '/addresses' },
  { path: '/address', redirect: '/addresses' },
  { path: '/user/addresses', redirect: '/addresses' },

  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false, layout: 'blank' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: '注册', requiresAuth: false, layout: 'blank' }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页', breadcrumb: ['首页'] }
      },
      {
        path: 'books',
        name: 'BookList',
        component: () => import('@/views/book/list.vue'),
        meta: { title: '图书列表', breadcrumb: ['首页', '图书列表'] }
      },
      {
        path: 'books/:id',
        name: 'BookDetail',
        component: () => import('@/views/book/detail.vue'),
        meta: { title: '图书详情', breadcrumb: ['首页', '图书详情'] }
      },
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/cart/index.vue'),
        meta: { title: '购物车', requiresAuth: true, breadcrumb: ['首页', '购物车'] }
      },
      {
        path: 'orders/confirm',
        name: 'OrderConfirm',
        component: () => import('@/views/order/confirm.vue'),
        meta: { title: '确认订单', requiresAuth: true, breadcrumb: ['首页', '确认订单'] }
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/order/list.vue'),
        meta: { title: '我的订单', requiresAuth: true, breadcrumb: ['首页', '我的订单'] }
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('@/views/order/detail.vue'),
        meta: { title: '订单详情', requiresAuth: true, breadcrumb: ['首页', '我的订单', '订单详情'] }
      },
      {
        path: 'addresses',
        name: 'AddressList',
        component: () => import('@/views/address/index.vue'),
        meta: { title: '收货地址', requiresAuth: true, breadcrumb: ['首页', '收货地址'] }
      },
      {
        path: 'booklists',
        name: 'BooklistList',
        component: () => import('@/views/booklist/list.vue'),
        meta: { title: '书单', breadcrumb: ['首页', '书单'] }
      },
      {
        path: 'booklists/new',
        name: 'BooklistNew',
        component: () => import('@/views/booklist/edit.vue'),
        meta: { title: '新建书单', requiresAuth: true, breadcrumb: ['首页', '书单', '新建书单'] }
      },
      {
        path: 'booklists/:id',
        name: 'BooklistDetail',
        component: () => import('@/views/booklist/detail.vue'),
        meta: { title: '书单详情', breadcrumb: ['首页', '书单', '书单详情'] }
      },
      {
        path: 'booklists/:id/edit',
        name: 'BooklistEdit',
        component: () => import('@/views/booklist/edit.vue'),
        meta: { title: '编辑书单', requiresAuth: true, breadcrumb: ['首页', '书单', '编辑书单'] }
      },
      {
        path: 'compare',
        name: 'Compare',
        component: () => import('@/views/compare/index.vue'),
        meta: { title: '图书对比', breadcrumb: ['首页', '图书对比'] }
      },
      {
        path: 'search/advanced',
        name: 'AdvancedSearch',
        component: () => import('@/views/search/advanced.vue'),
        meta: { title: '高级检索', breadcrumb: ['首页', '高级检索'] }
      },
      {
        path: 'account',
        name: 'Account',
        component: () => import('@/views/account/index.vue'),
        meta: { title: '个人中心', requiresAuth: true, breadcrumb: ['首页', '个人中心'] }
      },
      {
        path: 'account/history',
        name: 'History',
        component: () => import('@/views/account/history.vue'),
        meta: { title: '浏览历史', requiresAuth: true, breadcrumb: ['首页', '个人中心', '浏览历史'] }
      },
      {
        path: 'account/reviews',
        name: 'MyReviews',
        component: () => import('@/views/account/reviews.vue'),
        meta: { title: '我的评价', requiresAuth: true, breadcrumb: ['首页', '个人中心', '我的评价'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + '线上书店'
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.isLogin) {
    // 记录来源页，登录后回跳
    saveRedirect(to.fullPath)
    ElMessage.warning('请先登录')
    next('/login')
    return
  }
  if ((to.path === '/login' || to.path === '/register') && userStore.isLogin) {
    next('/')
    return
  }
  next()
})

export default router
