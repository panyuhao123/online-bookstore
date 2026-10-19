<template>
  <view class="page">
    <page-nav title="分类导航" />

    <!-- 桌面面包屑：告诉用户当前在哪个分类里，并给出回到上一级的入口 -->
    <bread-crumb v-if="isWide" :items="crumbs" />

    <!-- ① 搜索栏：始终只检索「当前一级分类」内的图书 -->
    <view class="search-bar">
      <view class="search-box">
        <text class="search-icon">⌕</text>
        <input
          class="input"
          v-model="keyword"
          :placeholder="searchPlaceholder"
          placeholder-class="ph"
          confirm-type="search"
          @input="onInput"
          @confirm="onSearch"
        />
        <text v-if="keyword" class="clear" @tap="clearSearch">×</text>
      </view>
    </view>

    <!-- ② 左目录（一级分类）+ ③ 右内容（二级子分类 + 热门图书） -->
    <view class="body" :style="{ paddingBottom: tabBarHeight + 'px' }">
      <!-- 移动端：窄侧栏一级分类 -->
      <scroll-view v-if="!isWide" class="side" scroll-y :show-scrollbar="false">
        <view
          v-for="c in categories"
          :key="c.id"
          class="side-item"
          :class="{ on: categoryId === c.id }"
          hover-class="hover-opacity"
          @tap="onSelect(c.id)"
        >
          <text class="side-name">{{ c.name }}</text>
        </view>
        <view v-if="!categories.length" class="side-loading">
          <view v-for="n in 6" :key="n" class="sk sk-anim side-sk" />
        </view>
      </scroll-view>

      <!-- 桌面：分类树（可展开二级）+ 筛选面板 -->
      <scroll-view v-else class="web-side" scroll-y :show-scrollbar="false">
        <web-category-tree
          :active-id="categoryId"
          :active-sub-id="subCategoryId"
          @select="onTreeSelect"
        />
        <web-filter-panel
          :model="filter"
          :publishers="publishers"
          @change="onFilterChange"
          @clear="onFilterClear"
        />
      </scroll-view>

      <view class="main">
        <scroll-view
          class="main-scroll"
          scroll-y
          :show-scrollbar="false"
          refresher-enabled
          :refresher-triggered="refreshing"
          refresher-background="#F4F5F7"
          @refresherrefresh="onRefresh"
          @scrolltolower="loadMore"
        >
          <!-- 桌面：筛选结果条，实时反映当前生效的条件，并支持单个条件一键移除 -->
          <view v-if="isWide" class="filter-bar">
            <text class="fb-total">共 {{ total }} 本</text>
            <text class="fb-sep">｜</text>
            <text class="fb-label">已选：</text>
            <text
              v-for="c in activeChips"
              :key="c.key"
              class="fb-chip"
              @tap="removeChip(c)"
            >
              {{ c.text }} ×
            </text>
            <text v-if="!activeChips.length" class="fb-none">未设置筛选条件</text>
          </view>

          <!-- 二级子分类宫格：整块即入口（不再用分类名首字做视觉块），点击按子类过滤 -->
          <view v-if="!isWide && !keyword && subCategories.length" class="sub-grid">
            <view
              class="sub-cell"
              :class="{ on: !subCategoryId }"
              hover-class="hover-scale"
              @tap="onSubTap(null)"
            >
              <text class="sub-name ellipsis-2">全部</text>
            </view>
            <view
              v-for="s in subCategories"
              :key="s.id"
              class="sub-cell"
              :class="{ on: subCategoryId === s.id }"
              hover-class="hover-scale"
              @tap="onSubTap(s.id)"
            >
              <text class="sub-name ellipsis-2">{{ s.name }}</text>
            </view>
          </view>

          <view class="sec-head">
            <text class="sec-title">{{ sectionTitle }}</text>
            <text v-if="!keyword" class="sec-sub">共 {{ total }} 本</text>
          </view>

          <!-- 排序：桌面用横向按钮组（文案写全），移动端仍是胶囊横滑 -->
          <view v-if="isWide" class="sort-web">
            <view
              v-for="s in sortOptions"
              :key="s.value"
              class="sort-web-item"
              :class="{ on: sort === s.value }"
              @tap="onSortTap(s.value)"
            >
              {{ s.full || s.label }}
            </view>
          </view>

          <scroll-view v-else class="sort-row" scroll-x :show-scrollbar="false">
            <view class="sort-inner">
              <view
                v-for="s in sortOptions"
                :key="s.value"
                class="mini-chip"
                :class="{ on: sort === s.value }"
                hover-class="hover-opacity"
                @tap="onSortTap(s.value)"
              >
                {{ s.label }}
              </view>
            </view>
          </scroll-view>

          <!-- 首屏骨架 -->
          <view v-if="firstState === 'loading'" class="grid-books">
            <view v-for="n in 4" :key="n" class="grid-cell">
              <view class="sk sk-anim sk-cover" />
              <view class="sk sk-anim sk-line" style="width: 90%" />
              <view class="sk sk-anim sk-line" style="width: 55%" />
            </view>
          </view>

          <state-view
            v-else-if="firstState === 'error'"
            type="error"
            text="图书加载失败"
            sub-text="请检查网络后重试"
            btn-text="重新加载"
            @retry="reload"
          />

          <state-view
            v-else-if="!displayList.length"
            type="empty"
            :text="keyword ? `“${keyword}”在本分类没有找到` : emptyText"
            :sub-text="keyword ? '换个关键词，或看看其他分类' : '试试放宽筛选条件，或看看其他分类'"
            :btn-text="keyword ? '清除搜索' : hasFilter ? '清空筛选' : ''"
            @retry="onEmptyAction"
          />

          <template v-else>
            <view class="grid-books">
              <view v-for="b in displayList" :key="b.id" class="grid-cell">
                <book-card :book="b" @tap="goDetail(b)" />
              </view>
            </view>

            <!-- 桌面分页：翻页比无限滚动更可控，第一页/末页的翻页按钮置灰 -->
            <view v-if="isWide" class="pager">
              <view class="pg-btn" :class="{ off: pageNum <= 1 }" @tap="goPage(pageNum - 1)">上一页</view>
              <view
                v-for="p in pageList"
                :key="p"
                class="pg-num"
                :class="{ on: p === pageNum }"
                @tap="goPage(p)"
              >
                {{ p }}
              </view>
              <view class="pg-btn" :class="{ off: pageNum >= pages }" @tap="goPage(pageNum + 1)">下一页</view>
            </view>

            <!-- 移动端：上拉加载 -->
            <view v-else class="load-more">
              <text v-if="loadingMore">加载中...</text>
              <text v-else-if="finished">— 没有更多了 —</text>
            </view>
          </template>
        </scroll-view>
      </view>
    </view>

    <tab-bar active="category" />
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 分类导航：左一级目录 + 右「二级子分类 + 热门图书」双栏结构。
 *
 * 与旧版的差别：右侧顶部补了二级子分类宫格（点击即按子类过滤），
 * 并把「搜索」从跳转一个搜索页改成在当前分类内就地过滤 —— 分类页的搜索意图
 * 绝大多数是「在这个类目里找」，跳走再回来反而多两步。
 *
 * 桌面（≥960px）在移动端结构之外多出三块，靠 isWide 分支而不是复制页面：
 *   ① 面包屑；② 左侧分类树（可展开二级）+ 筛选面板；③ 分页器与筛选结果条。
 * 移动端逻辑（上拉加载、侧栏切换）完全不变。
 *
 * 下拉刷新用 scroll-view 的自定义刷新而不是页面级原生刷新：
 * 本页是 custom 导航，原生刷新圈会出现在自绘导航之上，把导航盖住。
 *
 * 筛选能力的分界（写清楚避免后来人误以为都是接口能力）：
 *   分类 / 子分类 / 关键词 / 价格区间 / 排序 → 查询接口支持，按条件重新取数；
 *   评分 / 出版社 → 接口与表结构都没有对应字段，在已取到的结果集里前端过滤。
 */
