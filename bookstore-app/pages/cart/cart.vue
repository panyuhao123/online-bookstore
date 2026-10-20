<template>
  <view class="page">
    <!-- 桌面站点顶栏：购物车没有 page-nav，需自己挂 -->
    <web-header v-if="isWide" />
    <view v-if="isWide" class="web-header-holder" />

    <!-- ① 自定义导航（移动端）：状态栏占位 + 页面标题；桌面用流内标题 -->
    <view v-if="!isWide" class="top-bar">
      <view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />
      <view class="title-row">
        <text class="page-title">购物车</text>
        <text v-if="list.length" class="count-text">共 {{ list.length }} 种</text>
      </view>
    </view>
    <view :style="{ height: topHolder + 'px' }" />

    <!-- 桌面流内标题 -->
    <view v-if="isWide" class="cart-head">
      <text class="ch-title">购物车</text>
      <text v-if="list.length" class="ch-count">共 {{ list.length }} 种图书</text>
    </view>

    <!-- ② 未登录：商品存在本机，登录后自动同步 -->
    <view v-if="!logged && list.length" class="local-tip" @tap="goLogin">
      <text class="local-tip-text">本机购物车 {{ localCount }} 件 · 登录后自动同步到账号</text>
      <text class="local-tip-btn">去登录 ›</text>
    </view>

    <!-- ③ 加载骨架 -->
    <view v-if="loading" class="list-body">
      <view v-for="n in 3" :key="n" class="row">
        <view class="sk sk-anim sk-check" />
        <view class="sk sk-anim sk-thumb" />
        <view class="row-main">
          <view class="sk sk-anim" style="height: 30rpx; width: 85%" />
          <view class="sk sk-anim" style="height: 30rpx; width: 50%; margin-top: 20rpx" />
        </view>
      </view>
    </view>

    <state-view
      v-else-if="!list.length"
      type="empty"
      text="购物车还是空的"
      :sub-text="logged ? '挑几本好书带回家吧' : '未登录也能加购，商品会先存在本机'"
      btn-text="去逛逛"
      @retry="goHome"
    />

    <!-- ============ 桌面：表格化购物车 ============ -->
    <block v-else-if="isWide">
      <view class="cart-wrap">
        <view class="cart-table">
          <!-- 表头 -->
          <view class="ct-head">
            <view class="ct-col-check">
              <view class="ct-check" @tap="toggleAll">
                <view class="ct-box" :class="{ on: allChecked }">
                  <text v-if="allChecked" class="ct-mark">✓</text>
                </view>
              </view>
              <text class="ct-all-label">全选</text>
            </view>
            <text class="ct-col-info">商品信息</text>
            <text class="ct-col-price">单价</text>
            <text class="ct-col-qty">数量</text>
            <text class="ct-col-sub">小计</text>
            <text class="ct-col-op">操作</text>
          </view>

          <!-- 行 -->
          <view
            v-for="item in list"
            :key="item.id"
            class="ct-row"
            :class="{ off: item.stockEnough === false }"
          >
            <view class="ct-col-check">
              <!-- 失效商品复选框置灰且不可勾，也不参与合计 -->
              <view v-if="item.stockEnough === false" class="ct-check is-disabled">
                <view class="ct-box disabled" />
              </view>
              <view v-else class="ct-check" @tap="toggleCheck(item)">
                <view class="ct-box" :class="{ on: item.checked === 1 }">
                  <text v-if="item.checked === 1" class="ct-mark">✓</text>
                </view>
              </view>
            </view>

            <view class="ct-col-info">
              <image class="ct-cover" :src="bookCover(item)" mode="aspectFill" @tap="goDetail(item.bookId)" />
              <view class="ct-info-main">
                <text class="ct-name ellipsis-2" @tap="goDetail(item.bookId)">{{ item.title }}</text>
                <text v-if="item.stockEnough === false" class="ct-tag-off">已失效（库存不足）</text>
                <text v-else-if="item.stock <= 5" class="ct-tag-low">仅剩 {{ item.stock }} 件</text>
              </view>
            </view>

            <text class="ct-col-price">¥{{ price(item.price) }}</text>

            <view class="ct-col-qty">
              <view class="ct-stepper">
                <view class="ct-step" :class="{ off: item.quantity <= 1 }" @tap="changeQty(item, -1)">
                  <text>−</text>
                </view>
                <text class="ct-step-num">{{ item.quantity }}</text>
                <view
                  class="ct-step"
                  :class="{ off: item.stock && item.quantity >= item.stock }"
                  @tap="changeQty(item, 1)"
                >
                  <text>＋</text>
                </view>
              </view>
            </view>

            <text class="ct-col-sub ct-subtotal">¥{{ subtotal(item) }}</text>

            <view class="ct-col-op">
              <text class="ct-del" @tap="removeOne(item)">删除</text>
            </view>
          </view>
        </view>

        <!-- 结算条：随页面滚动吸底，限定在 1200 内容区内 -->
        <view class="ct-settle">
          <view class="cts-left">
            <view class="ct-check" @tap="toggleAll">
              <view class="ct-box" :class="{ on: allChecked }">
                <text v-if="allChecked" class="ct-mark">✓</text>
              </view>
            </view>
            <text class="ct-all-label">全选</text>
            <text
              class="cts-batch"
              :class="{ disabled: selectedCount === 0 || removing }"
              @tap="removeSelected"
            >
              删除选中{{ selectedCount ? `（${selectedCount}）` : '' }}
            </text>
          </view>
          <view class="cts-right">
            <text class="cts-count">已选 {{ checkedCount }} 件</text>
            <text class="cts-ship">本店全场包邮</text>
            <view class="cts-sum">
              <text class="cts-sum-label">合计（含运费 ¥0.00）：</text>
              <text class="cts-sum-num">¥{{ checkedAmount }}</text>
            </view>
            <view
              class="cts-btn"
              :class="{ disabled: checkedCount === 0 || checking }"
              @tap="checkout"
            >
              {{ checkoutText }}
            </view>
          </view>
        </view>

        <!-- 猜你喜欢：按车内第一本书的分类取热门（购物车接口不返回分类字段） -->
        <view v-if="guessList.length" class="cart-like">
          <floor-title :title="guessTitle" sub="换一批好书凑一单" :show-more="false" />
          <view class="grid-books">
            <view v-for="b in guessList" :key="b.id" class="grid-cell">
              <book-card :book="b" @tap="goDetail(b.id)" />
            </view>
          </view>
        </view>
      </view>
    </block>

    <!-- ============ 移动端：原卡片行 + 底部固定结算栏 ============ -->
    <block v-else>
      <view class="list-card card-flush">
        <view v-for="item in list" :key="item.id" class="row" :class="{ 'row-off': item.stockEnough === false }">
          <!-- 勾选框：88rpx 热区 + 40rpx 可视框 -->
          <view class="check" hover-class="hover-opacity" @tap="toggleCheck(item)">
            <view class="check-box" :class="{ on: item.checked === 1 }">
              <text v-if="item.checked === 1" class="check-mark">✓</text>
            </view>
          </view>

          <image class="thumb" :src="bookCover(item)" mode="aspectFill" @tap="goDetail(item.bookId)" />

          <view class="row-main">
            <text class="row-title ellipsis-2" @tap="goDetail(item.bookId)">{{ item.title }}</text>
            <text v-if="item.stockEnough === false" class="row-warn">库存不足，请调整数量</text>
            <text v-else class="row-stock">库存 {{ item.stock }}</text>
            <view class="row-bottom">
              <text class="price">¥{{ price(item.price) }}</text>
              <view class="stepper">
                <view class="step-btn" :class="{ disabled: item.quantity <= 1 }" hover-class="hover-scale" @tap="changeQty(item, -1)">
                  <text>−</text>
                </view>
                <text class="step-num">{{ item.quantity }}</text>
                <view
                  class="step-btn"
                  :class="{ disabled: item.stock && item.quantity >= item.stock }"
                  hover-class="hover-scale"
                  @tap="changeQty(item, 1)"
                >
                  <text>＋</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 删除：88rpx 见方热区 -->
          <view class="del" hover-class="hover-scale" @tap="removeOne(item)">
            <text class="del-text">删除</text>
          </view>
        </view>
      </view>

      <view class="list-holder" />

      <!-- ④ 结算栏：合计与件数由前端实时计算，改数量/勾选后立刻可见 -->
      <view class="float-bar settle-bar">
        <view class="check all" hover-class="hover-scale" @tap="toggleAll">
          <view class="check-box" :class="{ on: allChecked }">
            <text v-if="allChecked" class="check-mark">✓</text>
          </view>
        </view>
        <text class="all-label">全选</text>
        <!-- 件数只在这里出现一次：结算按钮不再重复标注，避免「2 件 / (2)」两处冗余 -->
        <view class="flex-1 summary">
          <view class="sum-line">
            <text class="muted">合计</text>
            <text class="price summary-num">¥{{ checkedAmount }}</text>
          </view>
          <text class="sum-sub">共 {{ checkedCount }} 件 · 全场包邮</text>
        </view>
        <view
          class="btn-primary settle-btn"
          :class="{ disabled: checkedCount === 0 || checking }"
          hover-class="hover-scale"
          @tap="checkout"
        >
          {{ checkoutText }}
        </view>
      </view>
    </block>

    <tab-bar active="cart" />
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 购物车页。
 *
 * 移动端：「卡片行 + 底部结算栏」；
 * 桌面（≥960px）：当当/京东式表格——表头（全选/商品/单价/数量/小计/操作）、
 *   失效商品整行置灰且复选框不可勾（不参与合计）、批量删除选中、
 *   吸底结算条（全场包邮，运费 0）、下方按车内图书分类推荐「猜你喜欢」。
 *
 * 两种数据源对页面透明：
 *   - 已登录：服务端购物车（乐观更新 + 失败回滚）
 *   - 未登录：本机购物车（common/cart-local.js），登录时自动合并
 */
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCartList, updateCart, removeCart } from '@/api/cart'
import { getBook, pageBooks as apiPageBooks } from '@/api/book'
import { getBook as mockGetBook, pageBooks as mockPageBooks, isMockId } from '@/common/mock'
import { isLogin } from '@/common/auth'
// 本机购物车里可能存着演示数据集的书（封面是 APP 本地路径），
// 统一走 bookCover，避免被拼上后端地址
import { bookCover } from '@/common/cover'
import { price } from '@/common/format'
import { notifyCartChanged, refreshCartCount } from '@/common/cart-badge'
import {
  getLocalCart,
  localCartCount,
  updateLocalQuantity,
  toggleLocalChecked,
  removeLocalCart
} from '@/common/cart-local'
import StateView from '@/components/state-view.vue'
import TabBar from '@/components/tab-bar.vue'
import BookCard from '@/components/book-card.vue'
import FloorTitle from '@/components/floor-title.vue'
import WebHeader from '@/components/web-header.vue'
import { isWide, navHeight, WEB_HEADER_H } from '@/common/web'
import WebFooter from '@/components/web-footer.vue'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getSystemInfoSync().statusBarHeight || 0
} catch (e) {
  statusBarHeight.value = 0
}
/** 移动端让出状态栏+标题行；桌面只让出站点顶栏（桌面标题在流内） */
const topHolder = computed(() =>
  isWide.value ? WEB_HEADER_H : navHeight.value + 44
)

