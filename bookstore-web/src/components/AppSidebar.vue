<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { getCategories } from '@/api/category'
import { pageBooks } from '@/api/book'
import { DArrowRight, DArrowLeft } from '@element-plus/icons-vue'

const router = useRouter()
const appStore = useAppStore()
const categories = ref([])
const popoverBooks = reactive({})

onMounted(async () => {
  try {
    categories.value = await getCategories()
  } catch (e) {
    /* 静默 */
  }
})

function goCategory(id) {
  // 分类浏览走「商品目录」：那页把分类平铺成页签、带书目数，切类不用回侧栏；
  // 「图书列表」留给带关键词的检索场景
  router.push({ path: '/catalog', query: { categoryId: id } })
}

async function onPopoverShow(cat) {
  if (popoverBooks[cat.id]) return
  try {
    const res = await pageBooks({ categoryId: cat.id, pageNum: 1, pageSize: 4, sort: 'sales' })
    popoverBooks[cat.id] = res.list || []
  } catch (e) {
    popoverBooks[cat.id] = []
  }
}
</script>

<template>
  <aside class="app-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <!-- 折叠按钮 -->
    <div class="collapse-btn" @click="appStore.toggleSidebar()" :title="appStore.sidebarCollapsed ? '展开侧栏' : '收起侧栏'">
      <el-icon :size="16">
        <component :is="appStore.sidebarCollapsed ? DArrowRight : DArrowLeft" />
      </el-icon>
    </div>

    <!-- 展开态 -->
    <template v-if="!appStore.sidebarCollapsed">
      <div class="sidebar-title">图书分类</div>
      <ul class="category-list">
        <!-- 全部图书 → 商品目录（S6）：侧栏只能按单个分类点，需要一个"不挑分类"的入口 -->
        <li class="category-item all-item" @click="router.push('/catalog')">
          <span>全部图书</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </li>
        <el-popover
          v-for="cat in categories"
          :key="cat.id"
          placement="right-start"
          width="320"
          trigger="hover"
          :show-after="150"
          :hide-after="80"
          popper-class="sidebar-popover"
          @show="onPopoverShow(cat)"
        >
          <!-- popover 内容 -->
          <div class="popover-content">
            <div class="popover-header">
              <span class="popover-title">{{ cat.name }}</span>
              <el-link type="primary" :underline="false" @click="goCategory(cat.id)">
                查看全部 ›
              </el-link>
            </div>
            <div class="popover-books" v-if="popoverBooks[cat.id]?.length">
              <div
                v-for="b in popoverBooks[cat.id]"
                :key="b.id"
                class="popover-book-item"
                @click="goCategory(cat.id)"
              >
                <span class="bk-title text-ellipsis">{{ b.title }}</span>
                <span class="bk-price">{{ b.price }}</span>
              </div>
            </div>
            <div v-else class="popover-empty">暂无图书</div>
          </div>

          <template #reference>
            <li class="category-item" @click="goCategory(cat.id)">
              <span>{{ cat.name }}</span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </li>
          </template>
        </el-popover>
      </ul>
      <div v-if="categories.length === 0" class="empty">暂无分类</div>
    </template>

    <!-- 折叠态：只显示图标首字 -->
    <template v-else>
      <ul class="category-list collapsed">
        <li
          class="category-item collapsed"
          title="全部图书"
          @click="router.push('/catalog')"
        >
          全
        </li>
        <li
          v-for="cat in categories"
          :key="cat.id"
          class="category-item collapsed"
          :title="cat.name"
          @click="goCategory(cat.id)"
        >
          {{ cat.name?.charAt(0) }}
        </li>
      </ul>
    </template>
  </aside>
</template>

<style scoped>
.app-sidebar {
  width: var(--sidebar-width);
  background: #fff;
  border-right: 1px solid var(--border);
  padding: 16px 0;
  flex-shrink: 0;
  min-height: calc(100vh - var(--header-height));
  transition: width 0.25s ease, padding 0.25s ease;
  position: relative;
  overflow: hidden;
}

.app-sidebar.collapsed {
  width: 56px;
  padding: 16px 0;
}

.collapse-btn {
  position: absolute;
  top: 10px;
  right: -12px;
  width: 24px;
  height: 24px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  color: var(--text-sub);
  box-shadow: var(--shadow-sm);
  transition: color 0.15s, border-color 0.15s;
}

.collapse-btn:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.sidebar-title {
  padding: 0 20px 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 6px;
}

.category-list {
  padding: 0;
}

.category-list.collapsed {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.category-item {
  padding: 10px 20px;
  cursor: pointer;
  color: var(--text-regular);
  font-size: 13px;
  transition: all 0.15s;
  border-left: 3px solid transparent;
  white-space: nowrap;
}

.category-item:hover {
  background: var(--brand-weak);
  color: var(--brand);
  border-left-color: var(--brand);
}

.category-item .arrow {
  opacity: 0;
  transition: opacity 0.15s;
  margin-left: auto;
  font-size: 12px;
}

.category-item:hover .arrow {
  opacity: 0.7;
}

.category-item.collapsed {
  padding: 10px 0;
  width: 40px;
  height: 40px;
  margin: 2px 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  border-left: none;
  background: var(--bg-fill);
}

.category-item.collapsed:hover {
  background: var(--brand-weak);
  color: var(--brand);
}

.empty {
  padding: 20px;
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
}

/* popover 内样式 */
.popover-content {
  padding: 4px 0;
}

.popover-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 12px 8px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--border-light);
}

.popover-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.popover-books {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 4px;
}

.popover-book-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.popover-book-item:hover {
  background: var(--brand-weak);
}

.bk-title {
  flex: 1;
  color: var(--text-regular);
}

.bk-price {
  color: var(--brand);
  font-weight: 600;
  margin-left: 8px;
}

.popover-empty {
  text-align: center;
  color: var(--text-sub);
  font-size: 12px;
  padding: 16px;
}
</style>
