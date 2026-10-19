<template>
  <view class="page">
    <!-- ① 吸顶：自定义导航（fixed，用占位高度让出空间） -->
    <nav-bar @search-tap="goSearch" />
    <view :style="{ height: topHolder + 'px' }" />

    <!-- ② 运营轮播
         桌面并入三栏首屏「分类树 | Banner | 热销榜」：这是 PC 书城的标准首屏形态，
         移动端屏幕宽度不够分栏，仍是单独一条通栏 Banner。 -->
    <view v-if="isWide" class="web-hero">
      <web-category-tree class="web-hero-side" @select="onTreeSelect" />
      <home-banner class="web-hero-main" />
      <web-rank class="web-hero-extra" title="热销榜" sub="按销量" sort="sales" :limit="5" />
    </view>
    <home-banner v-else />

    <!-- ③ 功能宫格：桌面端入口已收进顶栏主导航 + 左侧分类树，不再重复占用首屏；
         移动端仍保留四宫格作为快捷入口。 -->
    <entry-grid v-if="!isWide" :entries="entries" />

    <!-- ④ 图书楼层：新书 / 热销 / 低价，各走一条真实接口 -->
    <view v-for="f in floors" :key="f.key" class="floor">
      <floor-title :title="f.title" :sub="f.sub" @more="goFloorList(f)" />

      <!-- 首屏骨架：结构已知，比整屏 loading 体验更稳 -->
      <view v-if="f.state === 'loading'" class="grid-books">
        <view v-for="n in 4" :key="n" class="grid-cell">
          <view class="sk sk-anim sk-cover" />
          <view class="sk sk-anim sk-line" style="width: 90%" />
          <view class="sk sk-anim sk-line" style="width: 55%" />
        </view>
      </view>

      <state-view
        v-else-if="f.state === 'error'"
        type="error"
        :text="f.title + '加载失败'"
        sub-text="网络连接失败，请检查网络后重试"
        btn-text="重新加载"
        @retry="loadFloor(f)"
      />

      <view v-else-if="f.books.length" class="grid-books">
        <view v-for="b in f.books" :key="b.id" class="grid-cell">
          <book-card :book="b" @tap="goDetail(b)" />
        </view>
      </view>
    </view>

    <!-- 整页失败：只在所有楼层都没取到数据时出现，避免单个楼层抖动就盖住整页 -->
    <state-view
      v-if="allFailed"
      type="error"
      text="首页数据加载失败"
      sub-text="网络连接失败，请检查网络后重试"
      btn-text="重新加载"
      @retry="reload"
    />

    <view class="tabbar-holder" />
    <tab-bar active="home" />
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
/**
 * 首页：楼层式结构（运营位 → 功能宫格 → 图书楼层），对齐示例 App 的首页形态。
 *
 * 与旧版「搜索栏 + 行式列表」的区别：旧版本质是个搜索结果页，用户进来只能搜；
 * 楼层式是电商 App 的标准形态，不搜索也能被内容引导着逛。
 *
 * 数据全部来自真实接口：三个楼层并发请求，
 * 单个楼层失败只影响自己那一块，不会因为某一档排序接口抖动就让整页变空白。
 */
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { pageBooks } from '@/api/book'
import NavBar from '@/components/nav-bar.vue'
import TabBar from '@/components/tab-bar.vue'
import HomeBanner from '@/components/home-banner.vue'
import WebCategoryTree from '@/components/web-category-tree.vue'
import WebRank from '@/components/web-rank.vue'
import EntryGrid from '@/components/entry-grid.vue'
import FloorTitle from '@/components/floor-title.vue'
import BookCard from '@/components/book-card.vue'
import StateView from '@/components/state-view.vue'
import { refreshCartCount } from '@/common/cart-badge'
import { navHeight, isWide } from '@/common/web'
import WebFooter from '@/components/web-footer.vue'

/* 顶部导航占位高度：桌面是两段式站点顶栏（112px），移动端是状态栏 + 44px 导航行 */
const topHolder = navHeight

/** 楼层定义：sort 与后端 BookQuery.sort 白名单一一对应 */
const floors = ref([
  { key: 'new', title: '新书上架', sub: '最新入库', sort: 'default', more: '/pages/book/new', books: [], state: 'loading' },
  {
    key: 'hot',
    title: '热销榜',
    sub: '按销量',
    sort: 'sales',
    more: '/pages/book/list?sort=sales&title=' + encodeURIComponent('热销榜'),
    books: [],
    state: 'loading'
  },
  { key: 'cheap', title: '低价优选', sub: '价格从低到高', sort: 'price_asc', more: '/pages/book/cheap', books: [], state: 'loading' }
])

const allFailed = computed(() => floors.value.every((f) => f.state === 'error'))

/**
 * 宫格入口：只放「逛」的入口，与当当首页「图书 / 童书 / 新书榜 / 电子书」的形态一致。
 *
 * 不再放订单、购物车、地址这类需登录的入口：真实电商首页不会摆这些，
 * 而且它们在底部 Tab 与个人中心都已可达，摆在首页只是重复。
 * icon 是图形名（components/entry-icon.vue），label 一律写完整中文名称。
 */
