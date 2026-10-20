<template>
  <view class="page">
    <!-- 桌面站点顶栏：详情页没有 page-nav / nav-bar，需自己挂 -->
    <web-header v-if="isWide" />
    <view v-if="isWide" class="web-header-holder" />

    <!-- 移动端：自绘返回键 -->
    <view v-if="!isWide" class="back-btn" :style="{ top: backTop + 'px' }" hover-class="hover-opacity" @tap="goBack">
      <text class="back-char">‹</text>
    </view>

    <!-- 加载骨架 -->
    <view v-if="loading" class="skeleton">
      <view class="cover-area">
        <view class="cover sk sk-anim" />
      </view>
      <view class="card">
        <view class="sk sk-anim" style="height: 48rpx; width: 40%" />
        <view class="sk sk-anim" style="height: 40rpx; width: 80%; margin-top: 20rpx" />
        <view class="sk sk-anim" style="height: 28rpx; width: 55%; margin-top: 20rpx" />
      </view>
    </view>

    <state-view
      v-else-if="!book"
      type="error"
      text="图书信息加载失败"
      sub-text="可能已下架，或网络不稳定"
      btn-text="重新加载"
      @retry="reload"
    />

    <template v-else>
      <!-- ============================================================
           桌面：当当式「面包屑 + 左图集 + 右购买面板 + 下方详情/评价 + 推荐」
           ============================================================ -->
      <block v-if="isWide">
        <view class="pd-wrap">
          <bread-crumb :items="detailCrumbs" />

          <view class="pd-main">
            <!-- 左：主图 + 缩略图，点击主图放大 -->
            <view class="pd-gallery">
              <view class="pd-stage" hover-class="hover-dim" @tap="openZoom">
                <cover-box :book="book" :char="firstChar" />
                <view class="pd-zoom-hint">
                  <text class="pz-ic">⤢</text>
                  <text>点击放大</text>
                </view>
              </view>
              <view class="pd-thumbs">
                <view class="pd-thumb on">
                  <cover-box :book="book" :char="firstChar" />
                </view>
              </view>
            </view>

            <!-- 右：购买面板 -->
            <view class="pd-panel">
              <text class="pd-title">{{ book.title }}</text>
              <view class="pd-sub">
                <text>{{ book.author || '佚名' }}</text>
                <text class="pd-sep">·</text>
                <text>{{ book.publisher || '暂无出版社信息' }}</text>
                <text v-if="book.categoryName" class="pd-cat">{{ book.categoryName }}</text>
              </view>

              <view v-if="reviewTotal > 0" class="pd-rating" hover-class="hover-opacity" @tap="goReviews">
                <text class="pd-stars">{{ starStr(roundRating) }}</text>
                <text class="pd-score">{{ avgText }} 分</text>
                <text class="pd-rv-cnt">{{ reviewTotal }} 条评价 ›</text>
              </view>

              <!-- 价格色块区：弱品牌色底，价格/原价/折扣/库存/销量一行一块 -->
              <view class="pd-pricebox">
                <view class="pp-row">
                  <text class="pp-label">价 格</text>
                  <text class="rmb">¥</text>
                  <text class="pp-price">{{ price(book.price) }}</text>
                  <text v-if="originalPrice" class="pp-origin">定价 ¥{{ price(originalPrice) }}</text>
                  <text v-if="discountText" class="pp-discount">{{ discountText }}</text>
                </view>
                <view class="pp-row pp-sub-row">
                  <text class="pp-label">库 存</text>
                  <text class="pp-stock" :class="{ out: soldOut, low: !soldOut && book.stock <= 5 }">{{ stockText }}</text>
                  <text class="pp-sales">已售 {{ salesText }} 件</text>
                </view>
              </view>

              <!-- 服务承诺：图标用弱品牌色圆点 + 勾，避免空口承诺（均与现有口径一致） -->
              <view class="pd-services">
                <view class="sv"><text class="sv-ic">✓</text><text>正版保障</text></view>
                <view class="sv"><text class="sv-ic">✓</text><text>全场包邮</text></view>
                <view class="sv"><text class="sv-ic">✓</text><text>48 小时内发货</text></view>
              </view>

              <!-- 数量：到达边界按钮置灰不可点 -->
              <view class="pd-qrow">
                <text class="pq-label">数 量</text>
                <view class="pd-stepper">
                  <view class="pq-btn" :class="{ off: qty <= 1 }" @tap="changeQty(-1)">
                    <text>−</text>
                  </view>
                  <text class="pq-num">{{ qty }}</text>
                  <view class="pq-btn" :class="{ off: qty >= stockLimit }" @tap="changeQty(1)">
                    <text>＋</text>
                  </view>
                </view>
                <text class="pq-tip">最多可购 {{ stockLimit }} 件</text>
              </view>

              <view class="pd-actions">
                <view class="pd-cart" :class="{ disabled: soldOut || adding }" hover-class="hover-opacity" @tap="addCart">
                  <view class="pd-cart-ic" />
                  <text>{{ adding ? '加入中...' : '加入购物车' }}</text>
                </view>
                <view class="pd-buy" :class="{ disabled: soldOut || buying }" hover-class="hover-opacity" @tap="buyNow">
                  <text>{{ soldOut ? '暂时售罄' : buying ? '处理中...' : '立即购买' }}</text>
                </view>
              </view>

              <!-- 加购成功反馈：站内条 + 去购物车直达（移动端仍是 Toast） -->
              <view v-if="addedTip" class="pd-tip">
                <text>已加入购物车</text>
                <text class="pd-tip-link" hover-class="hover-opacity" @tap="goCart">去购物车 ›</text>
              </view>
            </view>
          </view>

          <!-- 图书详情 -->
          <view class="pd-sec-card">
            <view class="ps-tabbar"><text class="ps-tab on">图书详情</text></view>
            <view class="ps-kv">
              <view class="ps-k"><text class="k">作者</text><text class="v">{{ book.author || '—' }}</text></view>
              <view class="ps-k"><text class="k">出版社</text><text class="v">{{ book.publisher || '—' }}</text></view>
              <view class="ps-k"><text class="k">分类</text><text class="v">{{ book.categoryName || '—' }}</text></view>
              <view class="ps-k"><text class="k">库存</text><text class="v">{{ soldOut ? '暂无库存' : book.stock + ' 件' }}</text></view>
            </view>
            <text class="ps-desc">{{ book.description || '暂无简介' }}</text>
          </view>

          <!-- 用户评价：评分筛选在前端对已加载评价过滤（接口无 rating 参数） -->
          <view class="pd-sec-card">
            <view class="ps-tabbar">
              <text class="ps-tab on">用户评价</text>
              <text v-if="reviewTotal > 0" class="ps-tab-cnt">（共 {{ reviewTotal }} 条）</text>
            </view>

            <view v-if="rvLoading" class="rv-block">
              <view class="sk sk-anim" style="height: 24px; width: 40%" />
              <view class="sk sk-anim" style="height: 14px; width: 90%; margin-top: 12px" />
            </view>

            <template v-else-if="reviewTotal > 0">
              <view class="rv-sum">
                <text class="rv-score">{{ avgText }}</text>
                <text class="rv-stars">{{ starStr(roundRating) }}</text>
                <text class="rv-cnt">{{ reviewTotal }} 条评价</text>
              </view>

              <view class="rv-filter">
                <view
                  v-for="f in ratingFilters"
                  :key="f.key"
                  class="rvf"
                  :class="{ on: ratingFilter === f.key }"
                  @tap="ratingFilter = f.key"
                >
                  {{ f.label }}<text v-if="f.count" class="rvf-cnt">（{{ f.count }}）</text>
                </view>
              </view>

              <view v-if="filteredReviews.length">
                <view v-for="r in filteredReviews" :key="r.id" class="rv-item">
                  <view class="rv-top">
                    <text class="rv-name">{{ r.nickname || '匿名用户' }}</text>
                    <text class="rv-stars sm">{{ starStr(r.rating) }}</text>
                  </view>
                  <text class="rv-text ellipsis-2">{{ r.content || '该用户未填写评价内容' }}</text>
                </view>
              </view>
              <view v-else class="rv-empty">本页加载的评价中没有该星级，可查看全部评价</view>

              <view class="rv-more" hover-class="hover-opacity" @tap="goReviews">
                查看全部 {{ reviewTotal }} 条评价 ›
              </view>
            </template>

            <view v-else class="rv-empty">暂无评价，购买后可发表第一条评价</view>
          </view>

          <!-- 看了又看：同分类热门，排除当前书 -->
          <view v-if="related.length" class="pd-related">
            <floor-title title="看了又看" sub="同分类热门好书" :show-more="false" />
            <view class="grid-books">
              <view v-for="b in related" :key="b.id" class="grid-cell">
                <book-card :book="b" @tap="goDetail(b)" />
              </view>
            </view>
          </view>
        </view>
      </block>

      <!-- ============================================================
           移动端：原有「大封面 + 信息卡 + 底部双按钮」单列结构
           ============================================================ -->
      <block v-else>
        <view class="detail-body">
          <!-- ② 封面区：整块白底大图（示例 App 的沉浸式封面） -->
          <view class="cover-area">
            <view class="cover-img">
              <cover-box :book="book" :char="firstChar" />
            </view>
          </view>

          <view class="right-col">
            <!-- ③ 信息卡：上移压住封面底边，形成层次 -->
            <view class="card info-card">
              <view class="price-row">
                <text class="rmb">¥</text>
                <text class="price-num">{{ price(book.price) }}</text>
                <text class="sales">已售 {{ salesText }}</text>
              </view>
              <text class="title">{{ book.title }}</text>
              <view class="meta-row">
                <text class="tag" :class="stockTagClass">{{ stockText }}</text>
                <text v-if="book.categoryName" class="tag-outline">{{ book.categoryName }}</text>
              </view>
              <view class="divider" />
              <view class="kv"><text class="k">作者</text><text class="v">{{ book.author || '—' }}</text></view>
              <view class="kv"><text class="k">出版社</text><text class="v">{{ book.publisher || '—' }}</text></view>
            </view>

            <!-- ④ 数量步进 -->
            <view class="card">
              <view class="flex-between">
                <text class="sec-title">购买数量</text>
                <view class="stepper">
                  <view class="step-btn" :class="{ disabled: qty <= 1 }" hover-class="hover-opacity" @tap="changeQty(-1)">
                    <text>−</text>
                  </view>
                  <text class="step-num">{{ qty }}</text>
                  <view
                    class="step-btn"
                    :class="{ disabled: qty >= stockLimit }"
                    hover-class="hover-opacity"
                    @tap="changeQty(1)"
                  >
                    <text>＋</text>
                  </view>
                </view>
              </view>
              <view v-if="!soldOut && book.stock <= 5" class="stock-tip">库存紧张，仅剩 {{ book.stock }} 件</view>
            </view>

            <!-- ⑤ 简介 -->
            <view class="card">
              <text class="sec-title">图书简介</text>
              <text class="desc">{{ book.description || '暂无简介' }}</text>
            </view>

            <!-- ⑥ 用户评价 -->
            <view class="card">
              <view class="rv-head">
                <text class="sec-title">用户评价</text>
                <view v-if="reviewTotal > 0" class="rv-all" hover-class="hover-opacity" @tap="goReviews">
                  <text class="rv-all-text">查看全部 {{ reviewTotal }} 条</text>
                  <text class="rv-arrow">›</text>
                </view>
              </view>

              <view v-if="rvLoading" class="rv-block">
                <view class="sk sk-anim" style="height: 32rpx; width: 40%" />
                <view class="sk sk-anim" style="height: 26rpx; width: 90%; margin-top: 16rpx" />
              </view>

              <template v-else-if="reviewTotal > 0">
                <view class="rv-sum">
                  <text class="rv-score">{{ avgText }}</text>
                  <text class="rv-stars">{{ starStr(roundRating) }}</text>
                  <text class="rv-cnt">{{ reviewTotal }} 条评价</text>
                </view>
                <view v-for="r in reviews" :key="r.id" class="rv-item">
                  <view class="rv-top">
                    <text class="rv-name">{{ r.nickname || '匿名用户' }}</text>
                    <text class="rv-stars sm">{{ starStr(r.rating) }}</text>
                  </view>
                  <text class="rv-text ellipsis-2">{{ r.content || '该用户未填写评价内容' }}</text>
                </view>
              </template>

              <view v-else class="rv-empty">暂无评价，购买后可发表第一条评价</view>
            </view>

            <view class="bar-holder" />
          </view>
        </view>

        <!-- ⑦ 底部行动栏（移动端固定通栏，桌面按钮在购买面板内） -->
        <view class="float-bar">
          <view class="btn-outline bar-btn" :class="{ disabled: soldOut || adding }" hover-class="hover-opacity" @tap="addCart">
            {{ adding ? '加入中...' : '加入购物车' }}
          </view>
          <view class="btn-primary bar-btn" :class="{ disabled: soldOut || buying }" hover-class="hover-opacity" @tap="buyNow">
            {{ soldOut ? '暂时售罄' : buying ? '处理中...' : '立即购买' }}
          </view>
        </view>
      </block>

      <!-- 封面放大层（仅桌面） -->
      <view v-if="isWide && zooming" class="pd-zoom-mask" @tap="closeZoom">
        <view class="pd-zoom-box" @tap.stop>
          <cover-box :book="book" :char="firstChar" />
        </view>
        <view class="pd-zoom-close" @tap="closeZoom"><text>×</text></view>
      </view>
    </template>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 图书详情页。
 *
 * 移动端：示例 App 的「大封面 + 信息卡上移 + 底部双按钮」结构；
 * 桌面（≥960px）：当当式购买面板——面包屑 / 左图集（点击放大）/
 *   右价格色块面板 + 数量 + 双按钮 + 服务承诺 / 下方详情与评价（星级前端筛选）/
 *   同分类「看了又看」。两套结构共用同一份数据与加购/立即购买逻辑。
 *
 * 页面在 pages.json 里配了 navigationStyle: custom，所以顶部返回键是自绘的。
 */
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBook, pageBooks as apiPageBooks } from '@/api/book'
import { getBook as mockGetBook, pageBooks as mockPageBooks, isMockId } from '@/common/mock'
import { pageBookReviews, getReviewSummary } from '@/api/review'
import { addToCart } from '@/api/cart'
import { isLogin } from '@/common/auth'
import { addLocalCart } from '@/common/cart-local'
import { buyNow as buyNowFlow } from '@/common/buy-now'
import { price } from '@/common/format'
import { notifyCartChanged } from '@/common/cart-badge'
import StateView from '@/components/state-view.vue'
import CoverBox from '@/components/cover-box.vue'
import BookCard from '@/components/book-card.vue'
import FloorTitle from '@/components/floor-title.vue'
import BreadCrumb from '@/components/bread-crumb.vue'
import WebHeader from '@/components/web-header.vue'
import { isWide } from '@/common/web'
import WebFooter from '@/components/web-footer.vue'

