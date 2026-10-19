import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getFreightRule } from '@/api/order'

/** 金额转「分」，整数比较，避免 0.1 + 0.2 那类浮点误差出现在包邮判定上 */
function toCents(value) {
  const num = Number(value)
  if (!isFinite(num)) return 0
  return Math.round(num * 100)
}

/**
 * 运费规则缓存。
 *
 * <p>为什么要缓存：图书卡片要显示「包邮」徽标、结算页要显示「再买 X 元免运费」，
 * 一次列表页就有几十张卡片，逐张去请求规则显然不行。这里只在**会话内存**里存，
 * 不做 localStorage 持久化 —— 后台把门槛改掉之后，用户下次进页面拿到的就是新规则；
 * 如果持久化了，用户会拿着上个月的规则来质疑结算金额。
 *
 * <p>包邮判定一律在「分」上做整数比较：运费门槛是 `99.00` 这种两位小数，
 * 用浮点比大小会在 `98.99999999` 这类边界上判错，而这是用户最容易较真的地方。
 */
export const useFreightStore = defineStore('freight', () => {
  const rule = ref(null)
  const loading = ref(false)

  const enabled = computed(() => rule.value?.enabled !== false)
  const freeThreshold = computed(() => Number(rule.value?.freeThreshold ?? 0))
  const baseFee = computed(() => Number(rule.value?.baseFee ?? 0))
  const tip = computed(() => rule.value?.tip || '')

  /** 拉一次规则（已拉到则直接返回，需要强制刷新时传 true） */
  async function ensure(force = false) {
    if (rule.value && !force) return rule.value
    if (loading.value) return rule.value
    loading.value = true
    try {
      rule.value = await getFreightRule()
    } catch (e) {
      // 规则拿不到时按「不包邮、运费 0」降级：宁可少收也不要在界面上凭空多出一笔钱，
      // 真实运费以提交后服务端返回的 payableAmount 为准。
      rule.value = { enabled: false, freeThreshold: 0, baseFee: 0, tip: '' }
    } finally {
      loading.value = false
    }
    return rule.value
  }

  /** 该金额是否包邮 */
  function isFreeShipping(goodsAmount) {
    if (!enabled.value) return true
    return toCents(goodsAmount) >= toCents(freeThreshold.value)
  }

  /** 该金额对应的运费（包邮为 0） */
  function feeOf(goodsAmount) {
    if (!enabled.value) return 0
    return isFreeShipping(goodsAmount) ? 0 : baseFee.value
  }

  /** 距离包邮还差多少（已包邮或缺省返回 0） */
  function amountToFreeShipping(goodsAmount) {
    if (!enabled.value) return 0
    const diff = toCents(freeThreshold.value) - toCents(goodsAmount)
    return diff > 0 ? diff / 100 : 0
  }

  return {
    rule,
    loading,
    enabled,
    freeThreshold,
    baseFee,
    tip,
    ensure,
    isFreeShipping,
    feeOf,
    amountToFreeShipping
  }
})
