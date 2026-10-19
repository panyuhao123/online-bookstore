/** 金额格式化：分/元统一保留两位小数 */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = Number(value)
  if (isNaN(num)) return '0.00'
  return num.toFixed(2)
}

/** 带 ¥ 符号的金额 */
export function formatPrice(value) {
  return '¥' + formatMoney(value)
}

/** 订单状态码 -> 中文文案 */
export const ORDER_STATUS_TEXT = {
  1: '待支付',
  2: '已支付',
  3: '已发货',
  4: '已完成',
  5: '已取消'
}

export function orderStatusText(status) {
  return ORDER_STATUS_TEXT[status] || '未知'
}

/** 订单状态 -> Element Plus tag type */
export function orderStatusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: 'info', 4: 'success', 5: 'info' }
  return map[status] || 'info'
}

/** 评分显示：null 显示「暂无评分」 */
export function formatRating(rating) {
  if (rating === null || rating === undefined) return '暂无评分'
  return Number(rating).toFixed(1)
}

/** 手机号脱敏：138****8000 */
export function maskPhone(phone) {
  if (!phone || phone.length < 11) return phone || ''
  return phone.slice(0, 3) + '****' + phone.slice(7)
}

/** 时间格式化：YYYY-MM-DD HH:mm */
export function formatDateTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    ' ' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes())
  )
}