const book = ref(null)
const loading = ref(true)
const adding = ref(false)
const buying = ref(false)
const qty = ref(1)
let bookId = null

/** 评价区块：汇总与列表并发取，失败只让这一块空，不阻断购买流程。
 *  桌面一页多取几条（10 条），星级筛选才有意义；移动端仍是 3 条。 */
const summary = ref({})
const reviews = ref([])
const rvLoading = ref(true)

/** 桌面：评价星级筛选（接口无 rating 查询参数，只在已加载评价内过滤） */
const ratingFilter = ref('all')

/** 桌面：看了又看（同分类热门） */
const related = ref([])

/** 桌面：放大层与加购成功提示 */
const zooming = ref(false)
const addedTip = ref(false)
let addedTimer = null

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getSystemInfoSync().statusBarHeight || 0
} catch (e) {
  statusBarHeight.value = 0
}
const backTop = computed(() => statusBarHeight.value + 8)

const firstChar = computed(() => ((book.value && book.value.title) || '书').charAt(0))

const reviewTotal = computed(() => Number(summary.value.reviewCount) || 0)

/** avgRating 无评价时为 null，不能当 0 展示 */
const avgText = computed(() => {
  const n = Number(summary.value.avgRating)
  return isNaN(n) ? '—' : n.toFixed(1)
})