const logged = ref(false)
const list = ref([])
const loading = ref(false)
const localCount = ref(0)
/** 单条操作锁：防止连点重复提交 */
const busyId = ref(null)
const checking = ref(false)
const removing = ref(false)

/** 失效行（库存不足）不可勾选，全选/合计都以「有效行」为准 */
const validList = computed(() => list.value.filter((i) => i.stockEnough !== false))

const allChecked = computed(
  () => validList.value.length > 0 && validList.value.every((i) => i.checked === 1)
)

/** 合计：口径与服务端一致——只统计「已勾选 + 库存充足」 */
const checkedAmount = computed(() => {
  const sum = list.value.reduce((s, i) => {
    if (i.checked === 1 && i.stockEnough !== false) {
      return s + Number(i.price || 0) * (i.quantity || 0)
    }
    return s
  }, 0)
  return sum.toFixed(2)
})

const checkedCount = computed(() =>
  list.value.reduce((s, i) => {
    if (i.checked === 1 && i.stockEnough !== false) return s + (i.quantity || 0)
    return s
  }, 0)
)

/** 批量删除按「勾选的种类数」计（含失效行，删除是清空动作） */
const selectedCount = computed(() => list.value.filter((i) => i.checked === 1).length)

const checkoutText = computed(() => {
  if (checking.value) return '处理中...'
  return logged.value ? '去结算' : '登录后同步'
})