import { ref, reactive, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCategories, pageBooks } from '@/common/mock'
import { tabBarHeight } from '@/common/layout'
import PageNav from '@/components/page-nav.vue'
import TabBar from '@/components/tab-bar.vue'
import BookCard from '@/components/book-card.vue'
import StateView from '@/components/state-view.vue'
import WebFooter from '@/components/web-footer.vue'
import BreadCrumb from '@/components/bread-crumb.vue'
import WebCategoryTree from '@/components/web-category-tree.vue'
import WebFilterPanel from '@/components/web-filter-panel.vue'
import { isWide } from '@/common/web'

/** 演示数据集是本地数据，一次取全量再在前端分页，翻页/筛选都不必反复请求 */
const FETCH_ALL = 500
const PAGE_SIZE = 10 // 移动端上拉每页
const WEB_PAGE_SIZE = 12 // 桌面每页（4 列 × 3 行）

/** 与后端 BookQuery.sort 白名单一致；full 是桌面排序条的完整文案 */
const sortOptions = [
  { label: '综合', full: '综合排序', value: 'default' },
  { label: '销量', full: '销量优先', value: 'sales' },
  { label: '价格从低到高', full: '价格从低到高', value: 'price_asc' },
  { label: '价格从高到低', full: '价格从高到低', value: 'price_desc' },
  { label: '评分最高', full: '评分最高', value: 'rating' }
]

