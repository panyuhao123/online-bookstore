/**
 * 管理端（bookstore-admin，5173）新增页面的 UI 端到端自测 —— CDP 驱动无头 Chrome，不依赖任何 npm 包。
 *
 * 背景：W3 交付的是**纯前端**的四个页面（轮播图管理 / 书单治理 / 到货提醒 / 运费规则），
 *      接口在 W1、N10 已由 python 脚本覆盖，但「页面能不能真的用起来」此前没有任何自动化检查。
 *      历史教训（D-07）：`vite build` 通过 ≠ 页面能跑 —— 调错方法、写错字段、绑错事件
 *      一律要到运行时才暴露。本脚本补这个缺口。
 *
 * 覆盖：
 *   1. 管理员登录 → 令牌落 localStorage → 跳转 dashboard
 *   2. 侧边菜单出现三个新入口（轮播图管理 / 书单管理 / 到货提醒）
 *   3. 轮播图页：请求 /api/admin/banner/list 且渲染既有数据
 *   4. 轮播图页：**真实表单提交**新增 → 行数 +1 且后端存在 → UI 删除 → 行数复原
 *   5. 书单页：请求 /api/admin/booklist/page 且能看到用户真实创建的书单
 *   6. 书单页：详情抽屉渲染条目（含已下架图书）
 *   7. 到货提醒页：缺货图书的等待记录出现在看板 → 点「标记已通知」→ 该行从看板消失
 *   8. 系统设置：读回真实运费规则 → 改门槛保存 → PUT 命中正确键且页面回显新值 → 还原
 *   9. 全程无未捕获的 JS 异常
 *
 * 用法：先启动后端（8080）与管理端 dev server（5173），再执行
 *   node tools/verify_admin_ui.cjs
 *
 * 数据安全：脚本自己造数据、自己清（finally 里恢复库存 / 删除测试书单 / 删除测试轮播图 /
 *          运费门槛还原为 99）。不依赖 seed 里的既有数据被改动。
 */
const { spawn, execFileSync } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const REDIS_CLI = process.env.BOOKSTORE_REDIS_CLI || 'D:/redis5/redis-cli.exe'
const ADMIN_WEB = 'http://localhost:5173'
const API = 'http://127.0.0.1:8080'
const PORT = 9337
const ADMIN_USER = 'admin'
const ADMIN_PASSWORD = '123456'
const PHONE = '13800138000'
const USER_PASSWORD = '123456'
const STAMP = Date.now()

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const results = []
function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -> ' + detail : ''}`)
}

function redisGet(key) {
  try {
    return execFileSync(REDIS_CLI, ['-h', '127.0.0.1', '-p', '6379', 'get', key], {
      encoding: 'utf8',
      timeout: 5000
    }).trim()
  } catch (e) {
    return ''
  }
}