const roundRating = computed(() => Math.round(Number(summary.value.avgRating) || 0))

function starStr(n) {
  const v = Math.max(0, Math.min(5, Number(n) || 0))
  return '★'.repeat(v) + '☆'.repeat(5 - v)
}

const soldOut = computed(() => !book.value || (book.value.stock || 0) <= 0)

/** 数量上限：库存与单品上限 99 取小 */
const stockLimit = computed(() => {
  if (!book.value) return 1
  return Math.max(Math.min(book.value.stock || 1, 99), 1)
})

/** 库存三态文案：售罄 / 紧张（≤5）/ 充足 */
const stockText = computed(() => {
  if (!book.value) return ''
  const s = book.value.stock || 0
  if (s <= 0) return '暂时售罄'
  if (s <= 5) return `仅剩 ${s} 件`
  return `库存 ${s} 件`
})

const stockTagClass = computed(() => {
  if (!book.value) return ''
  const s = book.value.stock || 0
  if (s <= 0) return 'tag-muted'
  if (s <= 5) return 'tag-warn'
  return 'tag'
})

const salesText = computed(() => {
  const s = Number((book.value && book.value.sales) || 0)
  return s >= 10000 ? (s / 10000).toFixed(1) + '万' : String(s)
})

/** 定价 / 折扣：只有 mock 数据集带 originalPrice，真实书无此字段则不展示 */
const originalPrice = computed(() => {
  const op = Number(book.value && book.value.originalPrice)
  return op && op > Number(book.value.price) ? op : null
})

