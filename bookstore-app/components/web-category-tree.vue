<template>
  <!--
    PC 分类树（仅桌面渲染，调用方必须用 v-if="isWide" 包裹）。

    与移动端分类页左栏的差异：移动端是「点一级 → 右侧换二级宫格」的两栏联动，
    桌面信息密度允许把二级直接挂在下面做手风琴展开，少一次点击；
    一级超过 PREVIEW 个时折叠为「更多分类」，避免首屏被 9 个类名占满。
  -->
  <view class="wct">
    <view v-if="loading" class="wct-loading">
      <view v-for="n in 6" :key="n" class="wct-sk" />
    </view>

    <template v-else>
      <view v-for="c in visible" :key="c.id" class="wct-item">
        <view
          class="wct-row"
          :class="{ on: activeId === c.id }"
          hover-class="hover-opacity"
          @tap="onTop(c)"
        >
          <text class="wct-name">{{ c.name }}</text>
          <text class="wct-count">{{ countText(c) }}</text>
          <text class="wct-arrow">{{ opened === c.id ? '▾' : '›' }}</text>
        </view>

        <!-- 二级：展开时纵向列出，点击直接按子类筛选 -->
        <view v-if="opened === c.id && c.children && c.children.length" class="wct-sub">
          <text
            v-for="s in c.children"
            :key="s.id"
            class="wct-sub-item"
            :class="{ on: activeSubId === s.id }"
            @tap.stop="onSub(c, s)"
          >
            {{ s.name }}
          </text>
        </view>
      </view>

      <view v-if="categories.length > PREVIEW" class="wct-more" @tap="expanded = !expanded">
        <text class="wct-more-text">{{ expanded ? '收起分类' : '更多分类' }}</text>
        <text class="wct-more-arrow">{{ expanded ? '▴' : '▾' }}</text>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * PC 分类树。
 *
 * 数据源为什么走演示数据集（common/mock）而不是 GET /api/category/list：
 *   后端的分类接口返回的是扁平一级列表（CategoryVO 只有 id/name/sort/bookCount），
 *   图书表也没有 sub_category_id —— 二级分类目前只存在于 APP 演示数据集中。
 *   分类页本身就是读这份数据做二级筛选的（见 pages/category/category.vue），
 *   首页分类树与它同源，才能保证「点二级子类」跳过去能真的筛出结果。
 *
 * 组件不负责跳转：emit('select') 把选中结果交给页面，
 * 首页要跳分类页、分类页要就地筛选，两种用法由页面自己决定。
 */
import { ref, computed } from 'vue'
import { getCategories } from '@/common/mock'

/** 首屏默认展示的一级分类数，其余收进「更多分类」 */
const PREVIEW = 8

const props = defineProps({
  /** 当前选中的一级分类 ID（高亮用） */
  activeId: { type: [Number, String], default: null },
  /** 当前选中的二级分类 ID（高亮用） */
  activeSubId: { type: [Number, String], default: null }
})

const emit = defineEmits(['select'])

const categories = ref([])
const loading = ref(true)
const opened = ref(null)
const expanded = ref(false)

const visible = computed(() =>
  expanded.value ? categories.value : categories.value.slice(0, PREVIEW)
)

/** 分类下的图书数：演示数据集自带，没有就不显示，不编数字 */
function countText(c) {
  const n = Number(c.bookCount || 0)
  return n > 0 ? String(n) : ''
}

async function load() {
  try {
    categories.value = await getCategories()
  } catch (e) {
    categories.value = []
  } finally {
    loading.value = false
  }
}

function onTop(c) {
  // 手风琴：点已展开的项收起，点其他项展开该项
  opened.value = opened.value === c.id ? null : c.id
  emit('select', { categoryId: c.id, subCategoryId: null, name: c.name })
}

function onSub(c, s) {
  emit('select', { categoryId: c.id, subCategoryId: s.id, name: c.name + ' / ' + s.name })
}

load()
</script>

<style lang="scss" scoped>
/* 本组件只在 ≥960px 渲染，尺寸直接用 px 表达桌面尺度 */
.wct {
  background-color: $bg-card;
  border-radius: 6px;
  padding: 6px 0;
}

.wct-loading {
  padding: 6px 8px;
}

.wct-sk {
  height: 14px;
  margin: 10px 0;
  background-color: $bg-fill;
  border-radius: 3px;
}

.wct-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 7px 12px;
  font-size: 13px;
  color: $text-regular;
}

/* 选中态沿用分类页的三重信号：弱红底 + 左竖条 + 文字变色加粗 */
.wct-row.on {
  background-color: $brand-weak;
  box-shadow: inset 3px 0 0 $brand;
}

.wct-row.on .wct-name {
  color: $brand;
  font-weight: 600;
}

.wct-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wct-count {
  flex-shrink: 0;
  margin-left: 6px;
  font-size: 11px;
  color: $text-placeholder;
}

.wct-arrow {
  flex-shrink: 0;
  margin-left: 6px;
  font-size: 11px;
  color: $text-placeholder;
}

.wct-sub {
  padding: 2px 12px 8px 24px;
  background-color: $brand-weak;
}

.wct-sub-item {
  display: block;
  padding: 4px 0;
  font-size: 12px;
  color: $text-regular;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wct-sub-item.on {
  color: $brand;
  font-weight: 600;
}

.wct-sub-item:hover {
  color: $brand;
}

.wct-more {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
  padding: 8px 0;
  border-top: 1px solid $border-color;
  font-size: 12px;
  color: $brand;
}

@media (hover: hover) and (pointer: fine) {
  .wct-row {
    cursor: pointer;
  }

  .wct-row:hover {
    background-color: $bg-page;
  }

  .wct-row.on:hover {
    background-color: $brand-weak;
  }

  .wct-sub-item {
    cursor: pointer;
  }

  .wct-more {
    cursor: pointer;
  }

  .wct-more:hover .wct-more-text {
    text-decoration: underline;
  }
}
</style>
