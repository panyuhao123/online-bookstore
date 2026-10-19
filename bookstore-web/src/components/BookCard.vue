<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { bookCover, placeholderCover } from '@/utils/image'
import { formatPrice, formatRating } from '@/utils/format'
import { useCompareStore } from '@/stores/compare'
import { useFreightStore } from '@/stores/freight'
import { bookBadges } from '@/utils/badges'
import { addToCartGuarded } from '@/utils/cart-action'
import { ShoppingCart, DataAnalysis } from '@element-plus/icons-vue'

const props = defineProps({
  book: { type: Object, required: true }
})

const router = useRouter()
const compareStore = useCompareStore()
// 运费规则由布局层统一拉取，卡片只读不请求（一屏二三十张卡片不能各自去打接口）
const freightStore = useFreightStore()

const isHover = ref(false)
const cover = computed(() => bookCover(props.book) || placeholderCover(props.book.title))
const soldOut = computed(() => props.book.stock <= 0)
// 依赖 compare store 的响应式状态，加入/移出对比后卡片标记会立即更新
const inCompare = computed(() => compareStore.has(props.book.id))

/** 卡片位置有限，只留最要紧的两个（售罄会独占） */
const badges = computed(() =>
  bookBadges(props.book, {
    freeEnabled: freightStore.enabled,
    freeThreshold: freightStore.freeThreshold,
    limit: 2
  })
)

function addCart() {
  return addToCartGuarded(props.book, { router })
}

function toggleCompare() {
  compareStore.toggle(props.book)
}

function goDetail() {
  router.push('/books/' + props.book.id)
}
</script>

<template>
  <div
    class="book-card card"
    @click="goDetail"
    @mouseenter="isHover = true"
    @mouseleave="isHover = false"
  >
    <div class="cover">
      <img :src="cover" :alt="book.title" loading="lazy" />

      <!-- 徽标（N5）：放在 hover 浮层之前，鼠标移入时被浮层盖住，不挡快捷操作 -->
      <div v-if="badges.length" class="badge-strip">
        <el-tag
          v-for="b in badges"
          :key="b.key"
          :type="b.type"
          size="small"
          effect="dark"
          class="badge-item"
        >
          {{ b.text }}
        </el-tag>
      </div>

      <!-- hover 浮层快捷操作 -->
      <transition name="fade">
        <div v-if="isHover && !soldOut" class="hover-overlay" @click.stop>
          <div class="hover-actions">
            <el-tooltip content="加入购物车" placement="top">
              <el-button type="primary" :icon="ShoppingCart" circle @click="addCart" />
            </el-tooltip>
            <el-tooltip :content="inCompare ? '移出对比' : '加入对比'" placement="top">
              <el-button
                :type="inCompare ? 'default' : 'success'"
                :icon="DataAnalysis"
                circle
                @click="toggleCompare"
              />
            </el-tooltip>
          </div>
        </div>
      </transition>
    </div>

    <div class="info">
      <h3 class="title text-ellipsis-2">{{ book.title }}</h3>
      <p class="author text-ellipsis">{{ book.author }}</p>
      <div class="meta">
        <span class="rating">
          <el-icon v-if="book.avgRating" color="#f5a623"><Star /></el-icon>
          {{ formatRating(book.avgRating) }}
          <span class="review-count" v-if="book.reviewCount">({{ book.reviewCount }})</span>
        </span>
      </div>
      <div class="bottom">
        <span class="price">{{ formatPrice(book.price) }}</span>
        <span v-if="inCompare" class="compare-tag">
          <el-icon><Check /></el-icon> 对比中
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.book-card {
  padding: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.book-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-3px);
}

.cover {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  background: var(--bg-fill);
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-bottom: 10px;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.book-card:hover .cover img {
  transform: scale(1.04);
}

.badge-strip {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  pointer-events: none;
}

.badge-item {
  border: none;
}

.hover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(2px);
}

.hover-actions {
  display: flex;
  gap: 12px;
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}

.author {
  font-size: 12px;
  color: var(--text-sub);
}

.meta {
  font-size: 12px;
  color: var(--text-regular);
  display: flex;
  align-items: center;
  gap: 4px;
}

.review-count {
  color: var(--text-sub);
}

.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
}

.price {
  font-size: 16px;
}

.compare-tag {
  font-size: 11px;
  color: #67c23a;
  display: flex;
  align-items: center;
  gap: 2px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