const discountText = computed(() => {
  if (!originalPrice.value) return ''
  const d = (Number(book.value.price) / originalPrice.value) * 10
  return d >= 9.95 ? '' : d.toFixed(1).replace(/\.0$/, '') + ' 折'
})

/** 面包屑：首页 › 分类 › 当前书名 */
const detailCrumbs = computed(() => {
  const items = [{ name: '首页', url: '/pages/index/index' }]
  if (book.value && book.value.categoryName) {
    items.push({
      name: book.value.categoryName,
      url: '/pages/book/list?categoryId=' + (book.value.categoryId || '') + '&title=' + encodeURIComponent(book.value.categoryName)
    })
  }
  items.push({ name: book.value ? book.value.title : '' })
  return items
})

/** 评价筛选分档及各档在已加载列表中的条数 */
const ratingFilters = computed(() => {
  const tier = (r) => (r >= 4 ? 'good' : r === 3 ? 'mid' : 'bad')
  const cnt = { good: 0, mid: 0, bad: 0 }
  reviews.value.forEach((r) => {
    const t = tier(Number(r.rating) || 0)
    cnt[t] += 1
  })
  return [
    { key: 'all', label: '全部', count: reviews.value.length },
    { key: 'good', label: '好评（4-5 星）', count: cnt.good },
    { key: 'mid', label: '中评（3 星）', count: cnt.mid },
    { key: 'bad', label: '差评（1-2 星）', count: cnt.bad }
  ]
})

const filteredReviews = computed(() => {
  if (ratingFilter.value === 'all') return reviews.value
  return reviews.value.filter((r) => {
    const v = Number(r.rating) || 0
    if (ratingFilter.value === 'good') return v >= 4
    if (ratingFilter.value === 'mid') return v === 3
    return v <= 2
  })
})

async function load(id) {
  loading.value = true
  rvLoading.value = true
  try {
    // 评分单一数据源：图书与评价统一走后端接口，详情页与评价页必然一致；
    // 书未导入真实库（404）或网络失败时，静默回退到 APP 内 mock 数据兜底
    let data = null
    try {
      data = await getBook(id, { silent: true })
    } catch (e) {
      if (!isMockId(id)) throw e
      data = await mockGetBook(id)
    }
    book.value = data || null
    qty.value = 1
    ratingFilter.value = 'all'
    if (!book.value) return
    await Promise.all([loadReviews(id), loadRelated(data)])
  } catch (e) {
    book.value = null
    rvLoading.value = false
  } finally {
    loading.value = false
  }
}

