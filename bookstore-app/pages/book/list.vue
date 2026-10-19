<template>
  <view class="page">
    <!-- ① 吸顶：可编辑搜索框（回显关键词，回车即搜） -->
    <nav-bar
      :keyword="keyword"
      :editable="true"
      :autofocus="autofocus"
      @search="onSearch"
    />

    <!-- ② 排序条 -->
    <view class="sort-bar" :style="{ top: sortTop + 'px' }">
      <view
        v-for="s in sortOptions"
        :key="s.value"
        class="sort-item"
        :class="{ on: sort === s.value }"
        hover-class="hover-opacity"
        @tap="onSortTap(s.value)"
      >
        {{ s.label }}
      </view>
    </view>
    <view :style="{ height: sortTop + 44 + 'px' }" />

    <!-- 桌面面包屑：搜索/榜单落地页告诉用户入口位置 -->
    <bread-crumb v-if="isWide" :items="crumbs" />

    <!-- ③ 标题：说明当前这批货的来源（搜索词 / 分类 / 榜单） -->
    <floor-title :title="pageTitle" :sub="rankMode ? '按销量排序' : ''" :show-more="false" />

    <!-- ④ 双列图书网格 -->
    <view v-if="books.length" class="grid-books">
      <view v-for="(b, i) in books" :key="b.id" class="grid-cell">
        <book-card :book="b" :rank="rankMode ? i + 1 : ''" @tap="goDetail(b)" />
      </view>
    </view>

    <state-view
      :type="stateType"
      :text="stateText"
      :sub-text="stateSubText"
      btn-text="重新加载"
      @retry="reload"
    />

    <view v-if="loadingMore" class="load-more">加载中...</view>
    <view v-else-if="finished && books.length" class="load-more">— 没有更多了 —</view>

    <view class="tabbar-holder" />
    <tab-bar :active="rankMode ? 'read' : ''" />
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 图书列表页：搜索结果 / 分类 / 书单 共用。
 *
 * 入参（onLoad options）：
 *   keyword    搜索词
 *   categoryId 分类
 *   sort       排序（default / sales / price_asc / price_desc）
 *   title      标题回显
 *   channel    read = 书单（强制按销量并展示排名序号）
 *   focus      1 = 落地即聚焦搜索框（分类页「搜索」入口用）
 *
 * 之所以单独拆这一页：首页改造成楼层式之后，不再承载搜索结果；
 * 而「书单」作为底部第 5 个入口，也需要一个能装排行榜的落地页。
 */
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { pageBooks } from '@/api/book'
import NavBar from '@/components/nav-bar.vue'
import TabBar from '@/components/tab-bar.vue'
import FloorTitle from '@/components/floor-title.vue'
import BookCard from '@/components/book-card.vue'
import StateView from '@/components/state-view.vue'
import { navHeight, takeSearchKeyword, isWide } from '@/common/web'
import WebFooter from '@/components/web-footer.vue'
import BreadCrumb from '@/components/bread-crumb.vue'

const PAGE_SIZE = 10

const sortOptions = [
  { value: 'default', label: '综合' },
  { value: 'sales', label: '销量' },
  { value: 'price_asc', label: '价格↑' },
  { value: 'price_desc', label: '价格↓' }
]

const keyword = ref('')
const categoryId = ref('')
const title = ref('')
const sort = ref('default')
const autofocus = ref(false)
/** 榜单模式：按销量排序并在卡片左上角显示名次 */
const rankMode = ref(false)

const books = ref([])
const pageNum = ref(1)
const total = ref(0)
const firstState = ref('loading') // loading / done / error
const loadingMore = ref(false)

const stateType = computed(() => {
  if (firstState.value === 'loading') return 'loading'
  if (firstState.value === 'error') return 'error'
  if (!books.value.length) return 'empty'
  return 'none'
})

