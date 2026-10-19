/**
 * 封面地址解析。
 *
 * 后端返回的 coverUrl：
 *   - 真实封面为 /files/cover/xxx.jpg（相对路径，由 vite proxy 转发）
 *   - 演示数据可能为 /static/cover/xxx.jpg（uni-app 本地资源，Web 端不可用）
 *
 * Web 端统一走后端 /files/ 路径；若为空返回兜底占位。
 */
export function bookCover(book) {
  if (!book) return ''
  const url = book.coverUrl || book.cover || book.bookCoverUrl || book.bookCover || ''
  if (!url) return ''
  // 已是 /files/ 开头或 http 开头直接用
  if (url.startsWith('/files/') || url.startsWith('http')) return url
  // 演示数据的 /static/cover/ 路径在 Web 端不存在，走兜底
  if (url.startsWith('/static/')) return ''
  return url
}

/** 兜底占位图（SVG 渐变 + 书名首字） */
export function placeholderCover(title) {
  const char = title ? title.charAt(0) : '书'
  const colors = [
    ['#c8362f', '#e8a598'],
    ['#2c3e50', '#8e9eab'],
    ['#16a085', '#a8e6cf'],
    ['#8e44ad', '#d2b4de'],
    ['#d35400', '#f5cba7']
  ]
  const idx = char.charCodeAt(0) % colors.length
  const [c1, c2] = colors[idx]
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='240' height='320'>
    <defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
      <stop offset='0%' stop-color='${c1}'/><stop offset='100%' stop-color='${c2}'/>
    </linearGradient></defs>
    <rect width='240' height='320' fill='url(#g)'/>
    <text x='120' y='170' font-size='72' fill='white' text-anchor='middle' font-family='sans-serif' font-weight='bold'>${char}</text>
  </svg>`
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}
