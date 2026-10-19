<script setup>
import { useRouter } from 'vue-router'
import { useCompareStore } from '@/stores/compare'
import { ElMessage } from 'element-plus'

const router = useRouter()
const compareStore = useCompareStore()

function goCompare() {
  if (compareStore.count < 2) {
    ElMessage.warning('请至少选择 2 本图书进行对比')
    return
  }
  router.push('/compare')
}
</script>

<template>
  <transition name="slide">
    <div v-if="compareStore.count > 0" class="compare-bar">
      <div class="compare-header">
        <span class="label">对比栏 ({{ compareStore.count }}/4)</span>
        <el-button link size="small" class="clear-btn" @click="compareStore.clear()">清空</el-button>
      </div>
      <div class="compare-items">
        <div
          v-for="book in compareStore.list"
          :key="book.id"
          class="compare-item"
          @click="router.push('/books/' + book.id)"
        >
          <span class="book-title text-ellipsis">{{ book.title }}</span>
          <el-icon class="remove" @click.stop="compareStore.remove(book.id)"><Close /></el-icon>
        </div>
      </div>
      <div class="actions">
        <el-button type="primary" size="small" @click="goCompare">
          开始对比（{{ compareStore.count }}）
        </el-button>
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

.compare-header {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.clear-btn {
  padding: 0;
  font-size: 12px;
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
  cursor: pointer;
  transition: background 0.15s;
}

.compare-item:hover {
  background: var(--brand-weak);
}

.book-title {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
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
  padding: 10px 12px;
  border-top: 1px solid var(--border-light);
  background: #fff;
}

.actions .el-button {
  width: 100%;
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
