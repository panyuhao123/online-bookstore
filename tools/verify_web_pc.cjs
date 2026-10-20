/**
 * PC 商城（bookstore-web，5176）端到端自测 —— CDP 驱动无头 Chrome，不依赖任何 npm 包。
 *
 * 背景：tools/ 下既有的 verify_web_guard / verify_web_interaction / verify_web_search.cjs
 *      全部指向 APP H5（5175 / `#/pages/...`），run_all_checks.py --with-frontend 也只跑
 *      check_app_sfc.cjs（APP 的 SFC 静态检查）。PC 商城此前**没有任何自动化检查**，本脚本补这个缺口。
 *
 * 覆盖（1~7 为 B1 批次修复过的真实功能缺陷，8~13 为 W2 接入的新功能，逐条可回归）：
 *   1. 官方书单能真正打开并渲染图书（此前 `Number('official-1')` = NaN，查询必然失效）
 *   2. 列表页读取并透传 `?sort=`（此前只认 keyword/categoryId，`看一看?sort=sales` 被丢弃）
 *   3. 非法 sort 回落 default（白名单，防止把任意串转发给后端）
 *   4. 高级检索翻页不被重置（此前 `search()` 首行把 pageNum 置 1，第 2 页永远拿第 1 页数据）
 *   5. 对比栏跨组件响应式同步（此前 BookCard 直接写 localStorage，对比栏永远不刷新）
 *   6. 购物车批量操作按钮可用（此前手写复选框列却绑 @selection-change，按钮永远禁用）
 *   7. 「立即购买」只结算该一本（此前结算页按 checked 取商品，会连带购物车里其他商品）
 *   8. 首页轮播图（S4）与分类楼层（N4）取自后端配置而非前端硬编码
 *   9. 商品目录页（S6）按分类页签平铺，切页签后 URL 与服务端请求同步 `categoryId`
 *  10. 搜索联想（N1）输入即请求 `/api/book/page?keyword=` 并渲染下拉
 *  11. 图书徽标（N5）每条都有数据支撑，文案白名单内（不做无依据的「新品」）
 *  12. 结算运费明细（S5）：显式传 `cartIds` 调 `settle-preview`，三项金额自洽
 *  13. 订单时间线（N3）4 节点、小票导出（N8）真的产出 xlsx、书单（S1）与浏览历史（S2）读写后端
 *
 * 断言一律锁「方法 + 路径 + 请求体（postData）」，不只看路径：页面写操作后常会再拉一次列表，
 * 只按路径取「最后一次请求」拿到的是那次读请求。详见 callOf()。
 *
 * 用法：先启动后端（8080）与 PC 商城 dev server（5176），再执行
 *   node tools/verify_web_pc.cjs
 *
 * 说明：验证码是图片、脚本无法识别；本地 E2E 直接从 Redis 读回本次验证码明文完成登录，
 *      这只是测试侧取数手段，不依赖服务端测试后门，也不改动应用代码。
 */
const { spawn, execFileSync } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const REDIS_CLI = process.env.BOOKSTORE_REDIS_CLI || 'D:/redis5/redis-cli.exe'
const WEB = 'http://localhost:5176'
const API = 'http://127.0.0.1:8080'
const PORT = 9336
const PHONE = '13800138000'
const PASSWORD = '123456'

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