/** 后端统一返回：HTTP 恒 200，成败只看 body.code */
async function api(method, p, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = 'Bearer ' + token
  const r = await fetch(API + p, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  return r.json()
}

function codeOf(js) {
  return js && typeof js.code === 'number' ? js.code : -1
}

async function adminLogin() {
  const js = await api('POST', '/api/admin/login', {
    username: ADMIN_USER,
    password: ADMIN_PASSWORD
  })
  const token = (js.data || {}).token
  if (!token) throw new Error('管理员登录失败：' + codeOf(js) + ' ' + (js.message || ''))
  return token
}

/** 用户登录（用户端强制图形验证码，脚本从 Redis 取回明文） */
async function userLogin() {
  const cap = await api('GET', '/api/captcha')
  const key = (cap.data || {}).captchaKey
  const code = redisGet('bookstore:captcha:' + key)
  if (!key || !code) throw new Error('取不到验证码（Redis 是否在线？）')
  const js = await api('POST', '/api/user/login', {
    phone: PHONE,
    password: USER_PASSWORD,
    captchaKey: key,
    captchaCode: code
  })
  const token = (js.data || {}).token
  if (!token) throw new Error('用户登录失败：' + codeOf(js) + ' ' + (js.message || ''))
  return token
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
  const requests = []
  const exceptions = []
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg)
      pending.delete(msg.id)
      return
    }
    if (!msg.method) return
    if (msg.method === 'Network.requestWillBeSent') {
      const req = (msg.params && msg.params.request) || {}
      if (String(req.url || '').includes('/api/')) {
        requests.push({
          method: req.method,
          url: String(req.url),
          postData: req.postData ? String(req.postData) : ''
        })
      }
    }
    if (msg.method === 'Runtime.exceptionThrown') {
      const d = (msg.params && msg.params.exceptionDetails) || {}
      exceptions.push(d.text || (d.exception && d.exception.description) || 'unknown')
    }
    listeners.forEach((fn) => fn(msg))
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
    requests,
    exceptions,
    async eval(expression) {
      const msg = await send('Runtime.evaluate', {
        expression,
        returnByValue: true,
        awaitPromise: true
      })
      if (msg.result && msg.result.exceptionDetails) {
        return '__ERR__ ' + (msg.result.exceptionDetails.text || '')
      }
      const res = msg.result && msg.result.result
      return res ? res.value : undefined
    },
    /** 切路由（hash 模式，不触发整页重载，网络监听得以延续） */
    async goto(hash, waitMs = 2500) {
      await this.eval(`location.href = ${JSON.stringify(hash)}`)
      await sleep(waitMs)
    },
    /**
     * 取命中某路径的 API 调用（含请求体）。
     *
     * 过滤 OPTIONS：管理端（5173）与后端（8080）不同源，浏览器会先发 CORS 预检，
     * 而预检与真实请求共用同一条 URL —— 不过滤会把 OPTIONS 当成「最后一次调用」。
     */
    calls(sub) {
      return requests
        .filter((r) => r.url.includes(sub) && r.method !== 'OPTIONS')
        .map((r) => ({ method: r.method, path: r.url.replace(/^https?:\/\/[^/]+/, ''), body: r.postData }))
    },
    lastCall(sub) {
      const hits = this.calls(sub)
      return hits.length ? hits[hits.length - 1] : null
    },
    /** 按方法 + 路径取调用：页面执行完写操作通常会再拉一次列表，只按路径取会拿到那次 GET */
    callOf(method, sub) {
      const hits = this.calls(sub).filter((c) => c.method === method)
      return hits.length ? hits[hits.length - 1] : null
    },
    clearRequests() {
      requests.length = 0
    },
    close: () => ws.close()
  }
}

/** 按文本点按钮（Element Plus 的按钮内容在 span 里，必须用 innerText 匹配） */
const CLICK_BY_TEXT = (sel, text) => `(() => {
  const nodes = [...document.querySelectorAll(${JSON.stringify(sel)})];
  const el = nodes.find((n) => (n.innerText || '').trim().includes(${JSON.stringify(text)}));
  if (!el) return 'not-found(' + nodes.length + ')';
  el.click();
  return 'clicked';
})()`

/** 按 el-form-item 的 label 精确匹配，填入值（走原生 setter + input 事件，才能驱动 v-model） */
const FILL_BY_LABEL = (scope, label, value) => `(() => {
  const items = [...document.querySelectorAll(${JSON.stringify(scope)} + ' .el-form-item')];
  const item = items.find((i) => {
    const lb = i.querySelector('.el-form-item__label');
    return lb && (lb.innerText || '').trim() === ${JSON.stringify(label)};
  });
  if (!item) return 'no-item';
  const input = item.querySelector('input');
  if (!input) return 'no-input';
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(input, ${JSON.stringify(value)});
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  return 'ok';
})()`

const ROW_COUNT = `document.querySelectorAll('.el-table__body-wrapper tbody tr').length`
const TABLE_TEXT = `(document.querySelector('.el-table__body-wrapper') || {}).innerText || ''`
const BODY_TEXT = `document.body.innerText || ''`