async function loadReviews(id) {
  rvLoading.value = true
  try {
    const [sum, page] = await Promise.all([
      getReviewSummary(id, { silent: true }).catch(() => null),
      pageBookReviews(id, { pageNum: 1, pageSize: isWide.value ? 10 : 3 }, { silent: true }).catch(() => null)
    ])
    // 汇总接口失败（如演示书未导入库）时，用图书自带评分字段兜底
    summary.value = sum || {
      avgRating: book.value.avgRating,
      reviewCount: book.value.reviewCount
    }
    reviews.value = (page && page.list) || []
  } finally {
    rvLoading.value = false
  }
}

/** 看了又看：mock 书查 mock，真实书查后端；同分类取热门，排除自己，最多 5 本 */
async function loadRelated(data) {
  related.value = []
  try {
    const params = {
      categoryId: data.categoryId || undefined,
      sort: 'sales',
      pageNum: 1,
      pageSize: 10
    }
    const res = isMockId(data.id) ? await mockPageBooks(params) : await apiPageBooks(params)
    const list = (res && res.list) || []
    related.value = list.filter((b) => String(b.id) !== String(data.id)).slice(0, 5)
  } catch (e) {
    related.value = []
  }
}

function reload() {
  if (bookId) load(bookId)
}

function goReviews() {
  if (!bookId) return
  uni.navigateTo({ url: '/pages/review/list?bookId=' + bookId })
}

function goDetail(b) {
  // 同页跳转：直接重载数据，避免多层详情页栈
  bookId = b.id
  uni.redirectTo({ url: '/pages/book/detail?id=' + b.id })
}

function changeQty(delta) {
  const next = qty.value + delta
  if (next < 1 || next > stockLimit.value) return
  qty.value = next
}

async function addCart() {
  if (adding.value || soldOut.value) return

  // 未登录不再打断用户：先存本机，登录/注册后自动合并到账号
  if (!isLogin()) {
    addLocalCart(book.value, qty.value)
    notifyCartChanged()
    if (isWide.value) {
      showAddedTip()
    } else {
      uni.showToast({ title: '已加入本机购物车，登录后自动同步', icon: 'none' })
    }
    return
  }

  adding.value = true
  if (!isWide.value) uni.showLoading({ title: '加入中', mask: true })
  try {
    await addToCart({ bookId: book.value.id, quantity: qty.value })
    if (isWide.value) {
      showAddedTip()
    } else {
      uni.showToast({ title: '已加入购物车', icon: 'success' })
    }
    notifyCartChanged()
  } catch (e) {
    // request 封装已统一弹错
  } finally {
    adding.value = false
    if (!isWide.value) uni.hideLoading()
  }
}

/** 桌面加购反馈：面板内显示 4 秒，提供「去购物车」直达 */
function showAddedTip() {
  addedTip.value = true
  if (addedTimer) clearTimeout(addedTimer)
  addedTimer = setTimeout(() => {
    addedTip.value = false
  }, 4000)
}

function goCart() {
  uni.switchTab({ url: '/pages/cart/cart' })
}

/**
 * 立即购买：加购 → 回查该条目的 cartId → 只结算这一条。
 * 具体实现见 common/buy-now.js（低价优选页复用同一份）。
 */
async function buyNow() {
  if (buying.value || soldOut.value) return
  buying.value = true
  try {
    await buyNowFlow(book.value, qty.value)
  } finally {
    buying.value = false
  }
}

/* ---- 封面放大层：点击主图打开，点遮罩/×/Esc 关闭 ---- */
function openZoom() {
  zooming.value = true
  if (typeof document !== 'undefined') {
    document.addEventListener('keydown', onZoomKeydown)
  }
}

function closeZoom() {
  zooming.value = false
  if (typeof document !== 'undefined') {
    document.removeEventListener('keydown', onZoomKeydown)
  }
}

function onZoomKeydown(e) {
  if (e.key === 'Escape') closeZoom()
}

function goBack() {
  uni.navigateBack({
    fail: () => uni.redirectTo({ url: '/pages/index/index' })
  })
}

onUnmounted(() => {
  if (addedTimer) clearTimeout(addedTimer)
  if (zooming.value && typeof document !== 'undefined') {
    document.removeEventListener('keydown', onZoomKeydown)
  }
})