/** 登录（APP/Web 用户登录强制图形验证码，脚本从 Redis 取回明文） */
async function login() {
  const cap = await api('GET', '/api/captcha')
  const key = (cap.data || {}).captchaKey
  const code = redisGet('bookstore:captcha:' + key)
  if (!key || !code) throw new Error('取不到验证码（Redis 是否在线？）')
  const js = await api('POST', '/api/user/login', {
    phone: PHONE,
    password: PASSWORD,
    captchaKey: key,
    captchaCode: code
  })
  const token = (js.data || {}).token
  if (!token) throw new Error('登录失败：' + js.code + ' ' + js.message)
  // 与前端 userStore.login() 一致：token 与用户信息同时落 localStorage
  return { token, user: (js.data || {}).user || null }
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
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg)
      pending.delete(msg.id)
    } else if (msg.method) {
      if (msg.method === 'Network.requestWillBeSent') {
        const req = (msg.params && msg.params.request) || {}
        if (String(req.url || '').includes('/api/')) {
          requests.push({ method: req.method, url: String(req.url), postData: req.postData || '' })
        }
      }
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
    requests,
    on(fn) {
      listeners.push(fn)
    },
    async eval(expression) {
      const msg = await send('Runtime.evaluate', {
        expression,
        returnByValue: true,
        awaitPromise: true
      })
      const res = msg.result && msg.result.result
      if (msg.result && msg.result.exceptionDetails) {
        return '__ERR__ ' + (msg.result.exceptionDetails.text || '')
      }
      return res ? res.value : undefined
    },
    /** 切路由（hash 模式，不触发整页重载，网络监听得以延续） */
    async goto(hash, waitMs = 2500) {
      await this.eval(`location.href = ${JSON.stringify(hash)}`)
      await sleep(waitMs)
    },
    /** 取当前页所有 /api 请求的 query 串，便于断言参数 */
    apiQueries(sub) {
      return requests
        .filter((r) => r.url.includes(sub))
        .map((r) => r.url.replace(/^https?:\/\/[^/]+/, ''))
    },
    /**
     * 按「方法 + 路径」取最后一次请求。
     *
     * <p>只按路径取最后一次是不够的：页面在写操作成功后会**再拉一次列表**，
     * 那时「最后一次 /api/booklist/list」是一条 GET，而不是刚才那条 POST。
     */
    callOf(method, sub) {
      const hits = requests.filter((r) => r.method === method && r.url.includes(sub))
      return hits.length ? hits[hits.length - 1] : null
    },
    clearRequests() {
      requests.length = 0
    },
    close: () => ws.close()
  }
}

/** 页面上按文本找按钮（Element Plus 的按钮内容在 span 里，必须用 innerText 匹配） */
const CLICK_BY_TEXT = (sel, text) => `(() => {
  const nodes = [...document.querySelectorAll(${JSON.stringify(sel)})];
  const el = nodes.find((n) => (n.innerText || '').trim().includes(${JSON.stringify(text)}));
  if (!el) return 'not-found(' + nodes.length + ')';
  el.click();
  return 'clicked';
})()`

