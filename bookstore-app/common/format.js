/**
 * 展示层格式化工具。
 *
 * 后端 BigDecimal / LocalDateTime 在不同序列化配置下可能表现为数字、字符串或数组
 * （如 LocalDateTime 可能被 Jackson 序列化为 [2026,10,16,10,30,0]），
 * 这里统一兜底，避免真机上出现 undefined / [object Object]。
 */

/** 金额：统一输出两位小数字符串 */
export function price(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = typeof v === 'number' ? v : Number(v)
  if (isNaN(n)) return String(v)
  return n.toFixed(2)
}

function pad(n) {
  return String(n).length < 2 ? '0' + n : String(n)
}

/** 时间：输出 yyyy-MM-dd HH:mm */
export function dateText(v) {
  if (!v) return ''
  // LocalDateTime 被序列化为数组的场景
  if (Array.isArray(v)) {
    if (v.length < 3) return ''
    const y = v[0]
    const mo = pad(v[1])
    const d = pad(v[2])
    if (v.length >= 5) return `${y}-${mo}-${d} ${pad(v[3])}:${pad(v[4])}`
    return `${y}-${mo}-${d}`
  }
  const s = String(v).replace('T', ' ')
  // 去掉秒与毫秒
  return s.length >= 16 ? s.slice(0, 16) : s
}

/** 手机号脱敏：138****8000 */
export function maskPhone(p) {
  const s = String(p || '')
  return s.length === 11 ? s.slice(0, 3) + '****' + s.slice(7) : s
}
