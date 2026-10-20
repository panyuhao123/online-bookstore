/**
 * 防重复提交：同一动作在进行中时，后续触发直接丢弃。
 *
 * 为什么需要它：
 *   APP 是触屏，连点概率低；H5 用鼠标，双击 / 回车连发非常容易触发，
 *   而后端 `OrderServiceImpl.create()` 没有幂等键（毕设范围内不改协议），
 *   重复请求会真的生成两笔订单。前端必须自己挡住。
 *
 * 为什么收敛成公共模块而不是每页写 `submitting` ref：
 *   加购 / 立即购买这类动作分散在首页、分类、榜单、详情页、低价优选页，
 *   逐个页面加锁必然漏掉某一处；收敛到 common/ 后所有调用方自动受益。
 *
 * 用法：
 *   const res = await once('cart:add', () => addToCart(...))
 *   if (res === undefined) return   // 被丢弃，说明上一次还没结束
 */
const running = new Set()

/**
 * 串行执行：key 相同的调用，未结束时再触发直接返回 undefined。
 *
 * @param {string} key  动作标识，按「动作:对象」命名，如 'cart:add' / 'order:create'
 * @param {Function} fn 实际动作，返回 Promise 或任意值
 * @returns {Promise<any>} 被丢弃时返回 undefined；否则返回 fn 的结果
 */
export async function once(key, fn) {
  if (running.has(key)) return undefined
  running.add(key)
  try {
    return await fn()
  } finally {
    running.delete(key)
  }
}
