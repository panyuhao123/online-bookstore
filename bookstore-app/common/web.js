/**
 * Web（H5）端宽屏适配的公共定义。
 *
 * 为什么断点取 960：
 *   uni-app H5 的 rpx 换算存在 rpxCalcMaxDeviceWidth（默认 960）上限，
 *   视口 ≥ 960 时 rpx 固定按 375 基准折算（1rpx = 0.5px），不会再随屏宽放大；
 *   < 960 时 rpx 随视口线性缩放。所以 960 是「rpx 行为切换点」，
 *   桌面布局断点与它对齐，可以避免同一套样式在临界宽度附近跳变。
 *
 * 非 H5 平台（APP）恒为窄屏：window / matchMedia 相关代码用条件编译剥离。
 */
import { ref, computed } from 'vue'

/** 宽屏断点（px） */
export const WIDE_MIN = 960

/**
 * 桌面顶栏总高度（px）：顶部窄条 36 + 主导航条 76。
 * 与 components/web-header.vue 的 .wh-topbar / .wh-main 高度保持一致；
 * 页面侧不要硬编码这个数字，用 CSS 变量 `--web-header-h`（见 common/web.scss）。
 */
export const WEB_HEADER_H = 112

/** 移动端自绘导航行高（px）：88rpx 按 375 基准折算 */
export const NAV_ROW_H = 44

/**
 * 站点一级导航。
 * 项与底部 tabBar 的主入口一致（首页 / 分类 / 书单），
 * 自绘 tabBar 下 switchTab 不可用，跳转一律 redirectTo。
 */
export const SITE_NAV = [
  { key: 'home', label: '首页', route: 'pages/index/index' },
  { key: 'category', label: '分类', route: 'pages/category/category' },
  { key: 'read', label: '书单', route: 'pages/booklist/list' },
  { key: 'new', label: '新书上架', route: 'pages/book/new' },
  { key: 'cheap', label: '低价优选', route: 'pages/book/cheap' }
]

const wide = ref(false)

// #ifdef H5
if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
  const mq = window.matchMedia('(min-width: ' + WIDE_MIN + 'px)')
  wide.value = mq.matches
  const onChange = (e) => {
    wide.value = e.matches
  }
  if (mq.addEventListener) {
    mq.addEventListener('change', onChange)
  } else if (mq.addListener) {
    mq.addListener(onChange)
  }
}
// #endif

/** 当前是否宽屏（桌面）视图；模板里可直接用，ref 会自动解包 */
export const isWide = wide

/** 状态栏高度：APP 需要占位，H5 桌面为 0 */
const statusBarH = ref(0)
try {
  const sys = uni.getSystemInfoSync()
  statusBarH.value = sys.statusBarHeight || 0
} catch (e) {
  statusBarH.value = 0
}

/**
 * 顶部导航实际占用高度（px）：页面用它算占位，不要用 44 硬编码，
 * 否则桌面顶栏（64px）会盖住页面第一屏内容。
 */
export const navHeight = computed(() => (wide.value ? WEB_HEADER_H : statusBarH.value + NAV_ROW_H))

/* ---------------- 搜索关键词传递 ---------------- */

/**
 * 桌面顶栏搜索：直接跳转列表页并带 keyword query。
 *
 * 实测中文 keyword 经 encodeURIComponent 后通过 uni-app H5 路由可正常解码，
 * 只在 URL 中传递，支持刷新 / 分享。
 */
export function goSearch(keyword) {
  const kw = (keyword || '').trim()
  const url = '/pages/book/list' + (kw ? '?keyword=' + encodeURIComponent(kw) : '')
  const pages = getCurrentPages()
  const cur = pages && pages[pages.length - 1]
  // 已在列表页：redirectTo 重建实例触发 onLoad，避免页面栈无限增长
  if (cur && cur.route === 'pages/book/list') {
    uni.redirectTo({ url })
  } else {
    uni.navigateTo({ url })
  }
}

/** 当前页面 route，用于在顶栏高亮对应的一级导航 */
export function currentRoute() {
  const pages = getCurrentPages()
  const cur = pages && pages[pages.length - 1]
  return (cur && cur.route) || ''
}

/* ---------------- 浏览器标签标题 ---------------- */

const BASE_TITLE = '线上书店'

/**
 * route → 页面名。桌面浏览器会开多标签，标题全都是「线上书店」时分不清哪个是哪个；
 * APP 没有标签概念，所以这份映射只在 H5 用。
 */
const PAGE_TITLES = {
  'pages/index/index': '首页',
  'pages/category/category': '分类',
  'pages/booklist/list': '书单',
  'pages/booklist/detail': '书单详情',
  'pages/book/list': '图书列表',
  'pages/book/detail': '图书详情',
  'pages/book/new': '新书上架',
  'pages/book/cheap': '低价优选',
  'pages/cart/cart': '购物车',
  'pages/order/confirm': '确认订单',
  'pages/order/list': '我的订单',
  'pages/order/detail': '订单详情',
  'pages/address/list': '收货地址',
  'pages/address/edit': '编辑地址',
  'pages/review/list': '全部评价',
  'pages/review/edit': '发表评价',
  'pages/mine/mine': '我的',
  'pages/login/login': '登录',
  'pages/login/register': '注册'
}

/**
 * 跟随 hash 路由更新 document.title（仅 H5）。
 *
 * uni-app H5 没有全局路由守卫，但 manifest 配的是 hash 模式，
 * 路由变化必然触发 hashchange，监听它比在每个页面 onLoad 里 setTitle 更省事，
 * 也不会漏掉新增页面（漏了就回落到「线上书店」）。
 */
export function startTitleSync() {
  // #ifdef H5
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  const apply = () => {
    const m = String(window.location.hash || '').match(/^#\/(pages\/[^?#]*)/)
    const name = m ? PAGE_TITLES[m[1]] : ''
    document.title = name ? name + ' - ' + BASE_TITLE : BASE_TITLE
  }
  window.addEventListener('hashchange', apply)
  apply()
  // #endif
}