async function main() {
  const userDir = path.join(os.tmpdir(), 'bookstore-web-pc-profile')
  fs.rmSync(userDir, { recursive: true, force: true })
  const dlDir = path.join(os.tmpdir(), 'bookstore-web-pc-downloads')
  fs.rmSync(dlDir, { recursive: true, force: true })
  fs.mkdirSync(dlDir, { recursive: true })
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      `--user-data-dir=${userDir}`,
      `--remote-debugging-port=${PORT}`,
      '--window-size=1440,900',
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  const { token, user } = await login()
  // 造两条购物车数据，供「立即购买只结算一本」做对照
  const books = await api('GET', '/api/book/page?pageNum=1&pageSize=5')
  const all = (books.data || {}).list || []
  const withStock = all.filter((b) => b.stock > 0)
  const preAdded = withStock.slice(0, 2)
  const buyTarget = withStock[2] || withStock[0]
  for (const b of preAdded) {
    await api('POST', '/api/cart/add', { bookId: b.id, quantity: 1 }, token)
  }
  const cartBefore = await api('GET', '/api/cart/list', undefined, token)
  const cartIdsBefore = ((cartBefore.data || {}).list || []).map((i) => i.id)

  // ---- W2 新增功能所需的测试数据（全部走接口造，跑完在 finally 里还原） ---- #
  const STAMP = Date.now()
  const testBooklistName = 'E2E 商城书单 ' + STAMP
  let testBooklistId = null
  let createdAddressId = null
  let testOrderId = null
  const historyBook = buyTarget

  // 书单（S1）：建一个 + 加一本，供「我的书单」与「书单详情」断言
  const bl = await api(
    'POST',
    '/api/booklist',
    { name: testBooklistName, description: '自动化测试创建，跑完自动删除', isPublic: 1 },
    token
  )
  testBooklistId = (bl.data || {}).id || null
  if (testBooklistId) {
    await api('POST', `/api/booklist/${testBooklistId}/books`, { bookIds: [historyBook.id] }, token)
  }

  // 地址：下单必须有收货地址；用户没有才建一个（建了就记 id，收尾删掉）
  const addr = await api('GET', '/api/address/list', undefined, token)
  const addrList = addr.data || []
  if (!addrList.length) {
    const add = await api(
      'POST',
      '/api/address/add',
      {
        receiver: 'E2E 测试',
        phone: '13800138000',
        province: '北京市',
        city: '北京市',
        district: '东城区',
        detail: '自动化测试地址（跑完自动删除）',
        isDefault: 1
      },
      token
    )
    createdAddressId = (add.data || {}).id || null
  }

  try {
    if (!fs.existsSync(CHROME)) throw new Error('未找到 Chrome：' + CHROME)
    if (!(await waitDevTools())) throw new Error('Chrome 调试端口未就绪')

    // ================= 只读检查（未登录） =================
    const t1 = await newTab(WEB + '/#/booklists/official-1')
    const c1 = makeClient(t1.webSocketDebuggerUrl)
    await c1.ready
    await c1.send('Page.enable')
    await c1.send('Runtime.enable')
    await c1.send('Network.enable')
    await sleep(5000)

    // ---------- 1. 官方书单能真正打开 ----------
    const official = await c1.eval(`(() => {
      const h2 = document.querySelector('.detail-header .header-info h2');
      const rows = document.querySelectorAll('.el-table__body-wrapper tbody tr').length;
      const cards = document.querySelectorAll('.book-grid .book-card').length;
      const notFound = !!document.querySelector('.el-result');
      return JSON.stringify({
        name: h2 ? h2.innerText.trim() : '',
        rows, cards, notFound
      });
    })()`)
    let off = {}
    try {
      off = JSON.parse(String(official))
    } catch (e) {}
    const offCount = Math.max(off.rows || 0, off.cards || 0)
    check(
      '官方书单可打开并渲染图书',
      !off.notFound && offCount > 0,
      `标题=${off.name || '(空)'} 图书数=${offCount} 空态=${off.notFound}`
    )
    check(
      '官方书单展示的是「计算机经典必读」',
      String(off.name || '').includes('计算机经典必读'),
      String(off.name || '(空)')
    )

    // ---------- 2. 列表页透传 ?sort= ----------
    c1.clearRequests()
    await c1.goto('#/books?sort=price_asc', 4000)
    let q = c1.apiQueries('/api/book/page').pop() || ''
    let active = String(
      (await c1.eval(
        `(() => { const el = document.querySelector('.el-radio-button.is-active'); return el ? el.innerText.trim() : ''; })()`
      )) || ''
    )
    check('列表页把 ?sort=price_asc 透传给后端', q.includes('sort=price_asc'), q + ' | 选中=' + active)
    check('排序控件回显为「价格↑」', active.includes('价格'), active)

    // ---------- 3. 非法 sort 回落白名单 ----------
    c1.clearRequests()
    await c1.goto('#/books?sort=__evil__', 4000)
    q = c1.apiQueries('/api/book/page').pop() || ''
    active = String(
      (await c1.eval(
        `(() => { const el = document.querySelector('.el-radio-button.is-active'); return el ? el.innerText.trim() : ''; })()`
      )) || ''
    )
    check(
      '非法 sort 回落 default（白名单生效）',
      q.includes('sort=default') && !q.includes('__evil__'),
      q + ' | 选中=' + active
    )

    // ---------- 4. 高级检索翻页不被重置 ----------
    // 注意：页面 onMounted 会自行发一次查询，所以必须「先进页面、等首查结束、再清空记录、再点检索」，
    // 否则清空后会捕获不到任何请求，断言恒为假。
    await c1.goto('#/search/advanced', 4000)
    c1.clearRequests()
    const searched = String((await c1.eval(CLICK_BY_TEXT('.el-button', '检索'))) || '')
    await sleep(4000)
    const firstPage = c1.apiQueries('/api/book/page').pop() || ''
    let pager = String(
      (await c1.eval(
        `(() => { const li = [...document.querySelectorAll('.el-pagination .el-pager li')].find(n => n.innerText.trim() === '2'); if (!li) return 'no-pager'; li.click(); return 'clicked'; })()`
      )) || ''
    )
    await sleep(4000)
    const secondPage = c1.apiQueries('/api/book/page').pop() || ''
    check('检索首次查询为第 1 页', firstPage.includes('pageNum=1'), firstPage)
    check(
      '检索翻到第 2 页后 pageNum=2（未被重置）',
      pager === 'clicked' && secondPage.includes('pageNum=2'),
      `分页=${pager} 请求=${secondPage}`
    )

    // ---------- 5. 对比栏跨组件响应式同步 ----------
    await c1.goto('#/books', 4000)
    const toTable = String((await c1.eval(CLICK_BY_TEXT('.tool-right .el-button', '表格'))) || '')
    await sleep(1200)
    const addCompare = `(() => {
      const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
      let n = 0;
      for (const tr of rows.slice(0, 2)) {
        const btn = [...tr.querySelectorAll('button')].find((b) => (b.innerText || '').trim() === '对比');
        if (btn) { btn.click(); n++; }
      }
      return 'clicked=' + n + ' rows=' + rows.length;
    })()`
    const added = String((await c1.eval(addCompare)) || '')
    await sleep(1200)
    const barText = String(
      (await c1.eval(
        `(() => { const el = document.querySelector('.compare-bar'); return el ? el.innerText.replace(/\\s+/g, ' ').trim() : 'no-bar'; })()`
      )) || ''
    )
    // 再去掉一本，验证反向同步
    const removed = String(
      (await c1.eval(
        `(() => { const el = document.querySelector('.compare-bar .compare-item .remove'); if (!el) return 'no-remove'; el.click(); return 'clicked'; })()`
      )) || ''
    )
    await sleep(1200)
    const barText2 = String(
      (await c1.eval(
        `(() => { const el = document.querySelector('.compare-bar'); return el ? el.innerText.replace(/\\s+/g, ' ').trim() : 'no-bar'; })()`
      )) || ''
    )
    check(
      '对比栏随卡片操作实时同步到 2 本',
      barText.includes('(2/4)'),
      `切表格=${toTable} ${added} | 对比栏=${barText}`
    )
    check(
      '对比栏移出一本后同步为 1 本',
      removed === 'clicked' && barText2.includes('(1/4)'),
      `${removed} | 对比栏=${barText2}`
    )

    // ---------- 8. 首页轮播图取自后端（S4） ----------
    c1.clearRequests()
    await c1.goto('#/', 5000)
    const bannerReq = c1.requests.some((r) => r.url.includes('/api/banner/list'))
    const bannerApi = await api('GET', '/api/banner/list')
    const bannerList = bannerApi.data || []
    const bannerState = JSON.parse(
      String(
        (await c1.eval(`(() => {
        const slides = [...document.querySelectorAll('.banner-slide')];
        return JSON.stringify({
          count: slides.length,
          text: slides.map((s) => (s.innerText || '').replace(/\\s+/g, ' ').trim()).join(' | ')
        });
      })()`)) || '{}'
      )
    )
    check(
      '首页轮播图请求后台配置并完成渲染（S4）',
      bannerReq && bannerState.count > 0,
      `请求=${bannerReq} 渲染张数=${bannerState.count} 接口条数=${bannerList.length}`
    )
    // 接口有数据时必须与配置一致；为空时前端有本地兜底（设计如此），只能断言「没开天窗」
    const bannerMatched =
      bannerList.length === 0
        ? bannerState.count > 0
        : String(bannerState.text || '').includes(String(bannerList[0].title || ''))
    check(
      bannerList.length === 0 ? '轮播图为空时前端有兜底（不开天窗）' : '轮播图内容与后台配置一致',
      bannerMatched,
      `接口首条=${(bannerList[0] || {}).title || '(无)'} 渲染=${String(bannerState.text || '').slice(0, 60)}`
    )

    // ---------- 9. 首页分类楼层（N4） ----------
    const floorState = JSON.parse(
      String(
        (await c1.eval(`(() => {
        const floors = [...document.querySelectorAll('.floor')];
        const names = floors
          .map((f) => { const h = f.querySelector('.floor-title h2'); return h ? (h.innerText || '').trim() : ''; })
          .filter(Boolean);
        const subs = floors
          .map((f) => { const s = f.querySelector('.floor-aside .floor-sub'); return s ? (s.innerText || '').trim() : ''; })
          .filter(Boolean);
        return JSON.stringify({ count: floors.length, names: names.slice(0, 6), subs: subs.slice(0, 6) });
      })()`)) || '{}'
      )
    )
    check(
      '首页按分类聚合楼层（N4，含分类名与图书卡片）',
      floorState.count >= 2 && (floorState.names || []).length >= 1,
      `楼层数=${floorState.count} 分类名=${JSON.stringify(floorState.names || [])}`
    )
    // D-10：楼层左侧的「N 本在售」必须是真的数字，不能是「0 本在售」或空缺
    check(
      '首页楼层显示真实书目数（D-10）',
      (floorState.subs || []).some((s) => /^[1-9]\d*\s*本在售$/.test(s)),
      `楼层说明=${JSON.stringify(floorState.subs || [])}`
    )

    // ---------- 10. 商品目录页（S6） ----------
    c1.clearRequests()
    await c1.goto('#/catalog', 5000)
    const catalogState = JSON.parse(
      String(
        (await c1.eval(`(() => {
        return JSON.stringify({
          tabs: document.querySelectorAll('.cat-tab').length,
          cards: document.querySelectorAll('.book-grid .book-card').length,
          title: (document.querySelector('.page-title') || {}).innerText || '',
          sub: (document.querySelector('.page-sub') || {}).innerText || '',
          tabNums: [...document.querySelectorAll('.cat-tab .cnt')].map((e) => e.innerText.trim())
        });
      })()`)) || '{}'
      )
    )
    check(
      '商品目录页按分类页签平铺图书（S6）',
      catalogState.tabs >= 2 && catalogState.cards > 0,
      `页签=${catalogState.tabs} 图书=${catalogState.cards} 标题=${String(catalogState.title || '').trim()}`
    )
    // D-10：分类书目数由后端 /api/category/list 提供。接口给对了不代表页面渲染出来，
    // 中间任何一层没接上（字段名写错、v-if 条件写反）都只有这里能看到
    check(
      '目录页页签展示真实书目数（D-10）',
      (catalogState.tabNums || []).length >= 2 &&
        catalogState.tabNums.every((n) => /^\d+$/.test(n)) &&
        catalogState.tabNums.some((n) => Number(n) > 0),
      `页签数字=${JSON.stringify(catalogState.tabNums || [])}`
    )
    check(
      '目录页头部显示「全店 N 个分类 · N 本在售」（D-10）',
      /全店\s*\d+\s*个分类/.test(String(catalogState.sub || '')) &&
        /\d+\s*本在售/.test(String(catalogState.sub || '')),
      `头部=${String(catalogState.sub || '').replace(/\s+/g, ' ')}`
    )

    // ---------- 11. 目录页切分类：URL 与请求同步 ----------
    c1.clearRequests()
    const tabClick = String(
      (await c1.eval(
        `(() => { const tabs = [...document.querySelectorAll('.cat-tab')]; if (tabs.length < 2) return 'no-tabs'; tabs[1].click(); return 'clicked'; })()`
      )) || ''
    )
    await sleep(3500)
    const catHash = String((await c1.eval('location.hash')) || '')
    const catQuery = c1.apiQueries('/api/book/page').pop() || ''
    check(
      '目录页切分类后 URL 与服务端请求同步 categoryId',
      tabClick === 'clicked' && /categoryId=\d+/.test(catHash) && /categoryId=\d+/.test(catQuery),
      `点击=${tabClick} url=${catHash} 请求=${catQuery}`
    )

    // ---------- 12. 搜索联想（N1） ----------
    // 关键字取自真实书名，避免用到库里没有的词导致「联想为空」的技术性失败
    const kwSeed = String((all[0] || {}).title || '').slice(0, 2)
    c1.clearRequests()
    await c1.goto('#/books', 3500)
    c1.clearRequests()
    const typed = String(
      (await c1.eval(`(() => {
        const input = document.querySelector('.search-box input');
        if (!input) return 'no-input';
        input.focus();
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        setter.call(input, ${JSON.stringify(kwSeed)});
        input.dispatchEvent(new Event('input', { bubbles: true }));
        return 'ok';
      })()`)) || ''
    )
    await sleep(3000)
    const sugState = JSON.parse(
      String(
        (await c1.eval(`(() => {
        const items = [...document.querySelectorAll('.search-suggest-popper .sug-item')];
        return JSON.stringify({
          count: items.length,
          first: items.length ? (items[0].innerText || '').replace(/\\s+/g, ' ').trim() : ''
        });
      })()`)) || '{}'
      )
    )
    const sugQuery = c1.apiQueries('/api/book/page').pop() || ''
    check(
      '搜索联想输入即请求 keyword 并渲染下拉（N1）',
      typed === 'ok' && sugState.count > 0 && sugQuery.includes('keyword=' + encodeURIComponent(kwSeed)),
      `输入=${kwSeed} 建议数=${sugState.count} 请求=${sugQuery}`
    )

    // ---------- 13. 图书徽标（N5） ----------
    await c1.goto('#/books', 4000)
    const badgeState = JSON.parse(
      String(
        (await c1.eval(`(() => {
        const items = [...document.querySelectorAll('.badge-strip .badge-item')];
        return JSON.stringify({
          count: items.length,
          texts: items.map((n) => (n.innerText || '').trim()).slice(0, 10)
        });
      })()`)) || '{}'
      )
    )
    // 徽标文案白名单：每条都必须有数据支撑，不允许出现编造的「新品」之类
    const BADGE_RE = /^(已售罄|仅剩 \d+ 件|热销|好评|包邮)$/
    const badTexts = (badgeState.texts || []).filter((t) => !BADGE_RE.test(t))
    check(
      '图书卡片徽标有数据支撑且文案在白名单内（N5）',
      badgeState.count > 0 && badTexts.length === 0,
      `徽标数=${badgeState.count} 样本=${JSON.stringify(badgeState.texts || [])} 越界=${JSON.stringify(badTexts)}`
    )
    c1.close()

    // ================= 登录态检查 =================
    const t2 = await newTab(WEB + '/#/books')
    const c2 = makeClient(t2.webSocketDebuggerUrl)
    await c2.ready
    await c2.send('Page.enable')
    await c2.send('Runtime.enable')
    await c2.send('Network.enable')
    await sleep(3500)
    await c2.eval(`localStorage.setItem('bookstore_web_token', ${JSON.stringify(token)})`)
    await c2.eval(
      `localStorage.setItem('bookstore_web_user', ${JSON.stringify(JSON.stringify(user))})`
    )
    await c2.eval(`localStorage.removeItem('bookstore_web_compare')`)
    // userStore 的 token 只在 store 初始化时读一次 localStorage，
    // 注入后必须重载页面才会被路由守卫/顶栏识别，否则受保护页会被重定向到 /login。
    await c2.eval('location.reload()')
    await sleep(5000)
    // 登录态判据：登录分支渲染 `.user-info` 下拉、未登录分支渲染「登录/注册」按钮，
    // 两分支互斥，所以「有 user-info 且无登录按钮」才是真正的已登录。
    const headerState = String(
      (await c2.eval(`(() => {
        const info = document.querySelector('.header-right .user-info');
        const loginBtn = [...document.querySelectorAll('.header-right button')]
          .find((b) => (b.innerText || '').trim() === '登录');
        return JSON.stringify({
          hasUserInfo: !!info,
          hasLoginBtn: !!loginBtn,
          nickname: info ? info.innerText.trim() : ''
        });
      })()`)) || ''
    )
    let hs = {}
    try {
      hs = JSON.parse(headerState)
    } catch (e) {}
    check(
      '注入登录态后顶栏切换为已登录（显示用户菜单、无登录按钮）',
      hs.hasUserInfo === true && hs.hasLoginBtn === false,
      `用户菜单=${hs.hasUserInfo} 登录按钮=${hs.hasLoginBtn} 昵称=${hs.nickname || '(空)'}`
    )

    // ---------- 6. 购物车批量操作按钮可用 ----------
    await c2.goto('#/cart', 5500)
    const cartState = String(
      (await c2.eval(`(() => {
        const btns = [...document.querySelectorAll('button')];
        const batch = btns.find((b) => (b.innerText || '').trim() === '批量删除');
        const rows = document.querySelectorAll('.el-table__body-wrapper tbody tr').length;
        if (!batch) return JSON.stringify({ found: false, rows });
        return JSON.stringify({ found: true, disabled: batch.disabled, rows });
      })()`)) || ''
    )
    let cs = {}
    try {
      cs = JSON.parse(cartState)
    } catch (e) {}
    check(
      '购物车有商品时「批量删除」按钮可用',
      cs.found === true && cs.disabled === false && cs.rows > 0,
      `按钮存在=${cs.found} 禁用=${cs.disabled} 行数=${cs.rows}（购物车已有 ${cartIdsBefore.length} 条）`
    )

    // ---------- 7. 立即购买只结算一本 ----------
    await c2.goto('#/books/' + buyTarget.id, 4500)
    const buyClick = String((await c2.eval(CLICK_BY_TEXT('.el-button', '立即购买'))) || '')
    await sleep(5000)
    const confirmState = String(
      (await c2.eval(`(() => {
        const tag = document.querySelector('.mode-tag');
        const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
        const href = location.href;
        return JSON.stringify({
          tag: tag ? tag.innerText.trim() : '',
          rows: rows.length,
          titles: rows.map((r) => (r.innerText || '').split('\\n')[0].trim()).slice(0, 4),
          href
        });
      })()`)) || ''
    )
    let cf = {}
    try {
      cf = JSON.parse(confirmState)
    } catch (e) {}
    check(
      '「立即购买」进入结算页并标记为立即购买',
      cf.tag === '立即购买' && String(cf.href || '').includes('/orders/confirm'),
      `按钮=${buyClick} 标签=${cf.tag || '(空)'} url=${cf.href || ''}`
    )
    check(
      '结算页只包含「立即购买」的这 1 本（未连带购物车其他商品）',
      cf.rows === 1,
      `行数=${cf.rows} 商品=${JSON.stringify(cf.titles || [])}（购物车另有 ${cartIdsBefore.length} 条）`
    )

    // ---------- 14. 结算运费明细（S5） ----------
    const settleReq = c2.callOf('POST', '/api/order/settle-preview')
    const settlePost = settleReq ? String(settleReq.postData || '') : ''
    const amtState = JSON.parse(
      String(
        (await c2.eval(`(() => {
        const rows = [...document.querySelectorAll('.amount-section .amount-row')];
        const txt = (el) => (el ? (el.innerText || '').replace(/\\s+/g, ' ').trim() : '');
        const pick = (kw) => rows.map(txt).find((t) => t.includes(kw)) || '';
        return JSON.stringify({
          goods: pick('商品总额'),
          freight: pick('运费'),
          total: pick('应付金额'),
          freeTip: txt(document.querySelector('.amount-section .free-tip'))
        });
      })()`)) || '{}'
      )
    )
    const numOf = (s) => Number(String(s).replace(/[^\d.]/g, '')) || 0
    check(
      '结算页显式传 cartIds 调 settle-preview（金额以服务端为准）',
      !!settleReq && settlePost.includes('cartIds'),
      settlePost ? settlePost.slice(0, 140) : '未捕获到 POST /api/order/settle-preview'
    )
    const sumOk =
      numOf(amtState.total) > 0 &&
      Math.abs(numOf(amtState.goods) + numOf(amtState.freight) - numOf(amtState.total)) < 0.01
    check(
      '结算页三项金额自洽（商品总额 + 运费 = 应付）',
      sumOk,
      `${amtState.goods} | ${amtState.freight} | ${amtState.total} | 提示=${amtState.freeTip || '(无)'}`
    )

    // ---------- 15. 提交订单并校验时间线（N3） ----------
    c2.clearRequests()
    const submitClick = String((await c2.eval(CLICK_BY_TEXT('.submit-row .el-button', '提交订单'))) || '')
    await sleep(6500)
    const orderState = JSON.parse(
      String(
        (await c2.eval(`(() => {
        const steps = [...document.querySelectorAll('.el-step')];
        return JSON.stringify({
          count: steps.length,
          titles: steps.map((s) => {
            const t = s.querySelector('.el-step__title');
            return t ? (t.innerText || '').trim() : '';
          }),
          hash: location.hash
        });
      })()`)) || '{}'
      )
    )
    const om = /\/orders\/(\d+)/.exec(String(orderState.hash || ''))
    testOrderId = om ? Number(om[1]) : null
    check(
      '提交订单后进入订单详情页',
      submitClick === 'clicked' && !!testOrderId,
      `按钮=${submitClick} url=${orderState.hash || ''}`
    )
    const wantSteps = ['提交订单', '支付成功', '商家发货', '确认收货']
    check(
      '订单时间线渲染 4 个节点且顺序正确（N3）',
      orderState.count === 4 && (orderState.titles || []).join(',') === wantSteps.join(','),
      `节点数=${orderState.count} 标题=${JSON.stringify(orderState.titles || [])}`
    )

    // ---------- 16. 小票导出真的产出文件（N8） ----------
    // 用 CDP 把下载目录指到临时目录：只断言「按钮点了」证明不了导出能用，
    // 必须看到真的落了 .xlsx 文件（XLSX.writeFile 走 Blob + a[download]，无后端参与）。
    await c2.send('Page.setDownloadBehavior', { behavior: 'allow', downloadPath: dlDir })
    const exportClick = String((await c2.eval(CLICK_BY_TEXT('.el-button', '导出小票'))) || '')
    await sleep(4000)
    const downloaded = fs.readdirSync(dlDir).filter((f) => f.toLowerCase().endsWith('.xlsx'))
    check(
      '订单小票导出产出 xlsx 文件（N8）',
      exportClick === 'clicked' && downloaded.length > 0,
      `按钮=${exportClick} 下载=${JSON.stringify(downloaded)}`
    )

    // ---------- 17. 我的书单（S1） ----------
    await c2.goto('#/booklists', 5000)
    const mineTab = String(
      (await c2.eval(CLICK_BY_TEXT('.booklist-tabs .el-tabs__item', '我的书单'))) || ''
    )
    await sleep(3000)
    const mineState = JSON.parse(
      String(
        (await c2.eval(`(() => {
        const cards = [...document.querySelectorAll('.booklist-card.mine')];
        return JSON.stringify({
          count: cards.length,
          names: cards
            .map((c) => { const n = c.querySelector('.list-name'); return n ? (n.innerText || '').trim() : ''; })
            .slice(0, 6)
        });
      })()`)) || '{}'
      )
    )
    check(
      '「我的书单」展示后端数据（S1，含接口新建的书单）',
      mineTab === 'clicked' && (mineState.names || []).includes(testBooklistName),
      `切页签=${mineTab} 书单数=${mineState.count} 名称=${JSON.stringify(mineState.names || [])}`
    )

    // ---------- 18. 书单详情 ----------
    await c2.goto('#/booklists/' + testBooklistId, 5000)
    const blDetail = JSON.parse(
      String(
        (await c2.eval(`(() => {
        const h2 = document.querySelector('.detail-header .header-info h2');
        const tag = document.querySelector('.detail-header .official-tag');
        return JSON.stringify({
          name: h2 ? (h2.innerText || '').replace(/\\s+/g, ' ').trim() : '',
          tag: tag ? (tag.innerText || '').trim() : '',
          cards: document.querySelectorAll('.book-grid .book-card').length,
          notFound: !!document.querySelector('.el-result')
        });
      })()`)) || '{}'
      )
    )
    check(
      '书单详情按 id 从后端加载并渲染图书（S1）',
      String(blDetail.name || '').includes(testBooklistName) &&
        blDetail.tag === '我的' &&
        blDetail.cards > 0,
      `名称=${blDetail.name} 标记=${blDetail.tag || '(无)'} 图书=${blDetail.cards} 空态=${blDetail.notFound}`
    )

    // ---------- 19. 浏览历史（S2） ----------
    await c2.goto('#/account/history', 5500)
    const histState = JSON.parse(
      String(
        (await c2.eval(`(() => {
        const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
        return JSON.stringify({
          count: rows.length,
          titles: rows
            .map((r) => { const t = r.querySelector('.book-title'); return t ? (t.innerText || '').trim() : ''; })
            .slice(0, 8)
        });
      })()`)) || '{}'
      )
    )
    check(
      '浏览详情后写入后端并在「我的足迹」展示（S2）',
      histState.count > 0 && (histState.titles || []).includes(String(historyBook.title || '')),
      `行数=${histState.count} 目标书「${historyBook.title}」 列表=${JSON.stringify(histState.titles || [])}`
    )
    c2.close()
  } catch (e) {
    check('脚本执行', false, e.message)
  } finally {
    // 还原本次验证产生的数据：测试订单 / 测试书单 / 浏览记录 / 测试地址 / 购物车条目
    try {
      if (testOrderId) {
        const c = await api('POST', `/api/order/${testOrderId}/cancel`, undefined, token)
        console.log(`[清理] 测试订单 #${testOrderId} 已取消（code=${c.code}）`)
      }
      if (testBooklistId) {
        const d = await api('DELETE', `/api/booklist/${testBooklistId}`, undefined, token)
        console.log(`[清理] 测试书单 #${testBooklistId} 已删除（code=${d.code}）`)
      }
      if (historyBook && historyBook.id) {
        const h = await api('DELETE', `/api/history/${historyBook.id}`, undefined, token)
        console.log(`[清理] 浏览记录（书 #${historyBook.id}）已删除（code=${h.code}）`)
      }
      if (createdAddressId) {
        const a = await api('DELETE', `/api/address/${createdAddressId}`, undefined, token)
        console.log(`[清理] 测试地址 #${createdAddressId} 已删除（code=${a.code}）`)
      }
    } catch (e) {
      console.log('[清理] 业务数据还原失败：' + e.message)
    }
    // 还原购物车：清掉本次验证新增的条目（含「立即购买」多加的那一本）
    try {
      const now = await api('GET', '/api/cart/list', undefined, token)
      const ids = ((now.data || {}).list || [])
        .map((i) => i.id)
        .filter((id) => !cartIdsBefore.includes(id))
      if (ids.length) await api('POST', '/api/cart/removeBatch', ids, token)
      const after = await api('GET', '/api/cart/list', undefined, token)
      console.log(
        `\n[清理] 本次新增 ${ids.length} 条已移除，购物车现有 ${
          ((after.data || {}).list || []).length
        } 条（验证前 ${cartIdsBefore.length} 条）`
      )
    } catch (e) {
      console.log('\n[清理] 失败：' + e.message)
    }
    await sleep(300)
    chrome.kill()
    try {
      fs.rmSync(userDir, { recursive: true, force: true })
      fs.rmSync(dlDir, { recursive: true, force: true })
    } catch (e) {}
  }

  const failed = results.filter((r) => !r.ok).length
  console.log(`\n===== PC 商城端到端自测：通过 ${results.length - failed} / 失败 ${failed} =====`)
  process.exit(failed ? 1 : 0)
}

main()
