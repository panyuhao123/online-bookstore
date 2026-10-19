import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistory, setHistory, clearHistory as clearStorageHistory } from '@/utils/storage'
import {
  recordHistory,
  pageHistory,
  mergeHistory,
  removeHistory,
  clearHistory as clearRemoteHistory
} from '@/api/history'
import { useUserStore } from '@/stores/user'

/** 本地最多保留的条数（与服务端上限一致，避免合并时超出上限被截断） */
const LOCAL_MAX = 100

/**
 * 浏览历史（双轨：未登录走本地，登录走服务端）。
 *
 * <p><b>为什么要双轨</b>：历史是"顺手产生"的数据，要求先登录才能记录等于把
 * 90% 的浏览行为丢掉。所以未登录时照常记在 localStorage，等用户登录后
 * 一次性 `merge` 上去 —— 这也是后端专门做了 `/history/merge` 的原因。
 *
 * <p><b>合并只做一次</b>：`synced` 标记保证同一次登录会话里不会反复提交。
 * 反复提交虽然接口是幂等的，但会让用户每次进页面都看到一条"已同步 N 条"的提示。
 */
export const useHistoryStore = defineStore('history', () => {
  const userStore = useUserStore()

  /** 未登录时的本地记录：[{bookId, title, author, coverUrl, price, viewTime}] */
  const local = ref(getHistory())

  /** 服务端分页结果（登录后使用） */
  const list = ref([])
  const total = ref(0)
  const loading = ref(false)

  /** 本次登录会话是否已把本地记录合并上去 */
  const synced = ref(false)

  const isLogin = computed(() => userStore.isLogin)
  /** 页面展示用的数据源：登录看服务端，未登录看本地 */
  const displayList = computed(() => (isLogin.value ? list.value : local.value))

  function persistLocal() {
    setHistory(local.value)
  }

  function addLocal(book) {
    if (!book || book.id === undefined || book.id === null) return
    const record = {
      bookId: book.id,
      title: book.title,
      author: book.author,
      coverUrl: book.coverUrl,
      price: book.price,
      viewTime: new Date().toISOString()
    }
    // 去重：同本书只保留最新一条，然后插到最前面
    local.value = [record, ...local.value.filter((r) => r.bookId !== book.id)].slice(0, LOCAL_MAX)
    persistLocal()
  }

  /**
   * 记录一次浏览。
   *
   * <p>登录态下失败**静默**：历史记录失败不该打断用户看书，
   * 更不该因为一次历史接口报错就在详情页弹一个红色错误框。
   */
  async function addRecord(book) {
    if (!book || book.id === undefined || book.id === null) return
    if (!isLogin.value) {
      addLocal(book)
      return
    }
    try {
      await recordHistory(book.id)
    } catch (e) {
      /* 静默：历史不是主流程 */
    }
  }

  async function fetchPage(params = {}) {
    if (!isLogin.value) {
      list.value = []
      total.value = 0
      return
    }
    loading.value = true
    try {
      const res = await pageHistory({ pageNum: 1, pageSize: 20, ...params })
      list.value = res.list || []
      total.value = res.total || 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 把未登录期间的本地记录合并到服务端（幂等，可重复调用）。
   *
   * @returns {Promise<{added:number,total:number}|null>} 未登录或无本地记录时返回 null
   */
  async function syncLocal() {
    if (!isLogin.value || synced.value) return null
    if (local.value.length === 0) {
      synced.value = true
      return null
    }

    // 本地已按浏览时间倒序存，直接按序提交：后端约定第 0 条最新，
    // 顺序传反会让"最近浏览"变成"最久之前浏览"。
    const bookIds = local.value.map((r) => r.bookId).filter((id) => id !== undefined && id !== null)
    try {
      const res = await mergeHistory(bookIds)
      synced.value = true
      // 合并成功后清本地：留着会在下次登录时再合并一遍，
      // 虽然幂等不产生脏数据，但"已同步 3 条"的提示会反复出现。
      local.value = []
      clearStorageHistory()
      if (res && res.added > 0) {
        ElMessage.success(`已同步 ${res.added} 条登录前的浏览记录`)
      }
      return res
    } catch (e) {
      // 失败不置 synced，下次进页面还能再试
      return null
    }
  }

  /** 删除单条（登录删服务端，未登录删本地） */
  async function removeOne(bookId) {
    if (isLogin.value) {
      await removeHistory(bookId)
      list.value = list.value.filter((r) => r.bookId !== bookId)
      total.value = Math.max(0, total.value - 1)
    } else {
      local.value = local.value.filter((r) => r.bookId !== bookId)
      persistLocal()
    }
  }

  /** 清空全部 */
  async function clearAll() {
    if (isLogin.value) {
      await clearRemoteHistory()
      list.value = []
      total.value = 0
    } else {
      local.value = []
      clearStorageHistory()
    }
  }

  /** 退出登录时把敏感的服务端数据从内存里清掉（本地历史不动） */
  function reset() {
    list.value = []
    total.value = 0
    synced.value = false
  }

  return {
    local,
    list,
    total,
    loading,
    isLogin,
    displayList,
    addRecord,
    fetchPage,
    syncLocal,
    removeOne,
    clearAll,
    reset
  }
})
