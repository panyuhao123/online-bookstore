/**
 * 自绘导航与底部 Tab 的高度（px）。
 *
 * 背景：页面配了 navigationStyle: custom 之后，page-nav / nav-bar 都是 fixed 的，
 * 不占布局空间。四个演示页用 scroll-view 承载内容并接自定义下拉刷新，
 * 必须显式算出可用高度 —— 少了会被导航或 Tab 盖住，多了会露出空白。
 *
 * 这里把 rpx→px 的换算收在一处，避免每个页面各写一份 750 设计稿折算。
 */
const sys = uni.getSystemInfoSync()

/** 状态栏高度（px） */
export const statusBarHeight = sys.statusBarHeight || 0

/** rpx 按 750 设计稿折算为 px */
export function rpx(v) {
  return Math.round((v * (sys.windowWidth || 375)) / 750)
}

/** 自绘导航行高（88rpx） */
export const navRowHeight = rpx(88)

/** 自绘导航总高 = 状态栏 + 行高 */
export const navBarHeight = statusBarHeight + navRowHeight

/** 底部安全区（全面屏手势条） */
export const safeBottom = (sys.safeAreaInsets && sys.safeAreaInsets.bottom) || 0

/** 底部自绘 Tab 高度 = 100rpx + 安全区 */
export const tabBarHeight = rpx(100) + safeBottom

/** 可视窗口高度，与 CSS 100vh 等价 */
export const windowHeight = sys.windowHeight || 667

/**
 * 自定义导航页的滚动区高度。
 *
 * @param {Object} opts
 * @param {number} opts.extra    导航之外还要扣掉的顶部高度（px），如搜索栏
 * @param {boolean} opts.tabBar  是否底部有自绘 Tab
 * @returns {number} px
 */
export function scrollHeight(opts) {
  const o = opts || {}
  return Math.max(200, windowHeight - navBarHeight - (o.extra || 0) - (o.tabBar ? tabBarHeight : 0))
}