async function main() {
  const userDir = path.join(os.tmpdir(), 'bookstore-admin-ui-profile')
  fs.rmSync(userDir, { recursive: true, force: true })

  // ==================== 前置数据（API 级） ====================
  const adminToken = await adminLogin()
  const userToken = await userLogin()

  // 造一个用户书单（名称带时间戳，便于在列表里唯一定位）
  const booklistName = 'E2E 测试书单 ' + STAMP
  const booksJs = await api('GET', '/api/book/page?pageNum=1&pageSize=5')
  const books = (booksJs.data || {}).list || []
  if (!books.length) throw new Error('拿不到图书数据，无法准备测试书单')
  const seededBook = books[0]
  const createdList = await api(
    'POST',
    '/api/booklist',
    { name: booklistName, description: '由 verify_admin_ui.cjs 自动创建，结束时删除', isPublic: 0 },
    userToken
  )
  const booklistId = (createdList.data || {}).id
  if (!booklistId) throw new Error('创建测试书单失败：' + codeOf(createdList) + ' ' + (createdList.message || ''))
  await api('POST', `/api/booklist/${booklistId}/books`, { bookIds: [seededBook.id] }, userToken)

  // 造一条到货提醒：需要一本缺货图书（有货时先临时改成 0，结束时恢复）
  let noticeBookId = null
  let noticeBook = null
  let restoredStock = null
  const page = await api('GET', '/api/book/page?pageNum=1&pageSize=50')
  const allBooks = (page.data || {}).list || []
  const oos = allBooks.find((b) => b.stock <= 0)
  if (oos) {
    noticeBookId = oos.id
    noticeBook = oos
  } else if (allBooks.length) {
    const target = allBooks[0]
    const detail = await api('GET', `/api/admin/book/${target.id}`, undefined, adminToken)
    const full = detail.data || {}
    if (full.id) {
      const put = await api(
        'PUT',
        `/api/admin/book/${target.id}`,
        {
          title: full.title,
          author: full.author,
          publisher: full.publisher,
          categoryId: full.categoryId,
          price: full.price,
          stock: 0,
          coverUrl: full.coverUrl,
          description: full.description
        },
        adminToken
      )
      if (codeOf(put) === 200) {
        noticeBookId = target.id
        noticeBook = target
        restoredStock = full.stock
      }
    }
  }
  if (noticeBookId) {
    await api('POST', '/api/stock-notice', { bookId: noticeBookId }, userToken)
  }

  let chrome
  let client
  try {
    if (!fs.existsSync(CHROME)) throw new Error('未找到 Chrome：' + CHROME)

    chrome = spawn(
      CHROME,
      [
        '--headless=new',
        '--disable-gpu',
        '--no-sandbox',
        `--user-data-dir=${userDir}`,
        `--remote-debugging-port=${PORT}`,
        '--window-size=1600,950',
        'about:blank'
      ],
      { stdio: 'ignore' }
    )
    if (!(await waitDevTools())) throw new Error('Chrome 调试端口未就绪')

    const tab = await newTab(ADMIN_WEB + '/#/login')
    client = makeClient(tab.webSocketDebuggerUrl)
    await client.ready
    await client.send('Page.enable')
    await client.send('Runtime.enable')
    await client.send('Network.enable')
    await sleep(4000)

    // ---------- 1. 登录 ----------
    // 密码框是第二个 input（第一个是已预填 admin 的账号框）
    const pwdFilled = String(
      (await client.eval(`(() => {
        const inputs = [...document.querySelectorAll('.login-box input')];
        if (inputs.length < 2) return 'no-inputs';
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        setter.call(inputs[1], ${JSON.stringify(ADMIN_PASSWORD)});
        inputs[1].dispatchEvent(new Event('input', { bubbles: true }));
        return 'ok';
      })()`)) || ''
    )
    await sleep(300)
    const loginClick = String((await client.eval(CLICK_BY_TEXT('.login-btn', '登'))) || '')
    await sleep(4000)
    const logged = await client.eval(`JSON.stringify({
      token: (localStorage.getItem('bookstore_admin_token') || '').slice(0, 12),
      hash: location.hash,
      hasMenu: !!document.querySelector('.layout-menu')
    })`)
    let lg = {}
    try {
      lg = JSON.parse(String(logged))
    } catch (e) {}
    check('管理员登录成功并进入后台', !!lg.token && String(lg.hash).includes('dashboard'),
      `令牌=${lg.token || '(空)'} hash=${lg.hash} 菜单=${lg.hasMenu}`)
    check('登录页密码框可填并触发登录', pwdFilled === 'ok' && loginClick === 'clicked',
      `填值=${pwdFilled} 按钮=${loginClick}`)

    // ---------- 2. 侧边菜单 ----------
    const menuText = String((await client.eval(`document.querySelector('.layout-menu').innerText`)) || '')
    check('菜单出现「轮播图管理」', menuText.includes('轮播图管理'), '')
    check('菜单出现「书单管理」', menuText.includes('书单管理'), '')
    check('菜单出现「到货提醒」', menuText.includes('到货提醒'), '')

    // ---------- 3. 轮播图页：列表 ----------
    client.clearRequests()
    await client.goto('#/banner', 4000)
    const bannerRows = Number((await client.eval(ROW_COUNT)) || 0)
    const listCall = client.lastCall('/api/admin/banner/list')
    check('轮播图页请求 /api/admin/banner/list', !!listCall, listCall ? listCall.path : '未发出请求')
    check('轮播图列表渲染既有数据（seed 3 条）', bannerRows >= 3, '行数=' + bannerRows)

    // ---------- 4. 轮播图页：新增（真实表单提交）→ 删除 ----------
    const bannerTitle = 'E2E 轮播图 ' + STAMP
    await client.eval(CLICK_BY_TEXT('.app-header .el-button', '新增轮播图'))
    await sleep(1200)
    const dialogOpen = String(
      (await client.eval(`(() => {
        const d = document.querySelector('.el-dialog');
        return d && d.offsetParent !== null ? 'open' : 'closed';
      })()`)) || ''
    )
    const titleFilled = String((await client.eval(FILL_BY_LABEL('.el-dialog', '标题', bannerTitle))) || '')
    await sleep(300)
    client.clearRequests()
    await client.eval(CLICK_BY_TEXT('.el-dialog__footer .el-button', '保存'))
    await sleep(3000)
    const createCall = client.callOf('POST', '/api/admin/banner')
    const rowsAfterCreate = Number((await client.eval(ROW_COUNT)) || 0)
    const listAfterCreate = await api('GET', '/api/admin/banner/list', undefined, adminToken)
    const createdBanner = ((listAfterCreate.data || []) || []).find((b) => b.title === bannerTitle)
    check('新增弹窗可打开且标题可填', dialogOpen === 'open' && titleFilled === 'ok',
      `弹窗=${dialogOpen} 填值=${titleFilled}`)
    check('提交后 POST /api/admin/banner 命中并带上标题',
      !!createCall && String(createCall.body || '').includes(bannerTitle),
      createCall ? createCall.method + ' ' + createCall.path + ' body=' + (createCall.body || '') : '未发出请求')
    check('新增后列表行数 +1', rowsAfterCreate === bannerRows + 1, `${bannerRows} -> ${rowsAfterCreate}`)
    check('新增的轮播图确实落库', !!createdBanner,
      createdBanner ? 'id=' + createdBanner.id + ' sort=' + createdBanner.sort : '后端查不到该标题')

    if (createdBanner) {
      // 删除刚造的那一行（走 UI 的确认框，顺带验证删除入口）
      await client.eval(`(() => {
        const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
        const row = rows.find((r) => (r.innerText || '').includes(${JSON.stringify(bannerTitle)}));
        if (!row) return 'no-row';
        const btn = [...row.querySelectorAll('.el-button')].find((b) => (b.innerText || '').trim() === '删除');
        if (!btn) return 'no-btn';
        btn.click();
        return 'clicked';
      })()`)
      await sleep(1200)
      await client.eval(CLICK_BY_TEXT('.el-message-box__btns .el-button', '删除'))
      await sleep(2500)
      const rowsAfterDelete = Number((await client.eval(ROW_COUNT)) || 0)
      const listAfterDelete = await api('GET', '/api/admin/banner/list', undefined, adminToken)
      const stillThere = ((listAfterDelete.data || []) || []).some((b) => b.title === bannerTitle)
      check('UI 删除后行数复原', rowsAfterDelete === bannerRows, `${rowsAfterCreate} -> ${rowsAfterDelete}`)
      check('UI 删除后后端不再返回该轮播图', !stillThere, '仍存在=' + stillThere)
    }

    // ---------- 5. 书单页：列表与详情 ----------
    client.clearRequests()
    await client.goto('#/booklist', 4500)
    const blCall = client.lastCall('/api/admin/booklist/page')
    const blText = String((await client.eval(TABLE_TEXT)) || '')
    check('书单页请求 /api/admin/booklist/page', !!blCall, blCall ? blCall.path : '未发出请求')
    check('书单列表能看到用户真实创建的书单', blText.includes(booklistName),
      '书单名=' + booklistName)
    check('书单列表同时给出创建者手机号（脱敏）', /\d{3}\*{4}\d{4}/.test(blText),
      (blText.match(/\d{3}\*{4}\d{4}/) || ['(未找到)'])[0])

    await client.eval(`(() => {
      const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
      const row = rows.find((r) => (r.innerText || '').includes(${JSON.stringify(booklistName)}));
      if (!row) return 'no-row';
      const btn = [...row.querySelectorAll('.el-button')].find((b) => (b.innerText || '').trim() === '详情');
      if (!btn) return 'no-btn';
      btn.click();
      return 'clicked';
    })()`)
    await sleep(2500)
    const drawerText = String(
      (await client.eval(`(() => {
        const d = document.querySelector('.el-drawer');
        return d && d.offsetParent !== null ? d.innerText : '';
      })()`)) || ''
    )
    check('详情抽屉渲染书单条目', drawerText.includes('收录图书') && drawerText.includes(seededBook.title),
      '含书名=' + drawerText.includes(seededBook.title) + ' 长度=' + drawerText.length)
    await client.eval(`(() => {
      const btn = document.querySelector('.el-drawer__close-btn');
      if (btn) btn.click();
      return 'ok';
    })()`)
    await sleep(800)

    // ---------- 6. 到货提醒看板 ----------
    client.clearRequests()
    await client.goto('#/stock-notice', 4500)
    const snCall = client.lastCall('/api/admin/stock-notice/summary')
    check('到货提醒页请求 /api/admin/stock-notice/summary', !!snCall, snCall ? snCall.path : '未发出请求')
    if (!noticeBookId) {
      check('看板展示缺货图书的等待人数', false, '未能准备缺货图书（无图书数据），跳过')
    } else {
      const snText = String((await client.eval(TABLE_TEXT)) || '')
      const hasRow = snText.includes(noticeBook.title)
      check('看板展示缺货图书的等待人数', hasRow && snText.includes('人'),
        `书名=${noticeBook.title} 命中=${hasRow}`)
      if (hasRow) {
        await client.eval(`(() => {
          const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
          const row = rows.find((r) => (r.innerText || '').includes(${JSON.stringify(noticeBook.title)}));
          if (!row) return 'no-row';
          const btn = [...row.querySelectorAll('.el-button')].find((b) => (b.innerText || '').trim() === '标记已通知');
          if (!btn) return 'no-btn';
          btn.click();
          return 'clicked';
        })()`)
        await sleep(1200)
        await client.eval(CLICK_BY_TEXT('.el-message-box__btns .el-button', '确认'))
        await sleep(3000)
        const afterText = String((await client.eval(TABLE_TEXT)) || '')
        check('标记已通知后该书从「只看有人等待」列表中移除',
          !afterText.includes(noticeBook.title),
          '仍出现=' + afterText.includes(noticeBook.title))
      }
    }

    // ---------- 7. 系统设置：运费规则 ----------
    client.clearRequests()
    await client.goto('#/setting', 4500)
    const settingText = String((await client.eval(BODY_TEXT)) || '')
    const cfgCall = client.lastCall('/api/admin/config/list')
    check('系统设置请求 /api/admin/config/list', !!cfgCall, cfgCall ? cfgCall.path : '未发出请求')
    check('运费规则回显当前门槛（¥99.00 包邮）', settingText.includes('满 ¥99.00 包邮'),
      (settingText.match(/满 ¥[\d.]+ 包邮[^\n]*/) || ['(未找到)'])[0])

    const filled = String((await client.eval(FILL_BY_LABEL('.freight-form', '满额包邮门槛', '88'))) || '')
    await sleep(500)
    client.clearRequests()
    await client.eval(CLICK_BY_TEXT('.freight-form .el-button', '保存运费规则'))
    await sleep(3000)
    const putCall = client.callOf('PUT', '/api/admin/config/freight.free-threshold')
    const afterSave = String((await client.eval(BODY_TEXT)) || '')
    check('保存运费规则：填值有效', filled === 'ok', filled)
    check('保存运费规则：PUT 命中 freight.free-threshold 且值为 88.00',
      !!putCall && String(putCall.body || '').includes('88.00'),
      putCall ? putCall.method + ' ' + putCall.path + ' body=' + putCall.body : '未发出请求')
    check('保存后页面回显新门槛（¥88.00 包邮）', afterSave.includes('满 ¥88.00 包邮'),
      (afterSave.match(/满 ¥[\d.]+ 包邮[^\n]*/) || ['(未找到)'])[0])

    // 还原为 99，避免污染后续手工演示
    await client.eval(FILL_BY_LABEL('.freight-form', '满额包邮门槛', '99'))
    await sleep(500)
    await client.eval(CLICK_BY_TEXT('.freight-form .el-button', '保存运费规则'))
    await sleep(3000)
    const restored = String((await client.eval(BODY_TEXT)) || '')
    check('运费门槛已还原为 ¥99.00', restored.includes('满 ¥99.00 包邮'),
      (restored.match(/满 ¥[\d.]+ 包邮[^\n]*/) || ['(未找到)'])[0])

    // ---------- 8. 无未捕获异常 ----------
    check('全程无未捕获的 JS 异常', client.exceptions.length === 0,
      client.exceptions.slice(0, 3).join(' | '))
  } finally {
    if (client) client.close()
    if (chrome) chrome.kill()

    // 清理：先删测试书单，再恢复被临时改为 0 的库存
    try {
      if (booklistId) await api('DELETE', `/api/admin/booklist/${booklistId}`, undefined, adminToken)
    } catch (e) {}
    if (restoredStock !== null && noticeBookId) {
      try {
        const detail = await api('GET', `/api/admin/book/${noticeBookId}`, undefined, adminToken)
        const full = detail.data || {}
        await api(
          'PUT',
          `/api/admin/book/${noticeBookId}`,
          {
            title: full.title,
            author: full.author,
            publisher: full.publisher,
            categoryId: full.categoryId,
            price: full.price,
            stock: restoredStock,
            coverUrl: full.coverUrl,
            description: full.description
          },
          adminToken
        )
      } catch (e) {}
    }
  }

  const passed = results.filter((r) => r.ok).length
  const failed = results.length - passed
  console.log('')
  console.log(`===== 管理端 UI 端到端：通过 ${passed} / 失败 ${failed} =====`)
  if (failed > 0) process.exitCode = 1
}

main().catch((e) => {
  console.error('[ERROR] ' + (e && e.stack ? e.stack : e))
  process.exitCode = 1
})
