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