const categories = ref([])
const categoryId = ref(null)
const subCategoryId = ref(null)
const keyword = ref('')
const sort = ref('default')

/** 筛选条件：价格走接口，评分/出版社走前端过滤 */
const filter = reactive({ minPrice: null, maxPrice: null, minRating: 0, publisher: '' })

const allBooks = ref([])
const firstState = ref('loading') // loading / done / error
const loadingMore = ref(false)
const refreshing = ref(false)

const pageNum = ref(1) // 桌面当前页
const loaded = ref(PAGE_SIZE) // 移动端已加载条数

let navigating = false
let inputTimer = null

const currentCategory = computed(() => categories.value.find((c) => c.id === categoryId.value) || null)
const subCategories = computed(() => (currentCategory.value ? currentCategory.value.children : []))
const searchPlaceholder = computed(() =>
  currentCategory.value ? `在「${currentCategory.value.name}」中搜索` : '搜索书名 / 作者'
)

/** 出版社选项：从当前分类的真实图书里统计，按出现次数排序，不写死名单 */
const publishers = computed(() => {
  const map = new Map()
  allBooks.value.forEach((b) => {
    const p = (b.publisher || '').trim()
    if (p) map.set(p, (map.get(p) || 0) + 1)
  })
  return [...map.entries()].sort((a, b) => b[1] - a[1]).map((e) => e[0]).slice(0, 12)
})

/** 评分 / 出版社在结果集里过滤（接口无对应参数） */
const filtered = computed(() =>
  allBooks.value.filter((b) => {
    if (Number(filter.minRating || 0) > 0 && Number(b.avgRating || 0) < Number(filter.minRating)) {
      return false
    }
    if (filter.publisher && (b.publisher || '') !== filter.publisher) return false
    return true
  })
)

const total = computed(() => filtered.value.length)

/** 桌面按页切，移动端按已加载条数切 —— 两种分页习惯各自对应 */
const displayList = computed(() => {
  if (isWide.value) {
    const start = (pageNum.value - 1) * WEB_PAGE_SIZE
    return filtered.value.slice(start, start + WEB_PAGE_SIZE)
  }
  return filtered.value.slice(0, loaded.value)
})

const pages = computed(() => Math.max(1, Math.ceil(total.value / WEB_PAGE_SIZE)))

/** 页码列表：最多显示 5 个，当前页尽量居中 */
const pageList = computed(() => {
  const p = pages.value
  if (p <= 5) return Array.from({ length: p }, (_, i) => i + 1)
  let start = pageNum.value - 2
  if (start < 1) start = 1
  if (start + 4 > p) start = p - 4
  return Array.from({ length: 5 }, (_, i) => start + i)
})

