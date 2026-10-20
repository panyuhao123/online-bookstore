/**
 * 管理端逐页截图（视觉核对用，不属于门禁）。
 *
 * 为什么单独做：`verify_admin_ui.cjs` 断言的是「接口通、行数对、按钮能点」，
 * 它看不见「菜单分组塌成一列」「主色只改了按钮、标签还是蓝的」「KPI 卡里的色晕盖住数字」
 * 这类**能跑但难看**的问题。改版类需求必须落到像素上核对一遍。
 *
 * 覆盖：首页总览 / 订单管理 / 轮播图 / 图书 / 评价 / 系统设置 / 到货提醒 + 侧栏折叠态。
 * 用法：后端 8080 + 管理端 dev 5173 已启动，然后
 *   node tools/shot_admin_pages.cjs
 * 产物：tools/out/admin-*.png
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9341
const WEB = 'http://localhost:5173'
const API = 'http://127.0.0.1:8080'
const OUT = path.join(__dirname, 'out')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function adminLogin() {
  const r = await fetch(API + '/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' })
  })
  const js = await r.json()
  const token = (js.data || {}).token
  if (!token) throw new Error('登录失败 ' + JSON.stringify(js))
  return { token, admin: (js.data || {}).admin || {} }
}

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

function makeClient(wsUrl) {
  const ws = new WebSocket(wsUrl)
  let seq = 0
  const pending = new Map()
  const send = (method, params = {}) =>
    new Promise((resolve) => {
      const id = ++seq
      pending.set(id, resolve)
      ws.send(JSON.stringify({ id, method, params }))
    })
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg)
      pending.delete(msg.id)
    }
  }
  return {
    ready: new Promise((r) => (ws.onopen = r)),
    send,
    async eval(expression) {
      const msg = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
      if (msg.result && msg.result.exceptionDetails) return '__ERR__ ' + msg.result.exceptionDetails.text
      const res = msg.result && msg.result.result
      return res ? res.value : undefined
    },
    async goto(hash, waitMs = 3000) {
      await this.eval(`location.href = ${JSON.stringify(hash)}`)
      await sleep(waitMs)
    },
    async shot(file) {
      const metrics = await send('Page.getLayoutMetrics')
      const size = (metrics.result || {}).cssContentSize || { width: 1440, height: 900 }
      const msg = await send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: true,
        clip: { x: 0, y: 0, width: size.width, height: Math.min(size.height, 4000), scale: 1 }
      })
      const data = ((msg.result || {}).data || '')
      fs.writeFileSync(file, Buffer.from(data, 'base64'))
      console.log('saved', file, size.width + 'x' + Math.round(size.height))
    },
    close: () => ws.close()
  }
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const { token, admin } = await adminLogin()
  const profile = path.join(__dirname, 'chrome-' + Date.now())
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--remote-debugging-port=' + PORT,
      '--window-size=1440,1000',
      '--hide-scrollbars',
      '--no-first-run',
      '--no-default-browser-check',
      '--user-data-dir=' + profile,
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  const ok = await waitDevTools()
  if (!ok) throw new Error('Chrome 未就绪')

  const tab = await (await fetch(`http://localhost:${PORT}/json/new?about:blank`, { method: 'PUT' })).json()
  const client = makeClient(tab.webSocketDebuggerUrl)
  await client.ready
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false
  })

  // 先落到同源页面，再写 localStorage（否则 origin 不对，写不进去）
  await client.send('Page.navigate', { url: WEB + '/#/login' })
  await sleep(2500)
  await client.eval(`
    localStorage.setItem('bookstore_admin_token', ${JSON.stringify(token)});
    localStorage.setItem('bookstore_admin_info', ${JSON.stringify(JSON.stringify(admin))});
    localStorage.setItem('bookstore_admin_base_url', ${JSON.stringify(API)});
    'ok'
  `)

  const pages = [
    ['#/dashboard', 'admin-dash.png', 6000],
    ['#/order', 'admin-order.png', 5000],
    ['#/banner', 'admin-banner.png', 4000],
    ['#/book', 'admin-book.png', 5000],
    ['#/review', 'admin-review.png', 4500],
    ['#/setting', 'admin-setting.png', 4500],
    ['#/stock-notice', 'admin-stock.png', 4500]
  ]
  for (const [hash, file, wait] of pages) {
    await client.goto(hash, wait)
    await client.shot(path.join(OUT, file))
  }

  // 折叠态单独看一眼
  await client.goto('#/dashboard', 3000)
  await client.eval(`(() => { const b = document.querySelector('.collapse-btn'); if (b) b.click(); return 'ok' })()`)
  await sleep(1200)
  await client.shot(path.join(OUT, 'admin-collapsed.png'))

  // 最后看一眼登录页（清掉令牌，否则守卫会把人弹回 dashboard）
  await client.eval(`localStorage.clear(); 'ok'`)
  await client.goto('#/login', 2500)
  await client.shot(path.join(OUT, 'admin-login.png'))

  const errs = await client.eval(`JSON.stringify(window.__errs || [])`)
  console.log('page errors:', errs)

  client.close()
  chrome.kill()
  await sleep(500)
  fs.rmSync(profile, { recursive: true, force: true })
})().catch((e) => {
  console.error('FAILED', e)
  process.exit(1)
})