onLoad((options) => {
  bookId = options && options.id
  if (bookId) {
    load(bookId)
  } else {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: 40rpx;
}

/* ---- 自绘返回键 ---- */
.back-btn {
  position: fixed;
  left: $gap-page;
  z-index: 50;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(31, 33, 38, 0.45);
  border-radius: 50%;
}

.back-char {
  font-size: 44rpx;
  line-height: 1;
  color: $color-white;
}

/* ---- 封面 ---- */
.cover-area {
  height: 750rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $bg-card;
}

/* 封面尺寸由这里定，cover-box 内部按 100% 填充并自带渐变兜底 */
.cover-img {
  width: 480rpx;
  height: 600rpx;
  overflow: hidden;
  border-radius: $radius-md;
  background-color: $bg-fill;
}

.cover-img :deep(.cover-char) {
  font-size: 120rpx;
}

.skeleton .cover {
  width: 480rpx;
  height: 600rpx;
  border-radius: $radius-md;
}

/* ---- 信息卡：上移压住封面，形成层次 ---- */
.info-card {
  display: flex;
  flex-direction: column;
  margin-top: -$sp-4;
  position: relative;
  border-radius: $radius-lg $radius-lg 0 0;
}

.price-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.rmb {
  font-size: $fs-lg;
  font-weight: 600;
  color: $price-color;
}

.price-num {
  font-size: $fs-3xl;
  font-weight: 700;
  color: $price-color;
}

.sales {
  margin-left: $sp-2;
  font-size: $fs-sm;
  color: $text-sub;
}

.title {
  margin-top: $sp-2;
  font-size: $fs-2xl;
  font-weight: 600;
  line-height: 1.4;
  color: $text-main;
}

.meta-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: $sp-2;
  margin-top: $sp-2;
}

.divider {
  height: 1rpx;
  margin: $sp-3 0;
  background-color: $border-color;
}

.kv {
  display: flex;
  flex-direction: row;
  padding: $sp-1 0;
}

.k {
  width: 140rpx;
  flex-shrink: 0;
  font-size: $fs-base;
  color: $text-sub;
}

.v {
  font-size: $fs-base;
  color: $text-main;
}

/* ---- 步进器 ---- */
.stepper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.step-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-xl;
  color: $text-main;
  background-color: $bg-fill;
  border-radius: $radius-sm;
}

.step-btn.disabled {
  color: $text-placeholder;
}

.step-num {
  width: 96rpx;
  text-align: center;
  font-size: $fs-lg;
  font-weight: 600;
}

.stock-tip {
  margin-top: $sp-2;
  padding: $sp-2 $sp-3;
  font-size: $fs-sm;
  color: $warning-strong;
  background-color: $warning-weak;
  border-radius: $radius-sm;
}

.desc {
  margin-top: $sp-2;
  font-size: $fs-md;
  color: $text-regular;
  line-height: 1.8;
}

/* ---- 评价（移动端卡片 + 桌面区块共用） ---- */
.rv-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.rv-all {
  flex-shrink: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 64rpx;
  padding-left: $sp-3;
}

.rv-all-text {
  font-size: $fs-base;
  color: $brand;
}

.rv-arrow {
  margin-left: 4rpx;
  font-size: 38rpx;
  color: $text-placeholder;
  line-height: 1;
}

.rv-block {
  margin-top: $sp-3;
}

.rv-sum {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-top: $sp-3;
}

.rv-score {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.1;
  color: $brand;
}

.rv-stars {
  margin-left: $sp-2;
  font-size: $fs-sm;
  letter-spacing: 2rpx;
  color: $warning;
}

.rv-stars.sm {
  font-size: $fs-xs;
}

.rv-cnt {
  margin-left: $sp-2;
  font-size: $fs-sm;
  color: $text-sub;
}

.rv-item {
  margin-top: $sp-3;
  padding-top: $sp-3;
  border-top: 1rpx solid $border-color;
}

.rv-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.rv-name {
  font-size: $fs-base;
  font-weight: 600;
  color: $text-main;
}

.rv-text {
  margin-top: 10rpx;
  font-size: $fs-base;
  line-height: 1.7;
  color: $text-regular;
}

.rv-empty {
  margin-top: $sp-3;
  padding: $sp-4 0;
  text-align: center;
  font-size: $fs-base;
  color: $text-placeholder;
}

/* ---- 底部行动栏（移动端） ---- */
.bar-btn {
  flex: 1;
}

.bar-btn + .bar-btn {
  margin-left: $sp-3;
}

/* ============================================================
   桌面（≥960px）：当当式购买面板
   ------------------------------------------------------------
   与移动端完全不同的结构，类名统一 pd-/ps- 前缀，
   不再复用 .cover-area/.float-bar 的分栏规则，避免互相污染。
   ============================================================ */
