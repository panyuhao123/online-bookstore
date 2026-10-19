<template>
  <view class="page">
    <!-- 桌面站点顶栏：个人中心没有 page-nav，需自己挂 -->
    <web-header v-if="isWide" />
    <view v-if="isWide" class="web-header-holder" />

    <!-- ① 身份头部：渐变底 + 头像 + 昵称 / 手机号 -->
    <view class="hero" :style="{ paddingTop: isWide ? 0 : statusBarHeight + 'px' }">
      <view class="hero-row">
        <view class="avatar" hover-class="hover-opacity" @tap="onAvatarTap">{{ avatarChar }}</view>
        <view class="hero-main">
          <template v-if="logged">
            <text class="nickname">{{ user.nickname || '书友' }}</text>
            <text class="phone">{{ maskedPhone }}</text>
          </template>
          <template v-else>
            <text class="nickname">未登录</text>
            <text class="phone link" @tap="goLogin">点击登录 →</text>
          </template>
        </view>
      </view>

      <!-- 资产行：真实数据（购物车 / 评价）。
           订单不再出现在这里——订单只保留下方一组快捷入口，避免同一信息两处重复 -->
      <view class="perk-row">
        <view class="perk" hover-class="hover-scale" @tap="goCart">
          <text class="perk-title">{{ cartCount }}</text>
          <text class="perk-sub">购物车</text>
        </view>
        <view class="perk" hover-class="hover-scale" @tap="goReviews">
          <text class="perk-title">{{ reviewTotal }}</text>
          <text class="perk-sub">我的评价</text>
        </view>
      </view>
    </view>

    <!-- ② 交易信息：订单状态 + 收货地址
         收货地址是个人中心唯一的独有入口，与订单同属「交易」链路，因此并入同一张卡片底部；
         不单独为它开一张卡 —— 一个入口占一整张卡会留大片空白 -->
    <view v-if="logged" class="card">
      <view class="flex-between">
        <text class="sec-title">我的订单</text>
        <text class="muted" @tap="goOrders('')">全部 ›</text>
      </view>
      <view class="order-quad">
        <view v-for="q in orderQuads" :key="q.status" class="quad" hover-class="hover-scale" @tap="goOrders(q.status)">
          <text class="quad-num">{{ counts[q.status] || 0 }}</text>
          <text class="quad-label">{{ q.label }}</text>
        </view>
      </view>

      <view class="addr-row" hover-class="hover-dim" @tap="goAddress">
        <view class="addr-icon">
          <entry-icon name="address" tone="purple" size="small" />
        </view>
        <text class="addr-label">收货地址</text>
        <text class="addr-arrow">›</text>
      </view>
    </view>

    <!-- ④ 猜您喜欢：与首页同款双列卡片 -->
    <floor-title title="猜您喜欢" @more="goRank" />
    <view v-if="recBooks.length" class="grid-books">
      <view v-for="b in recBooks" :key="b.id" class="grid-cell">
        <book-card :book="b" @tap="goDetail(b)" />
      </view>
    </view>
    <state-view
      v-else
      :type="recStateType"
      text="暂无推荐图书"
      sub-text="去首页看看新书上架"
      btn-text="重新加载"
      @retry="loadRecommend"
    />

    <view v-if="logged" class="logout-btn" :class="{ disabled: loggingOut }" hover-class="hover-opacity" @tap="onLogout">
      {{ loggingOut ? '退出中...' : '退出登录' }}
    </view>

    <view class="tabbar-holder" />
    <tab-bar active="mine" />
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 个人中心：示例 App 的「渐变身份区 + 订单宫格 + 服务宫格 + 推荐楼层」结构。
 *
 * 与旧版的区别：旧版是「头部 + 订单四宫格 + 列表式菜单」，示例形态把菜单换成
 * 8 宫格，并在底部补了推荐流（逛完还能继续逛），信息密度与示例 App 对齐。
 *
 * 页面上每一处数字都来自真实接口：购物车件数、各状态订单数、评价数。
 */
import { ref, computed, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { logout } from '@/api/user'
import { pageOrders } from '@/api/order'
import { pageMyReviews } from '@/api/review'
import { pageBooks } from '@/api/book'
import { isLogin, getUser, clearAuth } from '@/common/auth'
import { cartCount, refreshCartCount } from '@/common/cart-badge'
import EntryIcon from '@/components/entry-icon.vue'
import FloorTitle from '@/components/floor-title.vue'
import BookCard from '@/components/book-card.vue'
import StateView from '@/components/state-view.vue'
import TabBar from '@/components/tab-bar.vue'
import WebHeader from '@/components/web-header.vue'
import { isWide } from '@/common/web'
import WebFooter from '@/components/web-footer.vue'

/** 四宫格：与订单状态 1待支付 2已支付 3已发货 4已完成 一一对应。
 *  文案按用户视角写（待发货 / 待收货），而不是照搬数据库状态名 */
const orderQuads = [
  { label: '待支付', status: 1 },
  { label: '待发货', status: 2 },
  { label: '待收货', status: 3 },
  { label: '已完成', status: 4 }
]

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getSystemInfoSync().statusBarHeight || 0
} catch (e) {
  statusBarHeight.value = 0
}

