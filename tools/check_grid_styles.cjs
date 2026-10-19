/**
 * 检查桌面 grid 布局的实际计算样式。
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = 'http://localhost:5176'
const PORT = 9338

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
  const listeners = []
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg)
      pending.delete(msg.id)
    } else if (msg.method) {
      listeners.forEach((fn) => fn(msg))
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
    on(fn) { listeners.push(fn) },
    close() { ws.close() },
    async eval(expression) {
      const msg = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
      return msg.result
    }
  }
}

async function main() {
  const userDir = path.join(os.tmpdir(), 'cb_grid_styles_' + Date.now())
  fs.mkdirSync(userDir, { recursive: true })
  const chrome = spawn(CHROME, [
    '--headless=new',
    '--no-sandbox',
    '--disable-gpu',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${userDir}`,
    '--window-size=1440,900'
  ], { detached: false })

  try {
    if (!(await waitDevTools())) throw new Error('Chrome DevTools 未就绪')
    const tab = await newTab(APP + '/#/pages/index/index')
    const client = makeClient(tab.webSocketDebuggerUrl)
    await client.ready
    await client.send('Page.enable')
    await client.send('Runtime.enable')
    await sleep(6000)

    const info = await client.eval(`(() => {
      const books = document.querySelector('.grid-books')
      const cell = document.querySelector('.grid-cell')
      if (!books || !cell) return { err: 'not found' }
      const bs = getComputedStyle(books)
      const cs = getComputedStyle(cell)
      return {
        viewportWidth: window.innerWidth,
        booksWidth: Math.round(books.getBoundingClientRect().width),
        booksDisplay: bs.display,
        gridTemplateColumns: bs.gridTemplateColumns,
        gap: bs.gap,
        cellWidth: Math.round(cell.getBoundingClientRect().width),
        cellStyleWidth: cs.width,
        cellMinWidth: cs.minWidth,
        cellDisplay: cs.display,
        bookCardWidth: Math.round((cell.querySelector('.book-card') || {}).getBoundingClientRect?.().width || 0)
      }
    })()`)

    console.log('首页 grid 计算样式：')
    console.log(JSON.stringify(info, null, 2))
  } catch (e) {
    console.error('ERR', e.message)
  } finally {
    await sleep(300)
    chrome.kill()
    try { fs.rmSync(userDir, { recursive: true, force: true }) } catch (e) {}
  }
}

main().catch((e) => { console.error(e); process.exit(1) })