const entries = [
  // 分类、书单是底部 Tab 级页面，自绘 tabBar 下 switchTab 不可用，一律 redirect
  { id: 'category', label: '分类导航', icon: 'category', tone: 'blue', url: '/pages/category/category', mode: 'redirect' },
  { id: 'rank', label: '书单', icon: 'rank', tone: 'red', url: '/pages/booklist/list', mode: 'redirect' },
  { id: 'new', label: '新书上架', icon: 'new', tone: 'amber', url: '/pages/book/new' },
  { id: 'cheap', label: '低价优选', icon: 'price', tone: 'teal', url: '/pages/book/cheap' }
]

/** 单个楼层取数：失败就地置 error，不影响其他楼层 */
async function loadFloor(f) {
  f.state = 'loading'
  try {
    const page = await pageBooks({ pageNum: 1, pageSize: 6, sort: f.sort })
    f.books = page.list || []
    f.state = 'done'
  } catch (e) {
    f.books = []
    f.state = 'error'
  }
}

async function reload() {
  floors.value.forEach((f) => {
    f.books = []
    f.state = 'loading'
  })
  await Promise.all(floors.value.map((f) => loadFloor(f)))
}

/**
 * 桌面分类树选中：跳分类页而不是图书列表页。
 *
 * 原因：二级子分类目前只存在于演示数据集（后端 book 表没有 sub_category_id，
 * BookQuery 也没有该参数），只有分类页用的是演示数据集的查询实现，
 * 能真正按子分类筛出结果；跳列表页会「点了子类但结果没变」，属于假功能。
 */
function onTreeSelect(sel) {
  if (!sel || !sel.categoryId) return
  let url = '/pages/category/category?categoryId=' + sel.categoryId
  if (sel.subCategoryId) url += '&subCategoryId=' + sel.subCategoryId
  uni.redirectTo({ url })
}

function goSearch() {
  uni.navigateTo({ url: '/pages/book/list' })
}

function goFloorList(f) {
  uni.navigateTo({ url: f.more })
}

/** 跳转锁：连点同一张卡片只会跳一次 */
let navigating = false
function goDetail(book) {
  if (navigating) return
  navigating = true
  setTimeout(() => {
    navigating = false
  }, 800)
  uni.navigateTo({ url: '/pages/book/detail?id=' + book.id })
}

onLoad(() => {
  reload()
})

/**
 * 自定义导航下不再使用系统下拉刷新（App 端会与自绘导航互相盖住），
 * 改成回到首页时自动补加载失败的楼层——网络恢复后无需用户手动点重试。
 */
onShow(() => {
  // 加购可能发生在别的页面，回来先同步角标
  refreshCartCount()
  floors.value.filter((f) => f.state === 'error').forEach((f) => loadFloor(f))
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: 40rpx;
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

.sk-cover {
  height: 0;
  padding-top: 133.33%;
  border-radius: $radius-lg;
}

.sk-line {
  height: 26rpx;
  margin-top: 14rpx;
}

/* 底部自绘 Tab 占位，防止最后一条内容被盖住 */
.tabbar-holder {
  height: calc(100rpx + #{$safe-bottom});
}

/* ============================================================
   桌面三栏首屏
   ------------------------------------------------------------
   左：分类树（可展开二级）——PC 书城把分类常驻在首屏，用户不用先点「分类」再找；
   中：运营轮播；
   右：热销榜——给「不知道买什么」的用户一个最短决策路径。
   移动端保持单列，这三个组件都不渲染（父级 v-if="isWide"）。
   ============================================================ */
@media (min-width: 960px) {
  .web-hero {
    display: flex;
    flex-direction: row;
    align-items: stretch;
    gap: 16px;
    padding: 16px 24px 0;
  }

  .web-hero-side {
    flex: 0 0 200px;
  }

  .web-hero-main {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    border-radius: 8px;
  }

  .web-hero-extra {
    flex: 0 0 220px;
    position: relative;
    z-index: 2;
  }

  /* Banner 组件默认桌面是通栏（左右 24px 边距 + 300px 高），
     进到三栏后边距交给 hero 的 gap，高度压到与左右两栏观感一致。 */
  .web-hero-main :deep(.banner) {
    margin: 0;
    height: 260px;
  }

  .web-hero-main :deep(.banner-inner) {
    height: 260px;
    padding: 0 32px;
  }

  .web-hero-main :deep(.title) {
    font-size: 28px;
  }

  .web-hero-main :deep(.sub) {
    font-size: 14px;
  }

  .web-hero-main :deep(.deco) {
    right: 32px;
    width: 140px;
    height: 140px;
  }

  .web-hero-main :deep(.deco-char) {
    font-size: 64px;
  }

  /* 桌面楼层：固定 5 列，列宽自适应容器，避免固定 px 在窄容器下被压扁 */
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

  .floor {
    max-width: 1240px;
    margin: 0 auto;
  }
}

/* 大屏（≥1240px）楼层容器居中 */
@media (min-width: 1240px) {
  .floor .grid-books {
    margin: 0 auto;
  }
}
</style>
