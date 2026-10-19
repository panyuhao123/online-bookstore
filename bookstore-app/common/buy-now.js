/**
 * 「立即购买」统一入口。
 *
 * 后端下单只接受 cartIds，所以流程固定为三步：
 *   加购 → 回查购物车取出该条目的 id → 只结算这一条。
 * 之所以不直接把 bookId 传给确认页：确认页渲染的是「购物车条目」
 * （带数量、勾选态、服务端价格快照），用 bookId 反推会丢掉这些信息，
 * 也会把用户其他已勾选的商品一起结算。
 *
 * 详情页与低价优选页共用这一份实现 —— 这段回查逻辑漏一步就会结算错条目，
 * 复制两份必然会在某次改动后走样。
 */
import { addToCart, getCartList } from '@/api/cart'
import { isLogin } from '@/common/auth'
import { notifyCartChanged } from '@/common/cart-badge'
import { once } from '@/common/once'

/**
 * @param {Object} book 图书对象（至少含 id / stock）
 * @param {number} [quantity=1] 数量
 * @returns {Promise<boolean>} 是否成功进入确认页
 */
export async function buyNow(book, quantity = 1) {
  if (!book || !book.id) return false

  if (Number(book.stock || 0) <= 0) {
    uni.showToast({ title: '该图书已售罄', icon: 'none' })
    return false
  }

  // 未登录不能结算：购物车可以本机暂存，订单必须落到账号上
  if (!isLogin()) {
    uni.showModal({
      title: '需要登录',
      content: '立即购买前需要登录，你本机已加购的商品会在登录后自动同步',
      confirmText: '去登录',
      success: (res) => {
        if (res.confirm) uni.navigateTo({ url: '/pages/login/login' })
      }
    })
    return false
  }

  // 防重：本函数会「加购 + 回查 + 跳转」三步，鼠标双击会各跑一遍，
  // 结果是同一本书被加购两次。锁加在公共入口，详情页与低价优选页同时受益。
  return (
    (await once('order:buy-now', async () => {
      uni.showLoading({ title: '处理中', mask: true })
      try {
        await addToCart({ bookId: book.id, quantity })
        const data = await getCartList()
        const item = (data.list || []).find((i) => String(i.bookId) === String(book.id))
        uni.hideLoading()
        if (!item) {
          uni.showToast({ title: '加入购物车失败，请重试', icon: 'none' })
          return false
        }
        notifyCartChanged()
        uni.navigateTo({ url: '/pages/order/confirm?cartIds=' + item.id })
        return true
      } catch (e) {
        uni.hideLoading()
        return false
      }
    })) || false
  )
}
