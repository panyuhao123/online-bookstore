import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  pageBooklists,
  getMyBooklists,
  getBooklist,
  createBooklist,
  updateBooklist,
  deleteBooklist,
  addBooksToBooklist,
  removeBookFromBooklist
} from '@/api/booklist'

/**
 * 书单状态（后端数据源）。
 *
 * <p><b>为什么换掉 localStorage</b>：书单是这个商城里最像"用户资产"的东西，
 * 存在浏览器里换台设备就没了，也没法分享给别人看。V6 迁移落库之后，
 * 这里改成全部走接口，localStorage 只剩一个用途 —— 存放**旧版本遗留的数据**，
 * 等用户登录后提示一次性导入，导完即清空。
 *
 * <p><b>不做本地缓存</b>：书单数量少、变化频繁（改名、加书、删书），
 * 缓存带来的收益远小于"改完还看到旧数据"的困惑。每次进页面都拉最新的。
 */
const LEGACY_KEY = 'bookstore_web_booklists'

function loadLegacy() {
  try {
    const raw = localStorage.getItem(LEGACY_KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list.filter((l) => l && l.name) : []
  } catch (e) {
    return []
  }
}

function saveLegacy(list) {
  try {
    if (!list || list.length === 0) localStorage.removeItem(LEGACY_KEY)
    else localStorage.setItem(LEGACY_KEY, JSON.stringify(list))
  } catch (e) {
    /* 隐私模式下 localStorage 可能不可写，忽略即可 */
  }
}

export const useBooklistStore = defineStore('booklist', () => {
  /** 广场（公开书单 + 自己创建的私密书单） */
  const square = ref([])
  const squareTotal = ref(0)
  const squareLoading = ref(false)

  /** 我的书单（含已隐藏，带 status 标记） */
  const mine = ref([])
  const mineTotal = ref(0)
  const mineLoading = ref(false)

  /** 当前打开的书单详情 */
  const detail = ref(null)
  const detailLoading = ref(false)

  /** 旧版本遗留在 localStorage 的书单（登录后可一键导入） */
  const legacy = ref(loadLegacy())
  const hasLegacy = computed(() => legacy.value.length > 0)
  const legacyBookCount = computed(() =>
    legacy.value.reduce((sum, l) => sum + (l.books?.length || 0), 0)
  )

  const hiddenMine = computed(() => mine.value.filter((l) => l.status === 0))

  async function fetchSquare(params = {}) {
    squareLoading.value = true
    try {
      const res = await pageBooklists({ pageNum: 1, pageSize: 12, ...params })
      square.value = res.list || []
      squareTotal.value = res.total || 0
    } finally {
      squareLoading.value = false
    }
  }

  async function fetchMine(params = {}) {
    mineLoading.value = true
    try {
      const res = await getMyBooklists({ pageNum: 1, pageSize: 50, ...params })
      mine.value = res.list || []
      mineTotal.value = res.total || 0
    } finally {
      mineLoading.value = false
    }
  }

  async function fetchDetail(id) {
    detailLoading.value = true
    try {
      detail.value = await getBooklist(id)
      return detail.value
    } finally {
      detailLoading.value = false
    }
  }

  async function create(data) {
    return createBooklist(data)
  }

  async function update(id, data) {
    const updated = await updateBooklist(id, data)
    // 详情页改名后要立刻反映，否则用户看到的是"改了但没变"
    if (detail.value && String(detail.value.id) === String(id)) {
      detail.value = { ...detail.value, ...updated }
    }
    return updated
  }

  async function remove(id) {
    await deleteBooklist(id)
    mine.value = mine.value.filter((l) => String(l.id) !== String(id))
    square.value = square.value.filter((l) => String(l.id) !== String(id))
    if (detail.value && String(detail.value.id) === String(id)) detail.value = null
  }

  /**
   * 批量加书。
   * @returns {Promise<{added:number,total:number}>} added 是真正新增的条数，
   *          已存在的会被跳过 —— 直接说"已加入 N 本"而实际一本没加，是用户最反感的那类提示
   */
  async function addBooks(id, bookIds) {
    const res = await addBooksToBooklist(id, bookIds)
    return { added: res?.added ?? 0, total: res?.total ?? (bookIds || []).length }
  }

  async function removeBook(id, bookId) {
    await removeBookFromBooklist(id, bookId)
    if (detail.value && String(detail.value.id) === String(id)) {
      const books = (detail.value.books || []).filter((b) => String(b.id) !== String(bookId))
      detail.value = {
        ...detail.value,
        books,
        bookCount: Math.max(0, (detail.value.bookCount || 1) - 1)
      }
    }
  }

  /**
   * 把旧版本 localStorage 里的书单导入到后端。
   *
   * <p>逐条「先建单、再加书」而不是拼一个批量接口：书单数量本来就只有个位数，
   * 多几次请求换来"某一单失败不影响其它单"，比一口气提交后整批回滚更符合用户预期。
   * 失败的条目会保留在本地，下次还能再试 —— 静默丢掉用户的旧数据是不可接受的。
   *
   * @returns {Promise<{imported:number, failed:number, books:number}>}
   */
  async function importLegacy() {
    const result = { imported: 0, failed: 0, books: 0 }
    const remain = []

    for (const old of legacy.value) {
      try {
        const created = await createBooklist({
          name: old.name,
          description: old.description || '',
          isPublic: 0
        })
        const bookIds = (old.books || [])
          .map((b) => b?.id ?? b?.bookId)
          .filter((id) => id !== undefined && id !== null)
        if (bookIds.length > 0) {
          const added = await addBooksToBooklist(created.id, bookIds)
          result.books += added?.added ?? 0
        }
        result.imported += 1
      } catch (e) {
        result.failed += 1
        remain.push(old)
      }
    }

    legacy.value = remain
    saveLegacy(remain)
    if (result.imported > 0) await fetchMine()
    return result
  }

  /** 放弃旧数据（用户明确选择"不要了"时才调用） */
  function discardLegacy() {
    legacy.value = []
    saveLegacy([])
  }

  return {
    square,
    squareTotal,
    squareLoading,
    mine,
    mineTotal,
    mineLoading,
    detail,
    detailLoading,
    legacy,
    hasLegacy,
    legacyBookCount,
    hiddenMine,
    fetchSquare,
    fetchMine,
    fetchDetail,
    create,
    update,
    remove,
    addBooks,
    removeBook,
    importLegacy,
    discardLegacy
  }
})