const logged = ref(false)
const user = ref({})
const loggingOut = ref(false)
const counts = reactive({ 1: 0, 2: 0, 3: 0, 4: 0 })
const reviewTotal = ref(0)
const recBooks = ref([])
const recState = ref('loading')

const avatarChar = computed(() => ((user.value && user.value.nickname) || '书').charAt(0))

const maskedPhone = computed(() => {
  const p = (user.value && user.value.phone) || ''
  return p.length === 11 ? p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : p
})

/**
 * 个人中心只放「别处没有」的入口。
 *
 * 不放书单 / 新书上架 / 低价优选：这三个是「逛」的入口，首页宫格与底部 Tab 都已可达，
 * 再摆一份就是同一个功能三个入口，用户点哪个都一样 —— 这类重复入口在成熟电商里是被刻意收敛的。
 * 也不放客服中心：客服需要工单 / 会话支撑，本项目没有这块数据，
 * 摆一个只能弹文案的空壳入口反而在答辩时容易被质疑；售后诉求由「我的订单」承接。
 *
 * 于是这里只剩收货地址一个：它是真正的个人功能，且没有第二处入口。
 */
function goAddress() {
  if (!isLogin()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 500)
    return
  }
  uni.navigateTo({ url: '/pages/address/list' })
}

function refresh() {
  logged.value = isLogin()
  user.value = getUser() || {}
}

/**
 * 各状态订单数并发取（pageSize=1 只取 total，返回体最小）。
 * 单个失败不影响其余宫格。
 */
async function loadCounts() {
  if (!logged.value) {
    Object.keys(counts).forEach((k) => {
      counts[k] = 0
    })
    reviewTotal.value = 0
    return
  }

  const results = await Promise.all(
    orderQuads.map((q) =>
      pageOrders({ status: q.status, pageNum: 1, pageSize: 1 })
        .then((data) => ({ status: q.status, total: (data && data.total) || 0 }))
        .catch(() => ({ status: q.status, total: 0 }))
    )
  )
  results.forEach((r) => {
    counts[r.status] = r.total
  })

  // 只取评价数：订单总数不再单独展示（入口统一收在上方订单宫格）
  const rv = await pageMyReviews({ pageNum: 1, pageSize: 1 }).catch(() => null)
  reviewTotal.value = (rv && rv.total) || 0
}

/** 推荐位状态：done 但没数据时要显示为「空」而不是让三态组件拿到未知值 */
const recStateType = computed(() => {
  if (recState.value === 'loading') return 'loading'
  if (recState.value === 'error') return 'error'
  return 'empty'
})

async function loadRecommend() {
  recState.value = 'loading'
  try {
    const page = await pageBooks({ pageNum: 1, pageSize: 4, sort: 'sales' })
    recBooks.value = page.list || []
    recState.value = 'done'
  } catch (e) {
    recBooks.value = []
    recState.value = 'error'
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}

function goCart() {
  uni.redirectTo({ url: '/pages/cart/cart' })
}

/** 需登录的入口统一引导 */
function goIfLogin(url) {
  if (!isLogin()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 500)
    return
  }
  uni.navigateTo({ url })
}

function goOrders(status = '') {
  goIfLogin('/pages/order/list' + (status === '' ? '' : '?status=' + status))
}

function goReviews() {
  goIfLogin('/pages/review/list')
}

/**
 * 隐藏调试入口：连点头像 5 次进入服务器地址设置。
 *
 * 为什么不做成可见入口：真实电商 APP 不会有「服务器地址」这种设置项，
 * 摆在外面一眼就能看出是学生项目。之所以保留而不是直接删页，是因为
 * 自动探测只试「上次成功的地址 + 打包默认地址」，电脑 IP 变了且缓存失效时，
 * 手动改地址是唯一兜底。隐藏入口沿用 Android「连点版本号开开发者选项」的做法。
 */