@media (min-width: 960px) {
  .page {
    padding: 0;
  }

  .skeleton {
    padding: 24px;
  }

  .pd-wrap {
    padding: 0 0 32px;
  }

  /* 面包屑与楼层标题贴着 1200 卡边，不再自带 24px 内边距 */
  .pd-wrap :deep(.bc) {
    padding-left: 0;
    padding-right: 0;
  }

  .pd-related :deep(.floor-head) {
    margin-left: 0;
    margin-right: 0;
  }

  /* ---- 主购买区：白卡内左右分栏 ---- */
  .pd-main {
    display: flex;
    flex-direction: row;
    align-items: stretch;
    gap: 36px;
    margin-top: 12px;
    padding: 28px 28px 32px;
    background-color: $bg-card;
    border-radius: 10px;
    border: 1rpx solid $border-color;
  }

  /* ---- 左：图集 ---- */
  .pd-gallery {
    flex: 0 0 380px;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .pd-stage {
    position: relative;
    width: 380px;
    height: 440px;
    border-radius: 8px;
    overflow: hidden;
    background-color: $bg-fill;
    cursor: zoom-in;
  }

  .pd-zoom-hint {
    position: absolute;
    right: 10px;
    bottom: 10px;
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 4px;
    padding: 5px 10px;
    font-size: 12px;
    color: $text-regular;
    background-color: rgba(255, 255, 255, 0.88);
    border-radius: 20px;
  }

  .pz-ic {
    font-size: 13px;
    color: $brand;
  }

  .pd-thumbs {
    display: flex;
    flex-direction: row;
    gap: 10px;
    margin-top: 12px;
  }

  .pd-thumb {
    width: 56px;
    height: 70px;
    border-radius: 4px;
    overflow: hidden;
    background-color: $bg-fill;
    border: 2px solid transparent;
    box-sizing: border-box;
  }

  .pd-thumb.on {
    border-color: $brand;
  }

  /* ---- 右：面板 ---- */
  .pd-panel {
    flex: 1;
    min-width: 0;
  }

  .pd-title {
    display: block;
    font-size: 22px;
    font-weight: 700;
    line-height: 1.45;
    color: $text-main;
  }

  .pd-sub {
    display: flex;
    flex-direction: row;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
    font-size: 13px;
    color: $text-sub;
  }

  .pd-sep {
    color: $border-strong;
  }

  .pd-cat {
    margin-left: 4px;
    padding: 1px 8px;
    font-size: 12px;
    color: $brand;
    background-color: $brand-weak;
    border-radius: 3px;
  }

  .pd-rating {
    display: flex;
    flex-direction: row;
    align-items: baseline;
    gap: 8px;
    margin-top: 12px;
  }

  .pd-stars {
    font-size: 16px;
    letter-spacing: 2px;
    color: $warning;
  }

  .pd-score {
    font-size: 14px;
    font-weight: 700;
    color: $brand;
  }

  .pd-rv-cnt {
    font-size: 13px;
    color: $text-sub;
  }

  /* 价格色块 */
  .pd-pricebox {
    margin-top: 16px;
    padding: 16px 20px;
    background-color: $brand-weak;
    border-radius: 8px;
  }

  .pp-row {
    display: flex;
    flex-direction: row;
    align-items: baseline;
  }

  .pp-label {
    flex-shrink: 0;
    width: 42px;
    font-size: 13px;
    color: $text-sub;
  }

  .pp-row .rmb {
    font-size: 16px;
  }

  .pp-price {
    font-size: 34px;
    font-weight: 700;
    line-height: 1;
    color: $price-color;
  }

  .pp-origin {
    margin-left: 12px;
    font-size: 13px;
    color: $text-sub;
    text-decoration: line-through;
  }

  .pp-discount {
    margin-left: 10px;
    padding: 2px 8px;
    font-size: 12px;
    font-weight: 600;
    color: #fff;
    background-color: $brand;
    border-radius: 3px;
  }

  .pp-sub-row {
    margin-top: 12px;
  }

  .pp-stock {
    font-size: 13px;
    color: $text-regular;
  }

  .pp-stock.low {
    color: $warning-strong;
  }

  .pp-stock.out {
    color: $text-placeholder;
  }

  .pp-sales {
    margin-left: auto;
    font-size: 13px;
    color: $text-sub;
  }

  /* 服务承诺 */
  .pd-services {
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px 28px;
    margin-top: 18px;
  }

  .sv {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: $text-regular;
  }

  .sv-ic {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 700;
    color: $brand;
    background-color: $brand-weak;
    border-radius: 50%;
  }

  /* 数量行 */
  .pd-qrow {
    display: flex;
    flex-direction: row;
    align-items: center;
    margin-top: 22px;
  }

  .pq-label {
    flex-shrink: 0;
    width: 42px;
    font-size: 13px;
    color: $text-sub;
  }

  .pd-stepper {
    display: flex;
    flex-direction: row;
    align-items: center;
  }

  .pq-btn {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    color: $text-main;
    background-color: $bg-card;
    border: 1px solid $border-color;
    box-sizing: border-box;
  }

  .pq-btn:first-child {
    border-radius: 4px 0 0 4px;
  }

  .pq-btn:last-child {
    border-radius: 0 4px 4px 0;
  }

  .pq-btn.off {
    color: $text-placeholder;
    background-color: $bg-fill;
    cursor: not-allowed;
  }

  .pq-num {
    width: 56px;
    height: 32px;
    line-height: 30px;
    text-align: center;
    font-size: 14px;
    font-weight: 600;
    background-color: $bg-card;
    border-top: 1px solid $border-color;
    border-bottom: 1px solid $border-color;
    box-sizing: border-box;
  }

  .pq-tip {
    margin-left: 12px;
    font-size: 12px;
    color: $text-placeholder;
  }

  /* 双按钮 */
  .pd-actions {
    display: flex;
    flex-direction: row;
    gap: 16px;
    margin-top: 26px;
  }

  .pd-cart,
  .pd-buy {
    flex: 1;
    height: 50px;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    font-weight: 600;
    border-radius: 4px;
    user-select: none;
  }

  .pd-cart {
    gap: 8px;
    color: $brand;
    background-color: $bg-card;
    border: 2px solid $brand;
  }

  .pd-cart-ic {
    width: 18px;
    height: 18px;
    background-repeat: no-repeat;
    background-position: center;
    background-size: contain;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23FF2832' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Ccircle cx='9' cy='21' r='1'/%3E%3Ccircle cx='20' cy='21' r='1'/%3E%3Cpath d='M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6'/%3E%3C/svg%3E");
  }

  .pd-buy {
    color: #fff;
    background-color: $brand;
  }

  .pd-cart.disabled {
    color: $text-placeholder;
    border-color: $border-strong;
    background-color: $bg-fill;
    cursor: not-allowed;
  }

  .pd-cart.disabled .pd-cart-ic {
    opacity: 0.4;
  }

  .pd-buy.disabled {
    color: $text-placeholder;
    background-color: $border-strong;
    cursor: not-allowed;
  }

  .pd-tip {
    display: inline-flex;
    flex-direction: row;
    align-items: center;
    gap: 10px;
    margin-top: 14px;
    padding: 7px 14px;
    font-size: 13px;
    color: $brand;
    background-color: $brand-weak;
    border-radius: 4px;
  }

  .pd-tip-link {
    font-weight: 600;
    cursor: pointer;
  }

  /* ---- 下方详情/评价区块 ---- */
  .pd-sec-card {
    margin-top: 16px;
    padding: 0 24px 20px;
    background-color: $bg-card;
    border-radius: 10px;
    border: 1rpx solid $border-color;
  }

  .ps-tabbar {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    height: 48px;
    margin: 0 -24px 16px;
    padding: 0 24px;
    border-bottom: 1px solid $border-color;
  }

  .ps-tab {
    position: relative;
    font-size: 16px;
    font-weight: 700;
    color: $text-main;
    line-height: 48px;
  }

  .ps-tab.on {
    color: $brand;
  }

  .ps-tab.on::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 2px;
    background-color: $brand;
  }

  .ps-tab-cnt {
    font-size: 13px;
    font-weight: 400;
    color: $text-sub;
  }

  .ps-kv {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px 32px;
  }

  .ps-k {
    display: flex;
    flex-direction: row;
    font-size: 14px;
  }

  .ps-k .k {
    width: 56px;
  }

  .ps-desc {
    display: block;
    margin-top: 14px;
    font-size: 14px;
    line-height: 1.9;
    color: $text-regular;
  }

  /* 评价区 */
  .rv-sum {
    margin-top: 0;
  }

  .rv-score {
    font-size: 22px;
  }

  .rv-stars {
    font-size: 15px;
  }

  .rv-filter {
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 10px;
    margin: 16px 0 4px;
  }

  .rvf {
    padding: 4px 14px;
    font-size: 12px;
    color: $text-regular;
    background-color: $bg-card;
    border: 1px solid $border-color;
    border-radius: 20px;
    cursor: pointer;
  }

  .rvf-cnt {
    color: $text-sub;
  }

  .rvf.on {
    color: #fff;
    font-weight: 600;
    background-color: $brand;
    border-color: $brand;
  }

  .rvf.on .rvf-cnt {
    color: rgba(255, 255, 255, 0.85);
  }

  .rv-item {
    margin-top: 14px;
    padding-top: 14px;
  }

  .rv-text {
    margin-top: 6px;
    font-size: 14px;
  }

  .rv-more {
    display: inline-block;
    margin-top: 16px;
    font-size: 13px;
    color: $brand;
    cursor: pointer;
  }

  /* ---- 看了又看 ---- */
  .pd-related {
    margin-top: 28px;
  }

  .pd-related .grid-books {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 20px;
    margin-top: 12px;
  }

  .pd-related .grid-cell {
    width: 100%;
    min-width: 0;
  }

  /* ---- 放大层 ---- */
  .pd-zoom-mask {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 200;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: rgba(0, 0, 0, 0.72);
  }

  .pd-zoom-box {
    width: 460px;
    height: 580px;
    border-radius: 8px;
    overflow: hidden;
    background-color: $bg-fill;
  }

  .pd-zoom-close {
    position: absolute;
    top: 24px;
    right: 32px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    line-height: 1;
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: 50%;
    cursor: pointer;
  }
}

@media (hover: hover) and (pointer: fine) {
  .pd-cart:not(.disabled):hover {
    background-color: $brand-weak;
  }

  .pd-buy:not(.disabled):hover {
    background-color: $brand-dark;
  }

  .rvf:hover {
    border-color: $brand;
  }

  .pd-rv-cnt:hover,
  .pd-rating:hover .pd-rv-cnt {
    color: $brand;
  }
}
</style>
