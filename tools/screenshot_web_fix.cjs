/**
 * 快速截取 PC 版式修复后的关键页面（CDP 驱动 Chrome）。
 *
 * 用法：启动后端（8080）与 H5 dev server（5175）后执行
 *   node tools/screenshot_web_fix.cjs
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = 'http://localhost:5175'
const PORT = 9335
const OUT = path.join(__dirname, 'web_fix_screenshots')

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

async function newTab(url) {
  const r = await fetch(`http://localhost:${PORT}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' })
  return r.json()
}

function makeClient(wsUrl) {
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
  return {
    ready: new Promise((r) => (ws.onopen = r)),
    send,
    async eval(expression) {
      const msg = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
      return msg.result
    },
    async screenshot() {
      const msg = await send('Page.captureScreenshot', { format: 'png' })
      return msg.result.data
    },
    async navigate(url) {
      await send('Page.navigate', { url })
      await sleep(800)
    },
    async setViewport(width, height) {
      await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
    }
  }
}

async function main() {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })

  const chrome = spawn(CHROME, [
    '--headless=new',
    '--no-sandbox',
    '--disable-gpu',
    `--remote-debugging-port=${PORT}`,
    '--window-size=1440,900'
  ], { detached: false })

  try {
    if (!(await waitDevTools())) throw new Error('Chrome DevTools 未就绪')

    const { webSocketDebuggerUrl } = await (await fetch(`http://localhost:${PORT}/json/version`)).json()
    const client = makeClient(webSocketDebuggerUrl)
    await client.ready
    await client.send('Page.enable')

    await client.setViewport(1440, 900)

    const shots = [
      { name: 'home', url: `${APP}/#/pages/index/index`, wait: 1200 },
      { name: 'new', url: `${APP}/#/pages/book/new`, wait: 1200 },
      { name: 'cheap', url: `${APP}/#/pages/book/cheap`, wait: 1200 },
      { name: 'category', url: `${APP}/#/pages/category/category`, wait: 1200 },
      { name: 'list', url: `${APP}/#/pages/book/list?sort=sales&title=${encodeURIComponent('热销榜')}`, wait: 1200 }
    ]

    for (const s of shots) {
      await client.navigate(s.url)
      await sleep(s.wait)
      const data = await client.screenshot()
      fs.writeFileSync(path.join(OUT, `${s.name}.png`), Buffer.from(data, 'base64'))
      console.log(`[OK] ${s.name}.png`)
    }

    console.log(`\n截图已保存到 ${OUT}`)
  } finally {
    try { chrome.kill() } catch (e) {}
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
