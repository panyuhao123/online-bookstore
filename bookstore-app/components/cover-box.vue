<template>
  <view class="cover-box" :style="{ backgroundImage: gradient }">
    <image
      v-if="src"
      class="cover-img"
      :class="{ loaded: loaded }"
      :src="src"
      mode="aspectFill"
      lazy-load
      @load="loaded = true"
      @error="failed = true"
    />
    <view v-if="showPlaceholder" class="cover-ph">
      <text class="cover-char">{{ displayChar }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 图书封面：真实图优先，缺失或加载失败时降级为渐变块 + 书名首字。
 *
 * 渐变始终作为底层背景，图片加载完成后淡入盖住它 ——
 * 这样弱网下的等待过程也不是一片灰，与「纯色/渐变占位图」的要求一致。
 */
import { ref, computed, watch } from 'vue'
import { bookCover, coverGradient } from '@/common/cover'

const props = defineProps({
  /** 图书对象（至少含 title / coverUrl） */
  book: { type: Object, required: true },
  /** 占位块上的文字，默认取书名首字 */
  char: { type: String, default: '' }
})

const src = computed(() => bookCover(props.book))
const gradient = computed(() => coverGradient(props.book.title))

const loaded = ref(false)
const failed = ref(false)
const showPlaceholder = computed(() => !src.value || failed.value || !loaded.value)
const displayChar = computed(() => props.char || (props.book.title || '书').charAt(0))

// 列表整体刷新时组件实例会被复用，不重置的话上一本书的失败态会带过来
watch(
  () => props.book.coverUrl,
  () => {
    loaded.value = false
    failed.value = false
  }
)
</script>

<style lang="scss" scoped>
.cover-box {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: $bg-fill;
}

.cover-img {
  width: 100%;
  height: 100%;
  opacity: 0;
  transition: opacity 0.25s;
}

.cover-img.loaded {
  opacity: 1;
}

.cover-ph {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-char {
  font-size: 64rpx;
  font-weight: 600;
  color: rgba(31, 33, 38, 0.32);
}
</style>
