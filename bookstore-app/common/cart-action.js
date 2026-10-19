/**
 * 加购统一入口。
 *
 * 首页 / 分类 / 榜单 / 个人中心的卡片上都有「加购」按钮，如果每个页面各写一遍
 * 「未登录存本机、已登录调接口、加完刷新角标」这三件事，迟早会漏掉某一处。
 * 这里收敛成一个函数，页面只负责传图书对象与数量。
 *
 * 失败提示由 common/request.js 统一弹出（后端返回的 message），这里不重复提示，
 * 避免出现两条 Toast。
 */
import { addToCart } from '@/api/cart'
import { isLogin } from '@/common/auth'
import { addLocalCart } from '@/common/cart-local'
import { notifyCartChanged } from '@/common/cart-badge'
import { once } from '@/common/once'

/**
 * 加入购物车。
 *
 * 防重：H5 鼠标双击会连发两次，后端没有幂等键，两请求都会成功 → 加两件。
 * 锁加在这里而不是各个页面，是因为卡片加购出现在首页 / 分类 / 榜单 / 书单详情，
 * 一处加锁即可覆盖全部入口。
 *
 * @param {Object} book 图书对象（至少含 id / title / price / stock）
 * @param {Number} [quantity=1] 数量
 * @returns {Promise<{ok: boolean, local?: boolean, dropped?: boolean}>}
 *          local 为 true 表示存的是本机；dropped 为 true 表示上一次还没结束，本次被丢弃
 */
export async function addBookToCart(book, quantity = 1) {
  if (!book || !book.id) return { ok: false }

  // 售罄直接拦住：与其让用户点了之后被服务端驳回，不如在点击前就说清楚
  if (Number(book.stock || 0) <= 0) {
    uni.showToast({ title: '该图书已售罄', icon: 'none' })
    return { ok: false }
  }

  return (
    (await once('cart:add', async () => {
      if (!isLogin()) {
        const count = addLocalCart(book, quantity)
        notifyCartChanged()
        uni.showToast({
          title: `已加入本机购物车（共 ${count} 件），登录后自动同步`,
          icon: 'none'
        })
        return { ok: true, local: true }
      }

      try {
        await addToCart({ bookId: book.id, quantity })
        notifyCartChanged()
        uni.showToast({ title: '已加入购物车', icon: 'success' })
        return { ok: true }
      } catch (e) {
        return { ok: false }
      }
    })) || { ok: false, dropped: true }
  )
}