function subtotal(item) {
  return (Number(item.price || 0) * (item.quantity || 0)).toFixed(2)
}

async function load() {
  loading.value = true
  try {
    const data = await getCartList()
    list.value = data.list || []
    localCount.value = 0
    loadGuess()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 未登录渲染本机购物车：补一个 id 字段，让模板与操作逻辑可以共用一套 */
function loadLocal() {
  const items = getLocalCart()
  list.value = items.map((item) => ({
    ...item,
    id: item.bookId,
    stockEnough: !item.stock || item.stock >= item.quantity
  }))
  localCount.value = localCartCount()
  loadGuess()
}

/* ---------- 数量（乐观更新 + 失败回滚） ---------- */
async function changeQty(item, delta) {
  if (busyId.value) return
  const next = item.quantity + delta
  if (next < 1) {
    uni.showToast({ title: '至少购买 1 件', icon: 'none' })
    return
  }
  if (item.stock && next > item.stock) {
    uni.showToast({ title: '已达库存上限', icon: 'none' })
    return
  }

  if (!logged.value) {
    updateLocalQuantity(item.bookId, next)
    loadLocal()
    notifyCartChanged()
    return
  }

  const prev = item.quantity
  busyId.value = item.id
  item.quantity = next
  try {
    await updateCart(item.id, { quantity: next })
    notifyCartChanged()
  } catch (e) {
    item.quantity = prev
  } finally {
    busyId.value = null
  }
}

/* ---------- 勾选（乐观更新 + 失败回滚） ---------- */
async function toggleCheck(item) {
  if (busyId.value) return
  if (item.stockEnough === false) return

  if (!logged.value) {
    toggleLocalChecked(item.bookId)
    loadLocal()
    notifyCartChanged()
    return
  }

  const prev = item.checked
  busyId.value = item.id
  item.checked = prev === 1 ? 0 : 1
  try {
    await updateCart(item.id, { checked: item.checked })
    notifyCartChanged()
  } catch (e) {
    item.checked = prev
  } finally {
    busyId.value = null
  }
}

async function toggleAll() {
  if (busyId.value) return
  const target = allChecked.value ? 0 : 1

  if (!logged.value) {
    // 只动有效行：失效行保持原样，避免被勾上
    getLocalCart().forEach((c) => {
      const enough = !c.stock || c.stock >= c.quantity
      const isChecked = c.checked === 1
      if (enough && isChecked !== (target === 1)) {
        toggleLocalChecked(c.bookId)
      }
    })
    loadLocal()
    notifyCartChanged()
    return
  }

  const targets = validList.value.filter((i) => i.checked !== target)
  const prev = targets.map((i) => ({ id: i.id, checked: i.checked }))
  busyId.value = 'all'
  targets.forEach((i) => {
    i.checked = target
  })
  try {
    await Promise.all(targets.map((i) => updateCart(i.id, { checked: target })))
    notifyCartChanged()
  } catch (e) {
    targets.forEach((i, idx) => {
      i.checked = prev[idx].checked
    })
  } finally {
    busyId.value = null
  }
}

/* ---------- 删除 ---------- */
function removeOne(item) {
  uni.showModal({
    title: '提示',
    content: `确定将《${item.title}》移出购物车吗？`,
    success: async (res) => {
      if (!res.confirm) return
      await doRemove([item])
    }
  })
}

/** 批量删除：二次确认后并发删除所有勾选项 */
function removeSelected() {
  if (removing.value) return
  const targets = list.value.filter((i) => i.checked === 1)
  if (!targets.length) return
  uni.showModal({
    title: '删除选中商品',
    content: `确定将选中的 ${targets.length} 种图书移出购物车吗？`,
    success: async (res) => {
      if (!res.confirm) return
      await doRemove(targets)
    }
  })
}

async function doRemove(targets) {
  if (!logged.value) {
    targets.forEach((i) => removeLocalCart(i.bookId))
    loadLocal()
    notifyCartChanged()
    uni.showToast({ title: '已移出购物车', icon: 'none' })
    return
  }
  removing.value = true
  try {
    await Promise.all(targets.map((i) => removeCart(i.id)))
    await load()
    notifyCartChanged()
    uni.showToast({ title: '已移出购物车', icon: 'none' })
  } catch (e) {
    // request 封装已统一弹错
  } finally {
    removing.value = false
  }
}

/* ---------- 结算 ---------- */
function checkout() {
  if (checking.value) return
  if (checkedCount.value === 0) {
    uni.showToast({ title: '请先勾选要结算的商品', icon: 'none' })
    return
  }
  if (lackStock()) return
  // 未登录先登录：登录成功会自动把本机购物车合并到账号，回来即可结算
  if (!logged.value) {
    uni.showModal({
      title: '需要登录',
      content: '结算前需要登录，你本机的购物车会在登录后自动同步到账号',
      confirmText: '去登录',
      success: (res) => {
        if (res.confirm) goLogin()
      }
    })
    return
  }
  checking.value = true
  setTimeout(() => {
    checking.value = false
  }, 1000)
  uni.navigateTo({ url: '/pages/order/confirm' })
}

function lackStock() {
  const lack = list.value.find((i) => i.checked === 1 && i.stockEnough === false)
  if (lack) {
    uni.showToast({ title: '部分商品库存不足，请调整数量', icon: 'none' })
    return true
  }
  return false
}

/* ---------- 猜你喜欢：购物车 VO 无分类字段，取车内第一本书查分类再取同类热门 ---------- */
const guessList = ref([])
const guessCategoryName = ref('')

const guessTitle = computed(() =>
  guessCategoryName.value ? `「${guessCategoryName.value}」猜你喜欢` : '猜你喜欢'
)

async function loadGuess() {
  guessList.value = []
  guessCategoryName.value = ''
  const first = list.value[0]
  if (!first) return
  try {
    let detail = null
    if (isMockId(first.bookId)) {
      detail = await mockGetBook(first.bookId)
    } else {
      try {
        detail = await getBook(first.bookId, { silent: true })
      } catch (e) {
        detail = null
      }
    }
    if (!detail) return
    guessCategoryName.value = detail.categoryName || ''
    const fetchPage = isMockId(first.bookId) ? mockPageBooks : apiPageBooks
    const res = await fetchPage({
      categoryId: detail.categoryId || undefined,
      sort: 'sales',
      pageNum: 1,
      pageSize: 10
    })
    const inCart = new Set(list.value.map((i) => String(i.bookId)))
    guessList.value = ((res && res.list) || [])
      .filter((b) => !inCart.has(String(b.id)))
      .slice(0, 5)
  } catch (e) {
    guessList.value = []
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}

function goHome() {
  uni.redirectTo({ url: '/pages/index/index' })
}

function goDetail(bookId) {
  uni.navigateTo({ url: '/pages/book/detail?id=' + bookId })
}

/**
 * 每次显示都刷新：登录态变化时要在「本机购物车」与「账号购物车」之间切换。
 * 自定义导航下不使用系统下拉刷新（App 端会与自绘导航互相盖住），
 * 靠 onShow 重拉取达到同样的效果。
 */
onShow(() => {
  logged.value = isLogin()
  if (logged.value) {
    load()
  } else {
    loadLocal()
  }
  refreshCartCount()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: 40rpx;
}

/* ---- 自定义导航（移动端） ---- */
.top-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  background-color: $bg-card;
}

.title-row {
  height: 88rpx;
  padding: 0 $gap-page;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: $fs-2xl;
  font-weight: 700;
  color: $text-main;
}

.count-text {
  font-size: $fs-sm;
  color: $text-sub;
}

/* ---- 本机购物车提示 ---- */
.local-tip {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin: $gap-item $gap-page 0;
  padding: $sp-2 $sp-3;
  border-radius: $radius-md;
  background-color: $warning-weak;
}

.local-tip-text {
  flex: 1;
  font-size: $fs-sm;
  color: $warning-strong;
}

.local-tip-btn {
  flex-shrink: 0;
  margin-left: $sp-2;
  font-size: $fs-sm;
  font-weight: 600;
  color: $brand;
}

/* ---- 条目 ---- */
.list-body {
  padding: $gap-item $gap-page 0;
}

.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: $gap-card 0;
  border-bottom: 1rpx solid $border-color;
}

.row:last-child {
  border-bottom: none;
}

/* 库存不足整条降透明度，提示不可结算 */
.row-off {
  opacity: 0.65;
}

.check {
  flex-shrink: 0;
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-box {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $bg-card;
  border: 2rpx solid $border-strong;
  border-radius: $radius-sm;
}

.check-box.on {
  background-color: $brand;
  border-color: $brand;
}

.check-mark {
  font-size: $fs-sm;
  font-weight: 700;
  color: $color-white;
}

.all {
  width: 64rpx;
  height: 64rpx;
}

.all-label {
  margin-left: $sp-1;
  font-size: $fs-base;
  color: $text-regular;
}

.thumb {
  flex-shrink: 0;
  width: 160rpx;
  height: 214rpx;
  border-radius: $radius-sm;
  background-color: $bg-fill;
}

.row-main {
  flex: 1;
  min-width: 0;
  margin: 0 $sp-2;
}

.row-title {
  font-size: $fs-base;
  line-height: 1.5;
  color: $text-main;
}

.row-warn {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-xs;
  color: $brand;
}

.row-stock {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.row-bottom {
  margin-top: $sp-2;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.row-bottom .price {
  font-size: $fs-lg;
}

.stepper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.step-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  color: $text-main;
  background-color: $bg-fill;
  border-radius: $radius-sm;
}

.step-btn.disabled {
  color: $text-placeholder;
}

.step-num {
  width: 72rpx;
  text-align: center;
  font-size: $fs-md;
  font-weight: 600;
}

.del {
  flex-shrink: 0;
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.del-text {
  font-size: $fs-xs;
  color: $text-sub;
}

.sk-check {
  width: 40rpx;
  height: 40rpx;
  border-radius: $radius-sm;
}

.sk-thumb {
  width: 160rpx;
  height: 214rpx;
  margin-left: $sp-3;
}

.skeleton-row-main {
  flex: 1;
}

/* ---- 移动端结算栏 ---- */
/* 本页同时有结算栏和自绘 Tab，两者都是 fixed bottom，
   结算栏要抬到 Tab 上方，否则会互相盖住 */
.settle-bar {
  bottom: calc(100rpx + #{$safe-bottom});
  z-index: 55;
}

.summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-right: $sp-2;
}

.sum-line {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.sum-line .muted {
  margin-right: $sp-1;
}

.sum-sub {
  margin-top: 2rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.summary-num {
  font-size: $fs-xl;
}

.settle-btn {
  flex-shrink: 0;
  min-width: 220rpx;
  height: $btn-h-sm;
}

/* 底部自绘 Tab 占位：结算栏与 Tab 同时存在，要留出两层高度 */
.list-holder {
  height: calc(120rpx + #{$btn-h} + #{$safe-bottom});
}

/* ============================================================
   桌面（≥960px）：表格化购物车
   ============================================================ */
@media (min-width: 960px) {
  .page {
    padding-bottom: 0;
  }

  /* 骨架 / 本机提示收进 1200 内容区 */
  .list-body {
    max-width: 1200px;
    margin: 0 auto;
    padding: 16px 24px 0;
  }

  .local-tip {
    max-width: 1152px;
    margin: 12px auto 0;
    border-radius: 6px;
  }

  /* ---- 流内标题 ---- */
  .cart-head {
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px 24px 4px;
    display: flex;
    flex-direction: row;
    align-items: baseline;
    gap: 12px;
  }

  .ch-title {
    font-size: 24px;
    font-weight: 700;
    color: $text-main;
  }

  .ch-count {
    font-size: 13px;
    color: $text-sub;
  }

  .cart-wrap {
    max-width: 1200px;
    margin: 0 auto;
    padding: 12px 24px 40px;
  }

  /* ---- 表格 ---- */
  .cart-table {
    background-color: $bg-card;
    border-radius: 10px;
    border: 1rpx solid $border-color;
    overflow: hidden;
  }

  /* 六列栅格：勾选 56 / 商品自适应 / 单价 110 / 数量 130 / 小计 120 / 操作 90 */
  .ct-head,
  .ct-row {
    display: grid;
    grid-template-columns: 56px minmax(0, 1fr) 110px 130px 120px 90px;
    align-items: center;
  }

  .ct-head {
    height: 48px;
    padding: 0 20px;
    font-size: 13px;
    color: $text-sub;
    background-color: $bg-page;
    border-bottom: 1px solid $border-color;
  }

  .ct-row {
    min-height: 110px;
    padding: 14px 20px;
    border-bottom: 1px solid $border-color;
  }

  .ct-row:last-child {
    border-bottom: none;
  }

  /* 失效行：灰度 + 屏蔽指针 */
  .ct-row.off {
    background-color: $bg-page;
    opacity: 0.75;
  }

  .ct-row.off .ct-name,
  .ct-row.off .ct-col-price,
  .ct-row.off .ct-subtotal,
  .ct-row.off .ct-cover {
    filter: grayscale(1);
    color: $text-placeholder;
  }

  .ct-col-check {
    display: flex;
    flex-direction: row;
    align-items: center;
  }

  .ct-check {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
  }

  .ct-check.is-disabled {
    cursor: not-allowed;
  }

  .ct-box {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: $bg-card;
    border: 1px solid $border-strong;
    border-radius: 3px;
    box-sizing: border-box;
  }

  .ct-box.on {
    background-color: $brand;
    border-color: $brand;
  }

  .ct-box.disabled {
    background-color: $bg-fill;
    border-color: $border-color;
  }

  .ct-mark {
    font-size: 12px;
    font-weight: 700;
    line-height: 1;
    color: #fff;
  }

  .ct-all-label {
    font-size: 13px;
    color: $text-regular;
    user-select: none;
  }

  .ct-col-info {
    display: flex;
    flex-direction: row;
    align-items: center;
    min-width: 0;
  }

  .ct-cover {
    flex-shrink: 0;
    width: 72px;
    height: 96px;
    border-radius: 4px;
    background-color: $bg-fill;
    cursor: pointer;
  }

  .ct-info-main {
    flex: 1;
    min-width: 0;
    margin-left: 14px;
  }

  .ct-name {
    display: -webkit-box;
    font-size: 14px;
    line-height: 1.5;
    color: $text-main;
    cursor: pointer;
  }

  .ct-tag-off,
  .ct-tag-low {
    display: inline-block;
    margin-top: 6px;
    padding: 1px 8px;
    font-size: 12px;
    border-radius: 3px;
  }

  .ct-tag-off {
    color: $text-sub;
    background-color: $bg-fill;
  }

  .ct-tag-low {
    color: $warning-strong;
    background-color: $warning-weak;
  }

  .ct-col-price,
  .ct-col-qty,
  .ct-col-sub,
  .ct-col-op {
    font-size: 14px;
    color: $text-regular;
  }

  .ct-col-price {
    color: $text-regular;
  }

  .ct-subtotal {
    font-size: 16px;
    font-weight: 700;
    color: $brand;
  }

  /* 数量步进器 */
  .ct-stepper {
    display: inline-flex;
    flex-direction: row;
    align-items: center;
  }

  .ct-step {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    color: $text-main;
    background-color: $bg-card;
    border: 1px solid $border-color;
    box-sizing: border-box;
    cursor: pointer;
  }

  .ct-step:first-child {
    border-radius: 4px 0 0 4px;
  }

  .ct-step:last-child {
    border-radius: 0 4px 4px 0;
  }

  .ct-step.off {
    color: $text-placeholder;
    background-color: $bg-fill;
    cursor: not-allowed;
  }

  .ct-step-num {
    width: 48px;
    height: 28px;
    line-height: 26px;
    text-align: center;
    font-size: 13px;
    font-weight: 600;
    background-color: $bg-card;
    border-top: 1px solid $border-color;
    border-bottom: 1px solid $border-color;
    box-sizing: border-box;
  }

  .ct-del {
    font-size: 13px;
    color: $text-sub;
    cursor: pointer;
  }

  .ct-del:hover {
    color: $brand;
  }

  /* ---- 吸底结算条（随文档滚动，限定在内容区内） ---- */
  .ct-settle {
    position: sticky;
    bottom: 0;
    z-index: 20;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    margin-top: 16px;
    padding: 14px 20px;
    background-color: $bg-card;
    border-top: 1px solid $border-color;
    border-radius: 10px;
    box-shadow: 0 -4px 16px rgba(31, 33, 38, 0.08);
  }

  .cts-left {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 4px;
  }

  .cts-batch {
    margin-left: 12px;
    padding-left: 16px;
    font-size: 13px;
    color: $text-regular;
    border-left: 1px solid $border-color;
    cursor: pointer;
  }

  .cts-batch:hover {
    color: $brand;
  }

  .cts-batch.disabled {
    color: $text-placeholder;
    cursor: not-allowed;
  }

  .cts-right {
    display: flex;
    flex-direction: row;
    align-items: center;
  }

  .cts-count {
    font-size: 13px;
    color: $text-regular;
  }

  .cts-ship {
    margin-left: 16px;
    padding: 2px 10px;
    font-size: 12px;
    color: $brand;
    background-color: $brand-weak;
    border-radius: 3px;
  }

  .cts-sum {
    display: flex;
    flex-direction: row;
    align-items: baseline;
    margin-left: 20px;
  }

  .cts-sum-label {
    font-size: 13px;
    color: $text-sub;
  }

  .cts-sum-num {
    font-size: 22px;
    font-weight: 700;
    color: $brand;
  }

  .cts-btn {
    width: 128px;
    height: 44px;
    margin-left: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    font-weight: 600;
    color: #fff;
    background-color: $brand;
    border-radius: 4px;
    cursor: pointer;
    user-select: none;
  }

  .cts-btn.disabled {
    color: $text-placeholder;
    background-color: $border-strong;
    cursor: not-allowed;
  }

  /* ---- 猜你喜欢 ---- */
  .cart-like {
    margin-top: 32px;
  }

  .cart-like :deep(.floor-head) {
    margin-left: 0;
    margin-right: 0;
  }

  .cart-like .grid-books {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 20px;
    margin-top: 12px;
  }

  .cart-like .grid-cell {
    width: 100%;
    min-width: 0;
  }
}

@media (hover: hover) and (pointer: fine) {
  .cts-btn:not(.disabled):hover {
    background-color: $brand-dark;
  }

  .ct-name:hover {
    color: $brand;
  }
}
</style>
