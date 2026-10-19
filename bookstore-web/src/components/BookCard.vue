<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { bookCover, placeholderCover } from '@/utils/image'
import { formatPrice, formatRating } from '@/utils/format'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { ShoppingCart, DataAnalysis, Check } from '@element-plus/icons-vue'
import { getCompareList, setCompareList } from '@/utils/storage'

const props = defineProps({
  book: { type: Object, required: true }
})

const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const isHover = ref(false)
const cover = computed(() => bookCover(props.book) || placeholderCover(props.book.title))
const soldOut = computed(() => props.book.stock <= 0)
const inCompare = computed(() => getCompareList().some((b) => b.id === props.book.id))

async function addCart() {
  if (soldOut.value) {
    ElMessage.warning('该图书已售罄')
    return
  }
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后再加购')
    localStorage.setItem('bookstore_web_redirect', router.currentRoute.value.fullPath)
    router.push('/login')
    return
  }
  await cartStore.add(props.book.id, 1)
  ElMessage.success('已加入购物车')
}

function toggleCompare() {
  const list = getCompareList()
  if (inCompare.value) {
    setCompareList(list.filter((b) => b.id !== props.book.id))
    ElMessage.info('已从对比栏移除')
    return
  }
  if (list.length >= 4) {
    ElMessage.warning('最多对比 4 本图书')
    return
  }
  list.push({
    id: props.book.id,
    title: props.book.title,
    coverUrl: props.book.coverUrl,
    author: props.book.author,
    price: props.book.price,
    publisher: props.book.publisher,
    publishYear: props.book.publishYear,
    avgRating: props.book.avgRating,
    reviewCount: props.book.reviewCount,
    stock: props.book.stock,
    description: props.book.description
  })
  setCompareList(list)
  ElMessage.success('已加入对比栏')
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
      <span v-if="soldOut" class="sold-out-tag">已售罄</span>

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

.sold-out-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
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
