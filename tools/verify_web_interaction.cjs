/**
 * Web 端「阶段 3：桌面交互增强」自测（CDP 驱动无头 Chrome，不依赖任何 npm 包）。
 *
 * 覆盖：
 *   1. 浏览器标签标题随路由变化（多标签场景，APP 无此概念）
 *   2. 登录页在密码框按回车即可提交（键盘提交）
 *   3. 桌面指针设备下可点元素有 pointer 光标（hover-class 走 touch，鼠标悬停必须靠 CSS 补）
 *   4. 快速连点「加购」只发一次请求（防重复提交，后端无幂等键）
 *
 * 用法：先启动 H5 dev server（5175）与后端（8080），再执行
 *   node tools/verify_web_interaction.cjs
 */
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = 'http://localhost:5175'
const API = 'http://127.0.0.1:8080'
const PORT = 9334
const PHONE = '13800138000'
const PASSWORD = '123456'

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
    on(fn) {
      listeners.push(fn)
    },
    async eval(expression) {
      const msg = await send('Runtime.evaluate', {
        expression,
        returnByValue: true,
        awaitPromise: true
      })
      return msg.result && msg.result.result ? msg.result.result.value : undefined
    },
    /** 派发真实按键，用于验证回车提交。
     *  注意：uni-app H5 的 confirm 事件由 keyup 触发（源码 onKeyUpEnter 判 event.key === 'Enter'），
     *  所以 keyDown 与 keyUp 必须成对派发，缺 keyUp 不会触发。 */
    async key(key) {
      const vk = { Enter: 13 }[key] || 0
      const base = {
        key,
        code: key,
        windowsVirtualKeyCode: vk,
        nativeVirtualKeyCode: vk,
        text: key === 'Enter' ? '\r' : key,
        unmodifiedText: key === 'Enter' ? '\r' : key
      }
      await send('Input.dispatchKeyEvent', Object.assign({ type: 'keyDown' }, base))
      await send('Input.dispatchKeyEvent', Object.assign({ type: 'keyUp' }, base))
    },
    close: () => ws.close()
  }
}

/**
 * 填入登录表单：直接改原生 value 并派发 input 事件，Vue 的 v-model 才会更新。
 *
 * 必须按 type 定位，不能按 inputs[0] / inputs[1] 下标：
 * 桌面视口下 page-nav 会叠加 web-header，页面上有三个 input（搜索框 / 手机号 / 密码），
 * 按下标填会把手机号写进搜索框（手机号视口只有两个 input，所以旧脚本没暴露这个问题）。
 */
const FILL_FORM = `(() => {
  const set = (el, v) => {
    const d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    d.call(el, v)
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }
  const phone = document.querySelector('uni-input input[type="number"]') || document.querySelector('input[type="number"]');
  const pwd = document.querySelector('uni-input input[type="password"]') || document.querySelector('input[type="password"]');
  if (!phone || !pwd) return 'phone=' + !!phone + ' pwd=' + !!pwd;
  set(phone, '${PHONE}');
  set(pwd, '${PASSWORD}');
  pwd.focus();
  return document.activeElement === pwd ? 'focused' : 'not-focused';
})()`

