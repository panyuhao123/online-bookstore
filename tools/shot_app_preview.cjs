/**
 * 逐页截取 docs/ui-design-preview.html 的设计稿预览（CDP 直连，无需 puppeteer）。
 *
 * 用途：APP 是 uni-app（HBuilderX 打包），命令行无法直接渲染 SFC；
 *      改完 uni.scss 的 Token 后，用这份等价 CSS 变量稿复核视觉是否自洽。
 *
 * 用法：node tools/shot_app_preview.cjs
 * 输出：tools/.tmp/shot/p1..p7.png
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PAGE = 'file:///D:/workbuddy123/线上书店/docs/ui-design-preview.html'
const PORT = 9337
const OUT = path.join(__dirname, '.tmp', 'shot')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function waitDevTools() {
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      if (res.ok) return await res.json()
    } catch (e) {
      /* 还没起来 */
    }
    await sleep(250)
  }
  throw new Error('devtools 未就绪')
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const proc = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      '--hide-scrollbars',
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${path.join(__dirname, '.tmp', 'chrome-shot-prof')}`,
      '--window-size=1440,1000',
      PAGE
    ],
    { stdio: 'ignore' }
  )

  try {
    await waitDevTools()
    const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
    const page = list.find((t) => t.type === 'page')
    if (!page) throw new Error('找不到页面 target')

    const ws = new WebSocket(page.webSocketDebuggerUrl)
    let id = 0
    const pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && pending.has(msg.id)) {
        pending.get(msg.id)(msg.result)
        pending.delete(msg.id)
      }
    })
    await new Promise((r) => ws.addEventListener('open', r))
    const send = (method, params = {}) =>
      new Promise((resolve) => {
        const mid = ++id
        pending.set(mid, resolve)
        ws.send(JSON.stringify({ id: mid, method, params }))
      })

    await send('Page.enable')
    await send('Runtime.enable')
    await sleep(600)

    for (let i = 1; i <= 7; i++) {
      await send('Runtime.evaluate', {
        expression: `document.querySelector('[data-p="p${i}"]').click()`,
        awaitPromise: false
      })
      await sleep(320)
      const shot = await send('Page.captureScreenshot', { format: 'png' })
      fs.writeFileSync(path.join(OUT, `p${i}.png`), Buffer.from(shot.data, 'base64'))
      console.log(`p${i}.png`)
    }
    ws.close()
  } finally {
    proc.kill()
  }
}

main().catch((e) => {
  console.error('截图失败：', e.message)
  process.exit(1)
})
