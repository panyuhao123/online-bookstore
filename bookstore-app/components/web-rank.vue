<template>
  <!-- PC 榜单（仅桌面渲染）：名次 + 书名 + 价格，底部给「完整榜单」入口 -->
  <view class="wr">
    <view class="wr-head">
      <text class="wr-title">{{ title }}</text>
      <text v-if="sub" class="wr-sub">{{ sub }}</text>
    </view>

    <view v-if="loading" class="wr-loading">
      <view v-for="n in limit" :key="n" class="wr-sk" />
    </view>

    <template v-else-if="books.length">
      <view
        v-for="(b, i) in books"
        :key="b.id"
        class="wr-item"
        hover-class="hover-opacity"
        @tap="goDetail(b)"
      >
        <text class="wr-no" :class="{ gold: i === 0, silver: i === 1, bronze: i === 2 }">{{ i + 1 }}</text>
        <text class="wr-name ellipsis">{{ b.title }}</text>
        <text class="wr-price">¥{{ price(b.price) }}</text>
      </view>

      <view class="wr-more" @tap="goFull">
        <text class="wr-more-text">完整{{ title }} ›</text>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * PC 侧边榜单。
 *
 * 数据来自真实的图书分页接口（与首页楼层同源），只是换排序取前 N 条：
 *   sales      → 热销榜
 *   new        → 新书榜（演示排序，真实接口未见白名单时用 default 回落）
 *   price_asc  → 低价榜
 * 榜单不自己造数据，也不写死书名——换库换数据它自动跟着变。
 *
 * 「完整榜单」跳列表页并带上同一排序，保证点进去看到的就是榜单的延续，
 * 而不是另一个口径的结果。
 */
import { ref } from 'vue'
import { pageBooks } from '@/api/book'
import { price } from '@/common/format'

const props = defineProps({
  /** 榜单标题，同时用于「完整 xx」入口文案 */
  title: { type: String, default: '热销榜' },
  /** 副标题，如「按销量」 */
  sub: { type: String, default: '' },
  /** 排序：与后端 BookQuery.sort 白名单一致 */
  sort: { type: String, default: 'sales' },
  /** 展示条数 */
  limit: { type: Number, default: 5 },
  /** 限定在某个分类内排名（分类页用） */
  categoryId: { type: [Number, String], default: '' }
})

const books = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: props.limit, sort: props.sort }
    if (props.categoryId) params.categoryId = props.categoryId
    const page = await pageBooks(params)
    books.value = page.list || []
  } catch (e) {
    books.value = []
  } finally {
    loading.value = false
  }
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

function goFull() {
  const url =
    '/pages/book/list?sort=' +
    encodeURIComponent(props.sort) +
    '&title=' +
    encodeURIComponent(props.title)
  uni.navigateTo({ url })
}

load()
</script>

<style lang="scss" scoped>
.wr {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 6px;
  padding: 12px;
}

.wr-head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.wr-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-main;
}

.wr-sub {
  font-size: 11px;
  color: $text-placeholder;
}

.wr-loading {
  padding: 2px 0;
}

.wr-sk {
  height: 12px;
  margin: 8px 0;
  background-color: $bg-fill;
  border-radius: 3px;
}

.wr-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  padding: 5px 0;
  font-size: 12px;
  color: $text-regular;
}

.wr-no {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: $color-white;
  background-color: $border-strong;
  border-radius: 50%;
}

/* 前三名用金 / 银 / 铜区分，与榜单页卡片角标同一套语义 */
.wr-no.gold {
  background-color: #d99b1f;
}

.wr-no.silver {
  background-color: #a8adb5;
}

.wr-no.bronze {
  background-color: #b47e4a;
}

.wr-name {
  flex: 1;
  min-width: 0;
  color: $text-main;
}

.wr-price {
  flex-shrink: 0;
  font-size: 11px;
  color: $brand;
}

.wr-more {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid $border-color;
  text-align: right;
}

.wr-more-text {
  font-size: 12px;
  color: $brand;
}

@media (hover: hover) and (pointer: fine) {
  .wr-item {
    cursor: pointer;
  }

  .wr-item:hover .wr-name {
    color: $brand;
  }

  .wr-more {
    cursor: pointer;
  }

  .wr-more:hover .wr-more-text {
    text-decoration: underline;
  }
}
</style>