let debugTaps = 0
let debugTimer = null
function onAvatarTap() {
  debugTaps += 1
  clearTimeout(debugTimer)
  debugTimer = setTimeout(() => {
    debugTaps = 0
  }, 1500)
  if (debugTaps >= 5) {
    debugTaps = 0
    clearTimeout(debugTimer)
    goServer()
    return
  }
  if (debugTaps >= 3) {
    uni.showToast({ title: `再点 ${5 - debugTaps} 次进入调试设置`, icon: 'none', duration: 1200 })
  }
}

function goServer() {
  uni.navigateTo({ url: '/pages/mine/server' })
}

function goRank() {
  uni.navigateTo({
    url: '/pages/book/list?sort=sales&title=' + encodeURIComponent('猜您喜欢')
  })
}

let navigating = false
function goDetail(book) {
  if (navigating) return
  navigating = true
  setTimeout(() => {
    navigating = false
  }, 800)
  uni.navigateTo({ url: '/pages/book/detail?id=' + book.id })
}

function onLogout() {
  if (loggingOut.value) return
  uni.showModal({
    title: '提示',
    content: '确定退出登录吗？',
    success: async (res) => {
      if (!res.confirm) return
      loggingOut.value = true
      await logout() // silent：失败也不阻断本地清理
      clearAuth()
      refreshCartCount()
      loggingOut.value = false
      refresh()
      loadCounts()
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  })
}

onShow(() => {
  refresh()
  refreshCartCount()
  loadCounts()
  if (!recBooks.value.length) loadRecommend()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: 40rpx;
}

/* ---- 身份头部 ---- */
.hero {
  padding: 0 $gap-page $gap-card;
  background: linear-gradient(160deg, $brand-light 0%, $brand 70%);
  border-radius: 0 0 40rpx 40rpx;
}

.hero-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $sp-3 0;
}

.avatar {
  flex-shrink: 0;
  width: 110rpx;
  height: 110rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 700;
  color: $brand;
  background-color: $color-white;
  border-radius: 50%;
}

.hero-main {
  margin-left: $sp-3;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.nickname {
  font-size: $fs-2xl;
  font-weight: 700;
  color: $color-white;
}

.phone {
  margin-top: $sp-1;
  font-size: $fs-base;
  color: $color-white;
  opacity: 0.9;
}

.link {
  text-decoration: underline;
}

/* ---- 资产行：三张半透明小卡 ---- */
.perk-row {
  display: flex;
  flex-direction: row;
  gap: $sp-2;
}

.perk {
  flex: 1;
  padding: $sp-2;
  border-radius: $radius-md;
  background-color: rgba(255, 255, 255, 0.16);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.perk-title {
  font-size: $fs-xl;
  font-weight: 700;
  color: $color-white;
}

.perk-sub {
  margin-top: 4rpx;
  font-size: $fs-xs;
  color: $color-white;
  opacity: 0.85;
}

/* ---- 订单宫格 ---- */
.order-quad {
  display: flex;
  flex-direction: row;
  margin-top: $gap-card;
}

.quad {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $sp-2 0;
}

.quad-num {
  font-size: 36rpx;
  font-weight: 700;
  color: $text-main;
}

.quad-label {
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-sub;
}

/* ---- 收货地址：订单卡底部一行，用分隔线归属到交易信息 ---- */
.addr-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: $sp-3;
  padding-top: $sp-3;
  border-top: 1rpx solid $border-color;
}

.addr-icon {
  flex-shrink: 0;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background-color: #eeedfe;
}

.addr-label {
  flex: 1;
  min-width: 0;
  padding-left: $sp-2;
  font-size: $fs-base;
  color: $text-regular;
}

.addr-arrow {
  font-size: $fs-lg;
  color: $text-sub;
}

/* ---- 推荐流 ---- */
.grid-books {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin: 0 $gap-page;
  gap: $gap-item;
}

.grid-cell {
  width: calc((100% - #{$gap-item}) / 2);
  display: flex;
}

/* ---- 退出 ---- */
.logout-btn {
  margin: $gap-block $gap-page 0;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $brand;
  background-color: $bg-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
}

.logout-btn.disabled {
  color: $text-sub;
}

.tabbar-holder {
  height: calc(100rpx + #{$safe-bottom});
}

/* 桌面：猜您喜欢改 5 列栅格（与首页楼层一致），不再依赖全局宽度规则 */
@media (min-width: 960px) {
  .grid-books {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 20px;
    margin: 0 24px;
  }

  .grid-cell {
    width: 100%;
    min-width: 0;
  }

  :deep(.floor-head) {
    margin-left: 24px;
    margin-right: 24px;
  }
}

@media (min-width: 1240px) {
  .grid-books {
    margin: 0 auto;
  }

  :deep(.floor-head) {
    margin-left: auto;
    margin-right: auto;
  }
}
</style>
