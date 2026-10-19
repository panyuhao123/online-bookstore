/**
 * Web 端桌面版式（1440×900）截图 + 布局测量脚本（CDP 驱动无头 Chrome，零依赖）。
 *
 * 用途：改造前后对同一组页面做像素级回归——既存截图，又输出关键元素的
 * getBoundingClientRect + getComputedStyle，判断「顶栏与内容区是否对齐 /
 * 分栏比例 / 网格列宽 / 有无横向溢出」。
 *
 * 用法：先启动 H5 dev server（5176）与后端（8080），再执行
 *   node tools/web_measure.cjs [cur|new]
 *   cur（默认）产物前缀 web_cur_，new 产物前缀 web_new_
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = 'http://localhost:5176'
const PORT = 9334
const TOOLS = __dirname
const PREFIX = process.argv[2] === 'new' ? 'web_new' : 'web_cur'
const PHONE = '13800138000'
const PASSWORD = '123456'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function waitDevTools() {
  for (let i = 0; i < 40; i++) {
    try {
      const r = await fetch(`http://localhost:${PORT}/json/version`)
      if (r.ok) return true
    } catch (e) {}
    await sleep(500)
  }
  return false
}

async function cdp(wsUrl) {
  const ws = new WebSocket(wsUrl)
  let seq = 0
  const pending = new Map()
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg)
      pending.delete(msg.id)
    }
  }
  const send = (method, params = {}) =>
    new Promise((resolve) => {
      const id = ++seq
      pending.set(id, resolve)
      ws.send(JSON.stringify({ id, method, params }))
    })
  await new Promise((r) => (ws.onopen = r))
  await send('Page.enable')
  await send('Runtime.enable')
  return {
    async eval(expression) {
      const msg = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
      if (msg.exceptionDetails) return { __error: msg.exceptionDetails.text }
      return msg.result && msg.result.result ? msg.result.result.value : undefined
    },
    async shot(name) {
      const msg = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
      if (msg.result && msg.result.data) {
        fs.writeFileSync(path.join(TOOLS, name), Buffer.from(msg.result.data, 'base64'))
      }
    },
    close: () => ws.close()
  }
}

async function openTab(hashUrl) {
  const r = await fetch(`http://localhost:${PORT}/json/new?${encodeURIComponent(APP + '/#' + hashUrl)}`, { method: 'PUT' })
  return r.json()
}

async function closeTab(tab) {
  try {
    await fetch(`http://localhost:${PORT}/json/close/${tab.id}`)
  } catch (e) {}
}

const ENV_JS = `JSON.stringify({
  innerW: window.innerWidth, innerH: window.innerHeight, dpr: window.devicePixelRatio,
  mq960: window.matchMedia('(min-width:960px)').matches,
  mq1280: window.matchMedia('(min-width:1280px)').matches,
  scrollW: document.documentElement.scrollWidth, clientW: document.documentElement.clientWidth,
  hash: location.hash
})`

function measureJs(sels) {
  return `JSON.stringify((() => {
    const out = {}
    for (const s of ${JSON.stringify(sels)}) {
      const el = document.querySelector(s)
      if (!el) { out[s] = null; continue }
      const r = el.getBoundingClientRect()
      const cs = getComputedStyle(el)
      out[s] = {
        w: Math.round(r.width), h: Math.round(r.height), x: Math.round(r.left), r: Math.round(r.right), y: Math.round(r.top),
        display: cs.display, width: cs.width, minW: cs.minWidth, flex: cs.flex, gap: cs.gap,
        gridCols: cs.gridTemplateColumns, justify: cs.justifyContent
      }
    }
    return out
  })())`
}

const LOGIN_JS = `new Promise((resolve) => {
  const set = (el, v) => {
    const d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    d.call(el, v)
    el.dispatchEvent(new Event('input', { bubbles: true }))
    el.dispatchEvent(new Event('change', { bubbles: true }))
  }
  const vis = [...document.querySelectorAll('input')].filter((e) => e.offsetParent !== null && e.type !== 'hidden')
  const pwd = vis.find((e) => e.type === 'password')
  const phone = pwd ? vis[vis.indexOf(pwd) - 1] || vis[0] : vis[0]
  if (!pwd || !phone) return resolve('inputs=' + vis.length)
  set(phone, '${PHONE}')
  set(pwd, '${PASSWORD}')
  setTimeout(() => {
    const cands = [...document.querySelectorAll('view,button,div,span')].filter((e) => e.offsetParent !== null)
    const btn = cands.find((e) => e.textContent.trim() === '登录' && e.className && String(e.className).includes('btn'))
      || cands.reverse().find((e) => e.textContent.trim() === '登录')
    if (btn) btn.click()
    setTimeout(() => resolve(localStorage.getItem('bookstore_token') ? 'token-ok' : 'no-token'), 3000)
  }, 300)
})`

const PAGES = [
  { name: 'home', hash: 'pages/index/index', wait: 7000,
    sels: ['.wh-inner', '.page', '.web-hero', '.web-hero-side', '.web-hero-main', '.web-hero-extra', '.floor', '.grid-books', '.grid-cell', '.grid-cell .book-card'] },
  { name: 'detail', hash: 'pages/book/detail?id=10001', wait: 7000,
    sels: ['.page', '.detail-body', '.cover-area', '.cover-img', '.right-col', '.info-card', '.float-bar', '.bar-holder'] },
  { name: 'cart', hash: 'pages/cart/cart', wait: 6000,
    sels: ['.page', '.list-card', '.row', '.thumb', '.row-main', '.settle-bar', '.state-box', '.local-tip'] },
  { name: 'category', hash: 'pages/category/category', wait: 7000,
    sels: ['.page', '.web-side', '.web-body', '.filter-bar', '.grid-books', '.grid-cell'] },
  { name: 'list', hash: 'pages/book/list?keyword=' + encodeURIComponent('java'), wait: 7000,
    sels: ['.page', '.sort-bar', '.grid-books', '.grid-cell', '.bc'] }
]

async function main() {
  if (!fs.existsSync(CHROME)) throw new Error('未找到 Chrome：' + CHROME)
  const userDir = path.join(os.tmpdir(), 'bookstore-web-measure-profile')
  fs.rmSync(userDir, { recursive: true, force: true })
  const chrome = spawn(
    CHROME,
    ['--headless=new', '--disable-gpu', '--no-sandbox', `--user-data-dir=${userDir}`,
     `--remote-debugging-port=${PORT}`, '--window-size=1440,900', 'about:blank'],
    { stdio: 'ignore' }
  )

  const report = {}
  try {
    if (!(await waitDevTools())) throw new Error('Chrome 调试端口未就绪（先启动 H5 dev 5176）')

    // 先在独立标签完成登录（localStorage 同源共享），后续购物车等页即为登录态
    const loginTab = await openTab('pages/login/login')
    const lc = await cdp(loginTab.webSocketDebuggerUrl)
    await sleep(5000)
    report.__login = await lc.eval(LOGIN_JS)
    await lc.shot(`${PREFIX}_login.png`)
    lc.close()
    await closeTab(loginTab)
    await sleep(800)

    for (const p of PAGES) {
      const tab = await openTab(p.hash)
      const c = await cdp(tab.webSocketDebuggerUrl)
      await sleep(p.wait)
      const env = JSON.parse(await c.eval(ENV_JS))
      const boxes = JSON.parse(await c.eval(measureJs(p.sels)))
      report[p.name] = { env, boxes }
      await c.shot(`${PREFIX}_${p.name}.png`)
      console.log(`[shot] ${PREFIX}_${p.name}.png  hash=${env.hash}`)
      c.close()
      await closeTab(tab)
      await sleep(300)
    }
  } finally {
    chrome.kill()
  }
  fs.writeFileSync(path.join(TOOLS, `${PREFIX}_measure.json`), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
