<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getCompareList, setCompareList } from '@/utils/storage'
import { ElMessage } from 'element-plus'

const router = useRouter()
const compareList = ref(getCompareList())

watch(
  () => compareList.value,
  (val) => setCompareList(val),
  { deep: true }
)

// 供外部调用：加入对比
function addToCompare(book) {
  if (compareList.value.some((b) => b.id === book.id)) {
    ElMessage.info('该图书已在对比栏中')
    return
  }
  if (compareList.value.length >= 4) {
    ElMessage.warning('最多对比 4 本图书')
    return
  }
  compareList.value.push({
    id: book.id,
    title: book.title,
    coverUrl: book.coverUrl,
    author: book.author,
    price: book.price,
    publisher: book.publisher
  })
  ElMessage.success('已加入对比栏')
}

function removeFromCompare(id) {
  compareList.value = compareList.value.filter((b) => b.id !== id)
}

function goCompare() {
  if (compareList.value.length < 2) {
    ElMessage.warning('请至少选择 2 本图书进行对比')
    return
  }
  router.push('/compare')
}

defineExpose({ addToCompare, compareList })
</script>

<template>
  <transition name="slide">
    <div v-if="compareList.length > 0" class="compare-bar">
      <div class="compare-inner">
        <span class="label">对比栏 ({{ compareList.length }}/4)</span>
        <div class="compare-items">
          <div v-for="book in compareList" :key="book.id" class="compare-item">
            <span class="book-title text-ellipsis">{{ book.title }}</span>
            <el-icon class="remove" @click="removeFromCompare(book.id)"><Close /></el-icon>
          </div>
        </div>
        <div class="actions">
          <el-button size="small" @click="compareList = []">清空</el-button>
          <el-button type="primary" size="small" @click="goCompare">开始对比</el-button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.compare-bar {
  position: fixed;
  right: 20px;
  bottom: 20px;
  width: 320px;
  max-height: 420px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 300;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.compare-inner {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-bottom: 1px solid var(--border-light);
  background: var(--brand-weak);
}

.label {
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.label::before {
  content: '';
  width: 3px;
  height: 16px;
  background: var(--brand);
  border-radius: 2px;
}

.compare-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  overflow-y: auto;
  flex: 1;
}

.compare-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--bg-fill);
  border-radius: var(--radius-sm);
  font-size: 13px;
  transition: background 0.15s;
}

.compare-item:hover {
  background: var(--brand-weak);
}

.book-title {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
}

.remove {
  cursor: pointer;
  color: var(--text-sub);
  flex-shrink: 0;
  transition: color 0.15s;
}

.remove:hover {
  color: var(--brand);
}

.actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--border-light);
  background: #fff;
}

.actions .el-button {
  flex: 1;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.25s, opacity 0.25s;
}
.slide-enter-from,
.slide-leave-to {
  transform: translateY(20px);
  opacity: 0;
}
</style>
