import { formatDateTime, orderStatusText } from '@/utils/format'

/**
 * 导出数据为 Excel 文件。
 * @param {Array} rows 数据行（对象数组）
 * @param {Array} columns [{prop, label, width?}] 列定义
 * @param {string} fileName 导出文件名（不含扩展名）
 */
export async function exportToExcel(rows, columns, fileName = 'export') {
  // 动态导入：只在点击导出时才加载 xlsx，避免首屏加载 280KB
  const XLSX = await import('xlsx')

  const header = columns.map((c) => c.label)
  const data = rows.map((row) =>
    columns.map((c) => {
      const val = row[c.prop]
      return val === null || val === undefined ? '' : val
    })
  )
  const ws = XLSX.utils.aoa_to_sheet([header, ...data])
  ws['!cols'] = columns.map((c) => ({ wch: c.width || 16 }))
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  XLSX.writeFile(wb, fileName + '.xlsx')
}

/**
 * 导出订单小票（N8）。
 *
 * <p>做成 Excel 而不是 PDF：xlsx 已经在用了，不引新依赖；而且用户拿到之后
 * 还能自己改格式、并进自己的记账表。小票是"一次性凭证"，做成不可编辑的 PDF
 * 对个人用户反而是负担。
 *
 * <p>结构：标题 → 订单信息 → 明细表 → 合计。合计拆成「商品小计 / 运费 / 应付」
 * 三行，和页面上看到的口径一致 —— 只写一个总数，用户对不上账会来回问。
 *
 * @param {object} order OrderVO（含 items / freightAmount / payableAmount）
 * @param {string} [fileName]
 */
export async function exportReceipt(order, fileName) {
  if (!order) return
  const XLSX = await import('xlsx')

  const cols = [
    { label: '书名', width: 36 },
    { label: '单价（元）', width: 12 },
    { label: '数量', width: 8 },
    { label: '小计（元）', width: 12 }
  ]
  const num = (v) => Number(v || 0)

  const rows = [
    ['线上书店 · 购物小票'],
    [],
    ['订单号', order.orderNo || ''],
    ['下单时间', formatDateTime(order.createTime)],
    ['订单状态', order.statusText || orderStatusText(order.status)],
    ['收货信息', order.addressSnapshot || ''],
    [],
    cols.map((c) => c.label),
    ...(order.items || []).map((i) => [
      i.bookTitle || '',
      num(i.price),
      i.quantity || 0,
      num(i.subtotal)
    ]),
    [],
    ['商品小计', '', '', num(order.totalAmount)],
    ['运费', '', '', num(order.freightAmount)],
    ['应付金额', '', '', num(order.payableAmount ?? order.totalAmount)]
  ]

  const ws = XLSX.utils.aoa_to_sheet(rows)
  ws['!cols'] = cols.map((c) => ({ wch: c.width }))
  // 标题跨列居中，看起来才像张小票
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]

  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '小票')
  XLSX.writeFile(wb, (fileName || `小票_${order.orderNo || order.id}`) + '.xlsx')
}
