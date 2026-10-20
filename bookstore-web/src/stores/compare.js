import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { getCompareList, setCompareList } from '@/utils/storage'

const COMPARE_MAX = 4

/**
 * 只保留对比需要的字段。
 *
 * <p>此前 BookCard 会把整本图书（含 description、publishYear… ）塞进 localStorage，
 * 而 CompareBar 只往里写 6 个字段，两边口径不一致；统一在这里裁剪。
 */
function toCompareItem(book) {
  return {
    id: book.id,
    title: book.title,
    coverUrl: book.coverUrl,
    author: book.author,
    price: book.price,
    publisher: book.publisher
  }
}

/**
 * 图书对比栏（响应式单一数据源）。
 *
 * <p>修复的问题：原先 BookCard 直接读写 localStorage 且 `inCompare` 是**无响应式依赖**的
 * computed，CompareBar 又在 setup 里对 localStorage 做了一次性快照 —— 于是加购/移出对比后，
 * 卡片上的「对比中」标记和右下角对比栏互不同步，必须刷新页面才对。这里把两处收敛到同一个 store，
 * localStorage 只作为持久化落地。
 */
export const useCompareStore = defineStore('compare', () => {
  const list = ref(getCompareList())

  const count = computed(() => list.value.length)
  const isFull = computed(() => count.value >= COMPARE_MAX)
  const ids = computed(() => list.value.map((b) => b.id))

  function persist() {
    setCompareList(list.value)
  }

  function has(id) {
    return list.value.some((b) => b.id === id)
  }

  /** @returns {boolean} 是否真的加入了（false 时已给出提示，调用方无需重复提示） */
  function add(book) {
    if (!book || book.id === undefined || book.id === null) return false
    if (has(book.id)) {
      ElMessage.info('该图书已在对比栏中')
      return false
    }
    if (isFull.value) {
      ElMessage.warning(`最多对比 ${COMPARE_MAX} 本图书`)
      return false
    }
    list.value = [...list.value, toCompareItem(book)]
    persist()
    ElMessage.success('已加入对比栏')
    return true
  }

  function remove(id) {
    if (!has(id)) return false
    list.value = list.value.filter((b) => b.id !== id)
    persist()
    return true
  }

  /** 在对比栏中则移出，否则加入 */
  function toggle(book) {
    if (has(book?.id)) {
      remove(book.id)
      ElMessage.info('已从对比栏移除')
      return false
    }
    return add(book)
  }

  function clear() {
    list.value = []
    persist()
  }

  /**
   * 批量加入（列表页「加入对比」按钮用）。
   * 逐条 add() 会连弹 N 次提示，所以这里一次性算好再给结论。
   */
  function addMany(books) {
    const result = { added: 0, duplicated: 0, overflow: 0 }
    const next = [...list.value]
    const nextIds = new Set(next.map((b) => b.id))

    for (const book of books || []) {
      if (!book || book.id === undefined || book.id === null) continue
      if (nextIds.has(book.id)) {
        result.duplicated += 1
        continue
      }
      if (next.length >= COMPARE_MAX) {
        result.overflow += 1
        continue
      }
      next.push(toCompareItem(book))
      nextIds.add(book.id)
      result.added += 1
    }

    if (result.added > 0) {
      list.value = next
      persist()
      ElMessage.success(`已加入对比栏 ${result.added} 本`)
    }
    if (result.overflow > 0) {
      ElMessage.warning(`对比最多 ${COMPARE_MAX} 本，已忽略 ${result.overflow} 本`)
    } else if (result.added === 0 && result.duplicated > 0) {
      ElMessage.info('所选图书均已在对比栏中')
    }
    return result
  }

  return { list, count, isFull, ids, has, add, addMany, remove, toggle, clear }
})
