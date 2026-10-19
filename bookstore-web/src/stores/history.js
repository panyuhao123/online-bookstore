import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getHistory, setHistory, clearHistory as clearStorageHistory } from '@/utils/storage'

/**
 * 浏览历史（localStorage 模拟）。
 * 每项：{bookId, title, author, coverUrl, price, viewTime}
 */
export const useHistoryStore = defineStore('history', () => {
  const list = ref(getHistory())

  function addRecord(book) {
    const record = {
      bookId: book.id,
      title: book.title,
      author: book.author,
      coverUrl: book.coverUrl,
      price: book.price,
      viewTime: new Date().toISOString()
    }
    // 去重：同本书只保留最新一条
    list.value = list.value.filter((r) => r.bookId !== book.id)
    list.value.unshift(record)
    // 最多保留 100 条
    if (list.value.length > 100) list.value = list.value.slice(0, 100)
    setHistory(list.value)
  }

  function removeRecords(bookIds) {
    const set = new Set(bookIds)
    list.value = list.value.filter((r) => !set.has(r.bookId))
    setHistory(list.value)
  }

  function clearAll() {
    list.value = []
    clearStorageHistory()
  }

  return { list, addRecord, removeRecords, clearAll }
})