const finished = computed(() => !isWide.value && loaded.value >= total.value)

const hasFilter = computed(
  () => filter.minPrice != null || filter.maxPrice != null || Number(filter.minRating) > 0 || !!filter.publisher
)

/** 筛选结果条上的条件 chips，点一下即移除该条件 */
const activeChips = computed(() => {
  const chips = []
  if (currentCategory.value) {
    const sub = subCategories.value.find((s) => s.id === subCategoryId.value)
    chips.push({
      key: 'category',
      text: sub ? `${currentCategory.value.name} / ${sub.name}` : currentCategory.value.name
    })
  }
  if (filter.minPrice != null || filter.maxPrice != null) {
    const lo = filter.minPrice == null ? '' : filter.minPrice
    const hi = filter.maxPrice == null ? '以上' : filter.maxPrice
    chips.push({ key: 'price', text: filter.maxPrice == null ? `价格 ${lo} 元以上` : `价格 ${lo}-${hi}` })
  }
  if (Number(filter.minRating) > 0) chips.push({ key: 'rating', text: `${filter.minRating} 星及以上` })
  if (filter.publisher) chips.push({ key: 'publisher', text: filter.publisher })
  return chips
})

const crumbs = computed(() => {
  const items = [{ name: '首页', url: '/pages/index/index' }]
  if (currentCategory.value) {
    items.push({ name: currentCategory.value.name, url: '/pages/category/category?categoryId=' + categoryId.value })
  }
  const sub = subCategories.value.find((s) => s.id === subCategoryId.value)
  if (sub) items.push({ name: sub.name })
  return items
})

const sectionTitle = computed(() => {
  if (keyword.value) return `“${keyword.value}” 的搜索结果`
  const sub = subCategories.value.find((s) => s.id === subCategoryId.value)
  if (sub) return sub.name
  return currentCategory.value ? currentCategory.value.name + ' · 热门图书' : '热门图书'
})

const emptyText = computed(() => (hasFilter.value ? '没有符合条件的图书' : '该分类暂无图书'))

async function loadCategories() {
  try {
    categories.value = await getCategories()
    // 外部带 categoryId 进来时（首页分类树跳转），以传入值为准
    if (categories.value.length && categoryId.value === null) {
      categoryId.value = categories.value[0].id
      await query(true)
    } else if (categoryId.value !== null) {
      await query(true)
    }
  } catch (e) {
    firstState.value = 'error'
  }
}

/** 取数：分类 / 子分类 / 关键词 / 价格 / 排序 都交给接口，评分与出版社在前端过滤 */
async function query(reset) {
  if (reset) {
    pageNum.value = 1
    loaded.value = PAGE_SIZE
  }
  firstState.value = 'loading'
  try {
    const params = {
      categoryId: categoryId.value,
      pageNum: 1,
      pageSize: FETCH_ALL,
      sort: sort.value
    }
    if (subCategoryId.value) params.subCategoryId = subCategoryId.value
    if (keyword.value) params.keyword = keyword.value
    if (filter.minPrice != null) params.minPrice = filter.minPrice
    if (filter.maxPrice != null) params.maxPrice = filter.maxPrice

    const page = await pageBooks(params)
    allBooks.value = page.list || []
    firstState.value = 'done'
  } catch (e) {
    allBooks.value = []
    firstState.value = 'error'
  }
}

function onSelect(id) {
  if (categoryId.value === id) return
  categoryId.value = id
  subCategoryId.value = null
  query(true)
}

function onSubTap(id) {
  if (subCategoryId.value === id) return
  subCategoryId.value = id
  query(true)
}

/** 桌面分类树：一级直接切换当前分类，二级切到该子类（都不跳页） */
function onTreeSelect(sel) {
  if (!sel) return
  const changed = sel.categoryId !== categoryId.value || sel.subCategoryId !== subCategoryId.value
  if (!changed) return
  categoryId.value = sel.categoryId
  subCategoryId.value = sel.subCategoryId || null
  query(true)
}

function onSortTap(value) {
  if (sort.value === value) return
  sort.value = value
  query(true)
}

