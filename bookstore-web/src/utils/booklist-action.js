import { ElMessage, ElMessageBox } from 'element-plus'
import { useBooklistStore } from '@/stores/booklist'
import { useUserStore } from '@/stores/user'
import { saveRedirect } from '@/utils/storage'

/**
 * 书单相关的公共动作。
 *
 * <p>书单从 localStorage 迁到后端之后，「建单 → 加书」这套流程在图书列表、高级检索、
 * 购物车、浏览历史四处各写了一遍，口径还互不相同（有的只说"已加入"、有的把已存在的书
 * 也算进去）。这里收敛成一处，顺带修掉迁移时漏改的三处调用 —— 它们仍在调
 * `createBooklist` / `addBooksToList` 这些**已经不存在的方法**，点下去只会抛 TypeError。
 */

/** ElMessageBox 用户取消时 reject 的是 'cancel' / 'close' 这两个字符串，不是 Error */
function isCancel(e) {
  return e === 'cancel' || e === 'close'
}

/**
 * 统一取图书 id。
 *
 * <p>图书对象在不同页面长得不一样：列表页是 `BookVO`（主键 `id`），
 * 购物车行是 `CartVO`（主键 `id`，图书 id 在 `bookId`）。不统一的话每处都要记一遍字段名。
 */
function toBookIds(books) {
  return (books || [])
    .map((b) => b?.id ?? b?.bookId)
    .filter((id) => id !== undefined && id !== null)
}

/**
 * 生成「加书结果」提示。
 *
 * <p>后端会跳过书单里已有的书并回传 `{added,total}`。如果无视它一律说"已加入 N 本"，
 * 用户会遇到"提示加成功、打开书单一本没多"的情况 —— 这是最招人烦的那类提示。
 */
function addedMessage(res, name) {
  const added = res?.added ?? 0
  const total = res?.total ?? added
  const target = name ? `「${name}」` : '书单'
  if (added === 0) return { type: 'info', text: `这些图书都已在${target}中` }
  if (added < total) {
    return { type: 'success', text: `已加入 ${added} 本到${target}，${total - added} 本已在其中` }
  }
  return { type: 'success', text: `已加入 ${added} 本到${target}` }
}

function requireLogin(router) {
  const userStore = useUserStore()
  if (userStore.isLogin) return true
  ElMessage.warning('请先登录后再操作书单')
  if (router) {
    saveRedirect(router.currentRoute.value.fullPath)
    router.push('/login')
  }
  return false
}

/** 可被选作目标的「我的书单」：已被平台隐藏的书单不再往里加书 */
function selectableLists(lists) {
  return (lists || []).filter((l) => l.status !== 0)
}

/**
 * 把选中的图书存成一个新书单。
 *
 * @param {Array} books 图书对象数组
 * @param {object} [options]
 * @param {object} [options.router] 未登录时用于跳转登录页
 * @returns {Promise<object|null>} 新建的书单；取消 / 失败返回 null
 */
export async function saveBooksToNewBooklist(books, options = {}) {
  const { router = null } = options
  const ids = toBookIds(books)
  if (ids.length === 0) {
    ElMessage.warning('请先选择图书')
    return null
  }
  if (!requireLogin(router)) return null

  const store = useBooklistStore()
  let name = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入书单名称', '保存为书单', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (v) => {
        const s = (v || '').trim()
        if (!s) return '书单名称不能为空'
        // 后端 BookListRequest 限制 50 字，先在前端拦一道，省一次往返
        if (s.length > 50) return '书单名称不能超过 50 个字'
        return true
      }
    })
    name = (value || '').trim()
  } catch (e) {
    if (isCancel(e)) return null
    return null
  }

  try {
    // 批量存书单是私人行为，默认建成「仅自己可见」；想公开再去书单里改
    const created = await store.create({ name, isPublic: 0 })
    const res = await store.addBooks(created.id, ids)
    const msg = addedMessage(res, name)
    ElMessage[msg.type](msg.text)
    return created
  } catch (e) {
    /* 接口层已弹过错误提示，这里只结束流程 */
    return null
  }
}

/**
 * 把选中的图书加进「已有的某个书单」，书单由用户在下拉里选。
 *
 * @param {Array} books
 * @param {object} [options]
 * @param {object} [options.router]
 * @returns {Promise<{id:number,res:object}|null>}
 */
export async function addBooksToPickedBooklist(books, options = {}) {
  const { router = null } = options
  const ids = toBookIds(books)
  if (ids.length === 0) {
    ElMessage.warning('请先选择图书')
    return null
  }
  if (!requireLogin(router)) return null

  const store = useBooklistStore()
  try {
    await store.fetchMine()
  } catch (e) {
    return null
  }

  const lists = selectableLists(store.mine)
  if (lists.length === 0) {
    ElMessage.info('还没有书单，先创建一个吧')
    if (router) router.push('/booklists/new')
    return null
  }

  let id = null
  let name = ''
  try {
    const { value } = await ElMessageBox.prompt('选择要加入的书单', '加入书单', {
      inputType: 'select',
      inputOptions: lists.map((l) => ({ value: l.id, label: l.name })),
      confirmButtonText: '加入',
      cancelButtonText: '取消'
    })
    id = Number(value)
    name = lists.find((l) => String(l.id) === String(value))?.name || ''
  } catch (e) {
    if (isCancel(e)) return null
    return null
  }

  try {
    const res = await store.addBooks(id, ids)
    const msg = addedMessage(res, name)
    ElMessage[msg.type](msg.text)
    return { id, res }
  } catch (e) {
    return null
  }
}
