/**
 * 端到端验证：桌面顶栏搜索框输入回车后跳转到列表页并携带关键词。
 * 使用 Chrome DevTools Protocol，Node 22 内置 fetch/WebSocket，无需第三方依赖。
 */
const CDP_PORT = 9223
const BASE = 'http://localhost:5175'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function cdpCall(ws, method, params = {}) {
  const id = Math.floor(Math.random() * 1e9)
  ws.send(JSON.stringify({ id, method, params }))
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('cdp timeout')), 8000)
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data.toString())
        if (msg.id === id) {
          ws.onmessage = null
          clearTimeout(timer)
          resolve(msg.result)
        }
      } catch (e) {}
    }
  })
}

async function main() {
  // 启动一个带调试端口的 Chrome
  const { spawn } = require('child_process')
  const chrome = spawn(
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      `--remote-debugging-port=${CDP_PORT}`,
      '--window-size=1440,900',
      'about:blank'
    ],
    { stdio: 'ignore' }
  )
  await sleep(1200)

  const list = await (await fetch(`http://localhost:${CDP_PORT}/json/list`)).json()
  const target = list.find((t) => t.type === 'page') || list[0]
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve) => (ws.onopen = resolve))

  await cdpCall(ws, 'Page.enable')
  await cdpCall(ws, 'Runtime.enable')
  await cdpCall(ws, 'Input.enable')

  // 打开首页
  await cdpCall(ws, 'Page.navigate', { url: `${BASE}/#/pages/index/index` })
  await sleep(2500)

  // 聚焦搜索框（uni-app 渲染成 .wh-input 内的真实 input）
  const focusRes = await cdpCall(ws, 'Runtime.evaluate', {
    expression: `
      const input = document.querySelector('.wh-input input') || document.querySelector('.wh-input .uni-input-input');
      if (input) { input.focus(); input.value = '活着'; input.dispatchEvent(new Event('input', { bubbles: true })); true; } else { false; }
    `,
    returnByValue: true
  })
  if (!focusRes || !focusRes.result || !focusRes.result.value) {
    throw new Error('未找到桌面搜索框')
  }
  await sleep(300)

  // 模拟回车
  await cdpCall(ws, 'Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', keyCode: 13 })
  await cdpCall(ws, 'Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', keyCode: 13 })
  await sleep(2000)

  // 验证 URL 与标题
  const urlRes = await cdpCall(ws, 'Runtime.evaluate', {
    expression: `location.href`,
    returnByValue: true
  })
  const href = (urlRes && urlRes.result && urlRes.result.value) || ''
  const titleRes = await cdpCall(ws, 'Runtime.evaluate', {
    expression: `document.body.innerText.includes('“活着” 的搜索结果')`,
    returnByValue: true
  })
  const hasTitle = !!(titleRes && titleRes.result && titleRes.result.value)

  await cdpCall(ws, 'Page.captureScreenshot', { format: 'png' }).then((res) => {
    if (res && res.data) {
      require('fs').writeFileSync('D:/workbuddy123/线上书店/tools/web_search_result.png', Buffer.from(res.data, 'base64'))
    }
  })

  ws.close()
  chrome.kill()

  console.log('当前 URL:', href)
  console.log('包含搜索结果标题:', hasTitle)
  if (!href.includes('keyword=') || !hasTitle) {
    process.exit(1)
  }
  console.log('桌面顶栏搜索验证通过')
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