function onFilterChange(next) {
  filter.minPrice = next.minPrice
  filter.maxPrice = next.maxPrice
  filter.minRating = next.minRating
  filter.publisher = next.publisher
  query(true)
}

function onFilterClear() {
  filter.minPrice = null
  filter.maxPrice = null
  filter.minRating = 0
  filter.publisher = ''
  query(true)
}

/** 移除单个条件：分类不可移除（移除就不知道在看哪个分类了），其余各自清零 */
function removeChip(chip) {
  if (chip.key === 'price') {
    filter.minPrice = null
    filter.maxPrice = null
  } else if (chip.key === 'rating') {
    filter.minRating = 0
  } else if (chip.key === 'publisher') {
    filter.publisher = ''
  } else {
    return
  }
  query(true)
}

function goPage(p) {
  if (p < 1 || p > pages.value || p === pageNum.value) return
  pageNum.value = p
  // 翻页后回到列表顶部，否则会停在上一页的滚动位置
  uni.pageScrollTo({ scrollTop: 0, duration: 0, fail: () => {} })
}

function loadMore() {
  if (isWide.value) return
  if (loadingMore.value || finished.value) return
  loadingMore.value = true
  loaded.value = Math.min(loaded.value + PAGE_SIZE, total.value)
  setTimeout(() => {
    loadingMore.value = false
  }, 200)
}

/** 输入防抖：边打字边打接口会把演示数据也拖出加载态，体验反而更差 */
function onInput() {
  clearTimeout(inputTimer)
  inputTimer = setTimeout(() => query(true), 320)
}

function onSearch() {
  clearTimeout(inputTimer)
  query(true)
}

function clearSearch() {
  clearTimeout(inputTimer)
  keyword.value = ''
  query(true)
}

/** 空态按钮：搜索无结果清关键词，筛选无结果清筛选 */
function onEmptyAction() {
  if (keyword.value) {
    clearSearch()
    return
  }
  if (hasFilter.value) onFilterClear()
}

async function onRefresh() {
  refreshing.value = true
  await query(true)
  refreshing.value = false
}

function reload() {
  loadCategories()
  query(true)
}

function goDetail(book) {
  if (navigating) return
  navigating = true
  setTimeout(() => {
    navigating = false
  }, 800)
  uni.navigateTo({ url: '/pages/book/detail?id=' + book.id })
}

onLoad((options) => {
  // 首页分类树会把选中的分类带过来，落地即定位到该分类（含二级）
  const q = options || {}
  if (q.categoryId) categoryId.value = Number(q.categoryId)
  if (q.subCategoryId) subCategoryId.value = Number(q.subCategoryId)
  loadCategories()
})

onUnmounted(() => {
  clearTimeout(inputTimer)
})
</script>

<style lang="scss" scoped>
/* 页面高度 100dvh（dvh 会随移动端浏览器地址栏伸缩，避免底部被顶出可视区；
   不支持的浏览器回落到上一行 100vh）+ box-sizing:border-box，配合自绘导航的占位块，
   下方 .body 用 flex 撑满剩余空间；子滚动区不再自己算视口高度 */
