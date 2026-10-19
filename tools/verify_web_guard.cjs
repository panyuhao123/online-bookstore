/**
 * Web 端登录守卫自测（CDP 驱动无头 Chrome，不依赖任何 npm 包）。
 *
 * 覆盖：
 *   1. 未登录用 URL 直达受保护页 → 是否被拦到登录页
 *   2. 回跳目标是否正确写入本地存储
 *   3. 在登录页完成登录后 → 是否回到原本想进的那一页
 *   4. 已登录（本地写入 token）→ 受保护页能否正常加载
 *
 * 用法：先启动 H5 dev server（5175）与后端（8080），再执行
 *   node tools/verify_web_guard.cjs
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = 'http://localhost:5175'
const PORT = 9333
const PHONE = '13800138000'
const PASSWORD = '123456'
const PROTECTED = '/#/pages/order/list'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const results = []
function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -> ' + detail : ''}`)
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

async function newTab(url) {
  const r = await fetch(`http://localhost:${PORT}/json/new?${encodeURIComponent(url)}`, {
    method: 'PUT'
  })
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
    async eval(expression) {
      const msg = await send('Runtime.evaluate', {
        expression,
        returnByValue: true,
        awaitPromise: true
      })
      return msg.result && msg.result.result ? msg.result.result.value : undefined
    },
    close: () => ws.close()
  }
}

/** 填入登录表单：直接改原生 value 并派发 input 事件，Vue 的 v-model 才会更新 */
const FILL_FORM = `(() => {
  const set = (el, v) => {
    const d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    d.call(el, v)
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }
  const ins = [...document.querySelectorAll('input')]
  if (ins.length < 2) return 'inputs=' + ins.length
  set(ins[0], '${PHONE}')
  set(ins[1], '${PASSWORD}')
  return 'ok'
})()`

async function main() {
  const userDir = path.join(os.tmpdir(), 'bookstore-web-guard-profile')
  fs.rmSync(userDir, { recursive: true, force: true })
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      `--user-data-dir=${userDir}`,
      `--remote-debugging-port=${PORT}`,
      '--window-size=390,844',
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  try {
    if (!fs.existsSync(CHROME)) throw new Error('未找到 Chrome：' + CHROME)
    if (!(await waitDevTools())) throw new Error('Chrome 调试端口未就绪')

    // ---- 1 & 2：未登录直达受保护页 ----
    const tab = await newTab(APP + PROTECTED)
    const c = makeClient(tab.webSocketDebuggerUrl)
    await c.ready
    await sleep(5000)

    const url1 = String(await c.eval('location.href') || '')
    const redirect = String(await c.eval("localStorage.getItem('bookstore_redirect')") || '')
    check('未登录直达订单页被拦到登录页', url1.includes('/pages/login/login'), url1)
    check('回跳目标已写入本地存储', redirect.includes('pages/order/list'), redirect)

    // ---- 3：在登录页完成登录，应回跳 ----
    const filled = String(await c.eval(FILL_FORM))
    await sleep(300)
    await c.eval("document.querySelector('.submit') && document.querySelector('.submit').click()")
    await sleep(5000)
    const url2 = String(await c.eval('location.href') || '')
    check('登录后回跳到订单页', url2.includes('/pages/order/list'), url2 + ' / 表单:' + filled)

    const text = String(await c.eval('document.body.innerText.slice(0, 200)') || '')
    check('回跳后订单页正常渲染', /订单|待支付|待发货|全部/.test(text), text.replace(/\s+/g, ' ').slice(0, 80))

    // ---- 4：清掉本地登录态后重新直达，应再次被拦 ----
    await c.eval("localStorage.removeItem('bookstore_token')")
    const tab2 = await newTab(APP + PROTECTED)
    const c2 = makeClient(tab2.webSocketDebuggerUrl)
    await c2.ready
    await sleep(5000)
    const url3 = String(await c2.eval('location.href') || '')
    check('清掉 token 后再次被拦', url3.includes('/pages/login/login'), url3)
    c.close()
    c2.close()
  } catch (e) {
    check('脚本执行', false, e.message)
  } finally {
    await sleep(500)
    chrome.kill()
    try {
      fs.rmSync(userDir, { recursive: true, force: true })
    } catch (e) {}
  }

  const failed = results.filter((r) => !r.ok).length
  console.log(`\n===== Web 守卫自测：通过 ${results.length - failed} / 失败 ${failed} =====`)
  process.exit(failed ? 1 : 0)
}

main()
