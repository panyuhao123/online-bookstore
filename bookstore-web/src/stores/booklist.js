import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 书单状态（localStorage 模拟，后续可对接后端）。
 * 书单结构：{id, name, description, books: [BookVO], createdAt}
 */
const STORAGE_KEY = 'bookstore_web_booklists'

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    return []
  }
}

function saveToStorage(lists) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(lists))
}

export const useBooklistStore = defineStore('booklist', () => {
  const booklists = ref(loadFromStorage())

  function persist() {
    saveToStorage(booklists.value)
  }

  function createBooklist(name, description = '') {
    const list = {
      id: Date.now(),
      name,
      description,
      books: [],
      createdAt: new Date().toISOString()
    }
    booklists.value.unshift(list)
    persist()
    return list
  }

  function updateBooklist(id, data) {
    const idx = booklists.value.findIndex((l) => l.id === id)
    if (idx > -1) {
      booklists.value[idx] = { ...booklists.value[idx], ...data }
      persist()
    }
  }

  function deleteBooklist(id) {
    booklists.value = booklists.value.filter((l) => l.id !== id)
    persist()
  }

  function getBooklist(id) {
    return booklists.value.find((l) => l.id === Number(id))
  }

  function addBooksToList(id, books) {
    const list = getBooklist(id)
    if (!list) return
    const existIds = new Set(list.books.map((b) => b.id))
    const newBooks = books.filter((b) => !existIds.has(b.id))
    list.books.push(...newBooks)
    persist()
  }

  function removeBookFromList(id, bookId) {
    const list = getBooklist(id)
    if (!list) return
    list.books = list.books.filter((b) => b.id !== bookId)
    persist()
  }

  return {
    booklists,
    createBooklist,
    updateBooklist,
    deleteBooklist,
    getBooklist,
    addBooksToList,
    removeBookFromList
  }
})