.page {
  height: 100vh;
  height: 100dvh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ---- 搜索栏 ---- */
.search-bar {
  flex-shrink: 0;
  padding: 12rpx $gap-page;
  background-color: $bg-card;
  border-bottom: 1rpx solid $border-color;
}

.search-box {
  height: 64rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 $sp-3;
  background-color: $bg-page;
  border-radius: $radius-pill;
}

.search-icon {
  margin-right: 10rpx;
  font-size: 28rpx;
  color: $brand;
}

.clear {
  flex-shrink: 0;
  width: 44rpx;
  text-align: center;
  font-size: 32rpx;
  color: $text-placeholder;
}

/* ---- 主体 ---- */
.body {
  flex: 1;
  height: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: row;
}

/* ---- 左侧一级分类（移动端） ---- */
.side {
  width: 176rpx;
  flex-shrink: 0;
  height: 100%;
  background-color: $bg-page;
}

.side-item {
  padding: 32rpx 12rpx;
  text-align: center;
  border-left: 8rpx solid transparent;
}

.side-name {
  font-size: $fs-base;
  color: $text-regular;
}

/* 选中态：弱红底 + 左侧品牌色竖条 + 加粗，三重信号确保当前分类一眼可辨 */
.side-item.on {
  background-color: $brand-weak;
  border-left-color: $brand;
  border-radius: 0 $radius-md $radius-md 0;
}

.side-item.on .side-name {
  color: $brand;
  font-weight: 700;
}

.side-loading {
  padding: 32rpx 16rpx;
}

.side-sk {
  height: 28rpx;
  margin-bottom: 48rpx;
}

/* ---- 右侧 ---- */
.main {
  flex: 1;
  min-width: 0;
  height: 100%;
  background-color: $bg-page;
}

.main-scroll {
  height: 100%;
}

/* 二级子分类宫格（3 列）：整块卡片即入口 */
.sub-grid {
  padding: $sp-3 $sp-3 $sp-1;
  background-color: $bg-card;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: $sp-3 $sp-2;
}

.sub-cell {
  width: calc((100% - #{$sp-2} * 2) / 3);
  /* 触控最小 88rpx；内容多时由文字撑开，不固定高度 */
  min-height: 88rpx;
  box-sizing: border-box;
  padding: $sp-2 $sp-1;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $bg-fill;
  border: 2rpx solid transparent;
  /* 与全局卡片圆角一致 */
  border-radius: $radius-lg;
}

/* 选中态：弱红底 + 品牌色描边 + 文字变色加粗 */
.sub-cell.on {
  background-color: $brand-weak;
  border-color: $brand;
}

.sub-name {
  /* 26rpx 满足正文下限；长名称最多两行，超出省略，不会溢出卡片 */
  font-size: $fs-base;
  line-height: 1.3;
  text-align: center;
  color: $text-regular;
}

.sub-cell.on .sub-name {
  color: $brand;
  font-weight: 600;
}

/* 区块标题 */
.sec-head {
  padding: $sp-3 $sp-3 $sp-2;
  background-color: $bg-page;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.sec-title {
  flex: 1;
  min-width: 0;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-main;
}

.sec-sub {
  flex-shrink: 0;
  font-size: $fs-xs;
  color: $text-sub;
}

/* 排序条 */
.sort-row {
  white-space: nowrap;
  padding-bottom: $sp-2;
  background-color: $bg-page;
}

.sort-inner {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  padding: 0 $sp-3;
}

.mini-chip {
  flex-shrink: 0;
  height: 52rpx;
  padding: 0 20rpx;
  margin-right: $sp-1;
  display: flex;
  align-items: center;
  font-size: $fs-sm;
  color: $text-regular;
  background-color: $bg-card;
  border-radius: $radius-pill;
}

.mini-chip.on {
  color: $color-white;
  font-weight: 600;
  background-color: $brand;
}

.grid-books {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  padding: 0 $sp-3;
  gap: $gap-item;
}

.grid-cell {
  width: calc((100% - #{$gap-item}) / 2);
  display: flex;
}

.sk-cover {
  height: 0;
  padding-top: 133.33%;
  border-radius: $radius-lg;
}

.sk-line {
  height: 26rpx;
  margin-top: 14rpx;
}

.load-more {
  padding: $sp-4 0 $sp-6;
  text-align: center;
  font-size: $fs-sm;
  color: $text-sub;
}

/* ============================================================
   桌面：筛选结果条 / 排序按钮组 / 分页 / 左侧栏
   ============================================================ */
@media (min-width: 960px) {
  /* 桌面改「整页文档流」：不再用 100dvh + 内部 scroll-view 的 App 壳式滚动，
     避免高度链上 scroll-view 塌陷（侧栏只剩 144px）和 footer 被裁切，
     且与其他页面的居中/滚动条表现保持一致。 */
  .page {
    height: auto;
    display: block;
    overflow: visible;
  }

  .body {
    height: auto;
    align-items: flex-start;
    padding-bottom: 24px;
  }

  /* 侧栏随页面滚动吸顶；高度按视口算而不是靠 % 高度链 */
  .web-side {
    flex: 0 0 220px;
    align-self: flex-start;
    position: sticky;
    top: 128px;
    max-height: calc(100vh - 144px);
    padding: 16px 16px 24px;
    box-sizing: border-box;
    background-color: $bg-page;
  }

  .main {
    height: auto;
  }

  /* scroll-view 宿主与内部容器都放开高度/定位，让内容随文档自然撑开，
     滚动交给页面；桌面已有分页器，不依赖 scrolltolower */
  .main-scroll {
    height: auto !important;
  }

  .main-scroll :deep(.uni-scroll-view),
  .main-scroll :deep(.uni-scroll-view-content) {
    height: auto !important;
    position: static !important;
    overflow: visible !important;
  }

  /* 搜索栏：与 1200 内容区的 24px 边距对齐 */
  .search-bar {
    padding-left: 24px;
    padding-right: 24px;
  }

  /* 移动端 .grid-books 的左右内边距是给窄屏留的，桌面与筛选/排序条共用 24px 边距 */
  .grid-books {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 20px;
    padding: 0 !important;
    margin: 0 24px;
  }

  .grid-cell {
    width: 100%;
    min-width: 0;
  }

  .load-more {
    margin: 0 24px;
  }

  .filter-bar {
    display: flex;
    flex-direction: row;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    margin: 16px 24px 0;
    padding: 10px 12px;
    background-color: $brand-weak;
    border-radius: 6px;
    font-size: 13px;
    color: $text-regular;
  }

  .fb-total {
    color: $brand;
    font-weight: 600;
  }

  .fb-sep {
    color: $border-strong;
  }

  .fb-label {
    color: $text-regular;
  }

  .fb-chip {
    padding: 3px 9px;
    background-color: $bg-card;
    border: 1px solid $brand;
    border-radius: 3px;
    font-size: 12px;
    color: $brand;
  }

  .fb-none {
    font-size: 12px;
    color: $text-placeholder;
  }

  /* 区块标题行与网格共用 24px 边距 */
  .sec-head {
    padding: 12px 24px 8px;
  }

  /* 排序：文案写全（价格从低到高），选中态是底色 + 边框双重信号 */
  .sort-web {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    padding: 0 24px 12px;
    background-color: $bg-page;
  }

  .sort-web-item {
    padding: 5px 14px;
    font-size: 13px;
    color: $text-regular;
    background-color: $bg-card;
    border: 1px solid $border-color;
    border-radius: 3px;
  }

  .sort-web-item.on {
    color: $color-white;
    font-weight: 600;
    background-color: $brand;
    border-color: $brand;
  }

  /* 分页：居中，禁用态用置灰而不是隐藏，让用户知道有这一页但到头了 */
  .pager {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 20px 0 8px;
  }

  .pg-btn,
  .pg-num {
    min-width: 32px;
    height: 30px;
    padding: 0 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: $text-regular;
    background-color: $bg-card;
    border: 1px solid $border-color;
    border-radius: 3px;
  }

  .pg-num.on {
    color: $color-white;
    font-weight: 600;
    background-color: $brand;
    border-color: $brand;
  }

  /* 第一页的「上一页」、末页的「下一页」置灰且不可点 */
  .pg-btn.off {
    color: $text-placeholder;
    background-color: $bg-fill;
    border-color: $border-color;
    pointer-events: none;
  }
}

@media (hover: hover) and (pointer: fine) {
  .sort-web-item,
  .pg-btn,
  .pg-num,
  .fb-chip {
    cursor: pointer;
  }

  .sort-web-item:hover {
    color: $brand;
    border-color: $brand;
  }

  .sort-web-item.on:hover {
    color: $color-white;
    background-color: $brand-dark;
  }

  .pg-num:hover {
    color: $brand;
    border-color: $brand;
  }

  .pg-btn:hover {
    color: $brand;
    border-color: $brand;
  }

  .fb-chip:hover {
    background-color: $brand-weak;
  }
}
</style>