const stateText = computed(() => (keyword.value ? `没有找到“${keyword.value}”相关图书` : '这里还没有图书'))
const stateSubText = computed(() => (keyword.value ? '换个关键词，或看看其他分类' : '去首页逛逛新书'))

const finished = computed(() => books.value.length >= total.value && total.value > 0)

const sortTop = navHeight

const pageTitle = computed(() => {
  if (rankMode.value) return '书单'
  if (keyword.value) return `“${keyword.value}” 的搜索结果`
  if (title.value) return title.value
  return '全部图书'
})

/** 面包屑：首页 ›（书单 / 搜索词 / 分类标题） */
const crumbs = computed(() => [
  { name: '首页', url: '/pages/index/index' },
  { name: pageTitle.value }
])

async function query(mode = 'first') {
  if (mode === 'first') {
    firstState.value = 'loading'
    pageNum.value = 1
  } else {
    if (loadingMore.value || finished.value) return
    loadingMore.value = true
  }
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      sort: sort.value
    }
    // 只放有值的参数：undefined 进 query 串会被后端当成非法 BigDecimal
    if (keyword.value) params.keyword = keyword.value
    if (categoryId.value) params.categoryId = categoryId.value

    const page = await pageBooks(params)
    books.value = mode === 'first' ? page.list || [] : books.value.concat(page.list || [])
    total.value = page.total || 0
    firstState.value = 'done'
    if (!finished.value) pageNum.value += 1
  } catch (e) {
    if (mode === 'first') firstState.value = 'error'
  } finally {
    loadingMore.value = false
  }
}

function reload() {
  query('first')
}

function onSearch(kw) {
  keyword.value = kw || ''
  query('first')
}

function onSortTap(value) {
  if (sort.value === value) return
  sort.value = value
  // 榜单模式下手动切排序会破坏「按销量排名」的语义，这里保持一致：切排序即退出榜单展示
  if (rankMode.value && value !== 'sales') rankMode.value = false
  query('first')
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

onLoad((options) => {
  const q = options || {}
  keyword.value = q.keyword ? decodeURIComponent(q.keyword) : ''
  // 桌面顶栏搜索把关键词放在本地存储里（URL query 会被 uni 路由二次编码，见 common/web.js）
  if (!keyword.value) keyword.value = takeSearchKeyword()
  categoryId.value = q.categoryId || ''
  title.value = q.title ? decodeURIComponent(q.title) : ''
  autofocus.value = q.focus === '1'
  if (q.channel === 'read') {
    rankMode.value = true
    sort.value = 'sales'
  } else if (q.sort) {
    sort.value = q.sort
  }
  query('first')
})

onReachBottom(() => {
  query('more')
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
}

.sort-bar {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 45;
  display: flex;
  flex-direction: row;
  padding: 0 $gap-page;
  background-color: $bg-card;
  border-bottom: 1rpx solid $border-color;
}

.sort-item {
  padding: 20rpx 28rpx;
  font-size: $fs-base;
  color: $text-regular;
  border-bottom: 4rpx solid transparent;
}

.sort-item.on {
  color: $brand;
  font-weight: 600;
  border-bottom-color: $brand;
}

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

.load-more {
  padding: $sp-5 0;
  text-align: center;
  font-size: $fs-sm;
  color: $text-sub;
}

.tabbar-holder {
  height: calc(100rpx + #{$safe-bottom});
}

/* 桌面：固定 5 列，列宽自适应容器。
   排序条保持通栏，内容居中交给 common/web.scss 的 gutter 内边距，
   不要再叠加 width:1240 + translateX（会与 gutter padding 叠加后溢出视口）。 */
@media (min-width: 960px) {
  .grid-books {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 20px;
    max-width: 1240px;
    margin: 0 auto;
    padding: 0 24px;
  }

  .grid-cell {
    width: 100%;
    min-width: 0;
  }

  .load-more {
    max-width: 1240px;
    margin: 0 auto;
  }
}
</style>