async function main() {
  const userDir = path.join(os.tmpdir(), 'bookstore-web-ix-profile')
  fs.rmSync(userDir, { recursive: true, force: true })
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      `--user-data-dir=${userDir}`,
      `--remote-debugging-port=${PORT}`,
      '--window-size=1440,900', // 桌面视口，才能命中 ≥960 的桌面布局
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  try {
    if (!fs.existsSync(CHROME)) throw new Error('未找到 Chrome：' + CHROME)
    if (!(await waitDevTools())) throw new Error('Chrome 调试端口未就绪')

    // ---------- 1. 标题随路由变化 ----------
    const t1 = await newTab(APP + '/#/pages/index/index')
    const c1 = makeClient(t1.webSocketDebuggerUrl)
    await c1.ready
    await c1.send('Page.enable')
    await c1.send('Runtime.enable')
    await c1.send('Input.enable')
    await c1.send('Network.enable')
    const loginPosts = []
    c1.on((msg) => {
      if (msg.method === 'Network.requestWillBeSent') {
        const req = (msg.params && msg.params.request) || {}
        if (String(req.url || '').includes('/api/user/login') && req.method === 'POST') {
          loginPosts.push(req.url)
        }
      }
    })
    await sleep(4500)
    const titleHome = String((await c1.eval('document.title')) || '')
    check('首页标签标题带页面名', titleHome.includes('首页'), titleHome)

    // 用免登录页测标题，避免被登录守卫改写 URL（受保护页未登录会 reLaunch 到登录页）
    await c1.eval("location.href = '#/pages/category/category'")
    await sleep(3500)
    const titleCat = String((await c1.eval('document.title')) || '')
    const hrefCat = String((await c1.eval('location.href')) || '')
    check('切页后标题同步', titleCat.includes('分类'), titleCat + ' @ ' + hrefCat)

    // ---------- 2. 回车提交登录 ----------
    await c1.eval("location.href = '#/pages/login/login'")
    await sleep(3000)
    const filled = String((await c1.eval(FILL_FORM)) || '')
    await sleep(300)
    const focused = String(
      (await c1.eval(
        `document.activeElement && document.activeElement.type === 'password' ? 'focused' : 'not-focused'`
      )) || ''
    )
    await sleep(200)
    await c1.key('Enter')
    await sleep(5000)
    const urlAfterEnter = String((await c1.eval('location.href')) || '')
    const tokenAfterEnter = String((await c1.eval("localStorage.getItem('bookstore_token')")) || '')
    check(
      '登录页密码框回车可提交',
      !!tokenAfterEnter && !urlAfterEnter.includes('/pages/login/login'),
      `填表=${filled} 焦点=${focused} token=${tokenAfterEnter ? '有' : '无'} ` +
        `login请求=${loginPosts.length} url=${urlAfterEnter}`
    )
    c1.close()

    // ---------- 3 & 4：桌面悬停 + 防重复提交 ----------
    const lr = await (await fetch(API + '/api/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: PHONE, password: PASSWORD })
    })).json()
    const token = lr && lr.data && lr.data.token
    if (!token) throw new Error('取不到登录 token')

    const t2 = await newTab(APP + '/#/pages/index/index')
    const c2 = makeClient(t2.webSocketDebuggerUrl)
    await c2.ready
    await c2.send('Page.enable')
    await c2.send('Runtime.enable')
    await sleep(3500)
    // 注入登录态，避免走表单
    await c2.eval(`localStorage.setItem('bookstore_token', ${JSON.stringify(token)})`)
    await c2.eval("location.href = '#/pages/book/cheap'")
    await sleep(5000)

    // 3. 桌面指针设备下的光标形态
    const mq = await c2.eval("matchMedia('(hover: hover) and (pointer: fine)').matches")
    const cursor = String(
      (await c2.eval(`(() => {
        const el = document.querySelector('.book-card .act.outline') || document.querySelector('.book-card');
        return el ? getComputedStyle(el).cursor : 'no-element';
      })()`)) || ''
    )
    check('桌面指针设备媒体查询命中', mq === true, String(mq))
    check('可点元素有 pointer 光标', cursor === 'pointer', cursor)

    // 4. 连点加购只发一次请求：监听真实网络请求，比看 Toast 更可靠
    const cartPosts = []
    const allApi = []
    await c2.send('Network.enable')
    c2.on((msg) => {
      if (msg.method === 'Network.requestWillBeSent') {
        const req = (msg.params && msg.params.request) || {}
        const u = String(req.url || '')
        if (u.includes('/api/')) allApi.push(req.method + ' ' + u.replace(/^https?:\/\/[^/]+/, ''))
        if (u.includes('/api/cart') && req.method === 'POST') cartPosts.push(u)
      }
    })

    // 确认注入的 token 被页面识别（顶栏「我的」= 已登录，「登录」= 未登录）
    const mineText = String(
      (await c2.eval(`(() => {
        const el = [...document.querySelectorAll('.wh-top-link')].pop();
        return el ? el.innerText.trim() : 'no-header';
      })()`)) || ''
    )
    check('注入的 token 被页面识别为已登录', mineText === '个人中心', '顶栏=' + mineText)

    // 快速连点两次「加购」——必须选有库存的卡片，售罄卡片会直接 return，测不出防重
    const clicked = String(
      (await c2.eval(`(() => {
        const btns = [...document.querySelectorAll('.book-card .act.outline')];
        const btn = btns.find((b) => (b.innerText || '').trim() === '加购');
        if (!btn) return 'no-available-btn(共' + btns.length + '个)';
        btn.click(); btn.click();
        return 'clicked';
      })()`)) || ''
    )
    await sleep(4500)
    c2.close()

    check(
      '连点两次加购只发一次请求',
      cartPosts.length === 1,
      `按钮=${clicked} POST /api/cart=${cartPosts.length} 全部API=${JSON.stringify(allApi.slice(0, 6))}`
    )
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
  console.log(`\n===== Web 桌面交互自测：通过 ${results.length - failed} / 失败 ${failed} =====`)
  process.exit(failed ? 1 : 0)
}

main()
