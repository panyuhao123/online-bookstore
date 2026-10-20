/**
 * 自绘导航与底部 Tab 的高度（px）。
 *
 * 背景：页面配了 navigationStyle: custom 之后，page-nav / nav-bar 都是 fixed 的，
 * 不占布局空间。滚动容器必须扣掉导航与 Tab 的高度，少了会被盖住，多了会露出空白。
 *
 * 滚动区高度一律走 CSS `calc(100vh - var(--nav-h))`（见 common/web.scss），
 * 本文件只提供各段实测高度，不再提供 JS 版高度计算函数。
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
const navRowHeight = rpx(88)

/** 自绘导航总高 = 状态栏 + 行高 */
export const navBarHeight = statusBarHeight + navRowHeight

/** 底部安全区（全面屏手势条） */
const safeBottom = (sys.safeAreaInsets && sys.safeAreaInsets.bottom) || 0

/** 底部自绘 Tab 高度 = 100rpx + 安全区 */
export const tabBarHeight = rpx(100) + safeBottom
