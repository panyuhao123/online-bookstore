# 线上书店 Web 端开发计划

> 编写日期：2026-10-17
> 依据：`bookstore-app/` 现有 uni-app 工程、`docs/线上书店App_UI设计规范.md`、后端接口约定（`bookstore-server`）
> 目标：在不引入与现有体系冲突的新框架、不动接口协议与数据结构的前提下，新增同时适配桌面浏览器与移动端浏览器的 Web 端。

---

## 一、现有 APP 工程梳理结论

### 1.1 技术栈与工程形态

| 项 | 现状 |
| --- | --- |
| 框架 | uni-app + Vue 3（`<script setup>`），`createSSRApp` 入口 |
| 工程类型 | HBuilderX 标准结构（**非 CLI**，根目录无 `package.json`） |
| 路由 | `pages.json` 声明式路由，共 **20 个页面**，hash/栈式导航（`navigateTo` / `redirectTo` / `navigateBack`） |
| 底部 Tab | **自绘** `components/tab-bar.vue`，`pages.json` 未配原生 `tabBar`（故 `switchTab` 失效，tab 级跳转一律 `redirectTo`） |
| 样式 | SCSS + `uni.scss` 设计 Token（朱砂红 `#C8362F` 体系），尺寸单位 **rpx**（750 设计稿） |
| 状态管理 | **无 Pinia/Vuex**；模块级 `ref` 单例 + `uni.$emit/$on` 事件总线（`common/cart-badge.js` 是唯一全局状态源） |
| UI 组件库 | **无第三方库**，11 个自写组件 |

### 1.2 目录结构与复用价值评估

| 目录/文件 | 内容 | 平台相关性 | Web 端复用策略 |
| --- | --- | --- | --- |
| `api/`（7 个） | book / category / cart / order / address / user / review，仅封装 `get/post` | **零平台依赖** | 直接复用，100% |
| `common/request.js` | 统一 `code` 判定、Bearer 头、401 跳登录 | 依赖 `uni.request` / `uni.showToast` / `uni.reLaunch`（H5 均有实现） | 直接复用 |
| `common/auth.js` | token / user 读写 | `uni.getStorageSync`（H5 = localStorage） | 直接复用 |
| `common/config.js` | baseUrl 三级兜底 + 探测，已有 `// #ifdef H5` 分支（H5 默认 `127.0.0.1:8080`） | 分支已就绪 | 复用，桌面/手机浏览器用同一地址；部署时改为同源 |
| `common/format.js` | 金额/时间/手机号脱敏 | 纯函数 | 直接复用 |
| `common/cover.js` | 封面地址解析、渐变占位、折扣文案 | 纯函数 | 直接复用 |
| `common/layout.js` | `getSystemInfoSync` 换算导航/Tab 高度 | H5 可用（状态栏=0、安全区=0） | 复用，桌面断点下需重算 |
| `common/cart-action.js` / `buy-now.js` / `cart-local.js` / `cart-badge.js` | 加购、立即购买、未登录本机购物车、角标广播 | `uni.showToast` + `uni.$emit` | 直接复用 |
| `common/region.js` + `region-data.js` | 省市区数据（地址页用） | 纯数据 | 直接复用 |
| `common/mock/`（4 个） | 演示数据集（9 分类 / 48 二级 / 102 本 / 20 书单） | 纯 JS | 直接复用 |
| `components/`（11 个） | nav-bar、page-nav、tab-bar、book-card、cover-box、state-view、entry-grid、entry-icon、channel-tabs、home-banner、floor-title | Vue 模板 + rpx 样式 | 复用模板，补充桌面断点样式 |
| `pages/`（20 页） | 见 1.3 | Vue 模板 + rpx 样式 | 复用模板，补充桌面断点样式 |
| `App.vue` | 全局样式类（`.card/.chip/.tag/.btn-*/ .state-box/.sk/.float-bar/.hover-*`） | 纯 CSS | 复用 + 追加桌面媒体查询 |
| `uni.scss` | 设计 Token | 纯变量 | 复用，桌面新增断点常量 |

### 1.3 页面清单（20）

- Tab 级（5）：首页 `index/index`、分类 `category/category`、书单 `booklist/list`、购物车 `cart/cart`、我的 `mine/mine`
- 浏览（4）：搜索结果 `book/list`、图书详情 `book/detail`、新书上架 `book/new`、低价优选 `book/cheap`、书单详情 `booklist/detail`
- 交易（4）：确认订单 `order/confirm`、订单列表 `order/list`、订单详情 `order/detail`、评价列表 `review/list`、发表评价 `review/edit`
- 账号（4）：登录 `login/login`、注册 `login/register`、地址列表 `address/list`、地址编辑 `address/edit`
- 调试（1）：`mine/server`（「我的」连点头像 5 次进入，**Web 端不保留**）

### 1.4 网络与数据模型（Web 端需保持一致）

- 统一返回 `{code,message,data,timestamp,requestId}`，HTTP 恒 200，成败只看 `body.code`（200/400/401/403/409/422/500）
- 鉴权：`Authorization: Bearer <token>`；`code=401` → 清 token + 跳登录
- 分页：`PageVO {list,total,pageNum,pageSize,pages}`
- 订单状态：1 待支付 / 2 已支付 / 3 已发货 / 4 已完成 / 5 已取消；用户视角文案：待支付/待发货/待收货/已完成
- 下单入参 `{addressId, cartIds}`；购物车主键 `id`；评价唯一键 `uk_review_user_book_order`
- 演示数据图书主键从 **10001** 起，详情页用 `isMockId()` 判定走 mock 还是真实接口

### 1.5 鉴权与本地存储

- 登录态：`bookstore_token` / `bookstore_user`（`uni.setStorageSync`，H5 落 localStorage）
- 后端地址：`bookstore_base_url`
- 未登录：加购写入本机购物车 `cart-local.js`，登录后按现有逻辑流转（Web 端保持一致，不新增合并逻辑）

### 1.6 原生能力依赖盘点（**核心结论**）

全量扫描 `uni.*` 调用（排除 `unpackage/`）后出现频次如下：

| API | 次数 | H5 支持情况 | 处理方式 |
| --- | --- | --- | --- |
| `uni.showToast` | 51 | 支持 | 无需改动 |
| `uni.navigateTo` / `redirectTo` / `navigateBack` / `reLaunch` | 37/10/11/2 | 支持 | 无需改动（自绘 tabBar 下仍用 `redirectTo`） |
| `uni.showLoading` / `hideLoading` / `showModal` | 12/24/13 | 支持 | 无需改动 |
| `uni.getSystemInfoSync` | 9 | 支持（状态栏/安全区=0） | 复用，桌面需重算高度 |
| `uni.request` | 3 | 支持 | 无需改动 |
| `uni.get/set/removeStorageSync` | 5/4/4 | 支持（localStorage） | 无需改动 |
| `uni.hideKeyboard` | 1（`components/nav-bar.vue:80`） | **H5 无此 API** | `// #ifndef H5` 包裹 |
| `uni.setTabBarBadge` | 0（仅注释提及） | 不适用 | 无改动 |

**结论：APP 未使用任何不可降级的原生能力**（无相机、定位、蓝牙、生物识别、推送、文件系统、扫码、支付 SDK；支付为模拟支付，仅改状态）。因此 Web 端**不存在需要替代或降级的核心能力**，改造集中在三处：平台 API 微差（`hideKeyboard`）、视口/安全区（移动端浏览器地址栏）、以及桌面宽屏布局。

---

## 二、Web 端技术方案

### 2.1 技术路线（三选一，推荐 A）

| 方案 | 做法 | 复用率 | 工期 | 风险 |
| --- | --- | --- | --- | --- |
| **A（推荐）同工程编译 H5** | 在 `bookstore-app` 内用 `#ifdef H5` + CSS 媒体查询做 Web 适配，HBuilderX「运行到浏览器 / 发行 → 网站 H5」产出静态站点 | ~95%（模板与逻辑零复制） | 最短 | 无 CLI 时不易接入自定义构建（可用 HBuilderX 发行代替） |
| B 同工程 + CLI | 在 A 基础上新增 `package.json` + `@dcloudio/vite-plugin-uni`，可 `npm run build` 部署 | ~95% | 中 | 新增 `package.json` 可能影响 HBuilderX 对工程类型的识别（需实测 App 打包仍正常） |
| C 独立 Vue3+Vite 工程 | 新建 `bookstore-web`，重写页面，仅复用 `api/` 与领域逻辑 | ~30% | 最长 | 与 APP 双份维护、视觉易漂移；且引入与现有「uni-app 一套代码」体系平行的第二套前端 |

推荐 A：**沿用 APP 技术栈、零新增框架、接口与数据结构天然一致**，完全满足约束条件；B 作为部署需要时的可选项留到阶段 5。

### 2.2 目录结构与代码复用策略

**不新建工程目录，不复制代码。** 改动全部落在 `bookstore-app` 内：

```
bookstore-app/
├── manifest.json                 # 新增/调整 h5 节点（端口、标题、rpx 上限、router）
├── pages.json                    # 基本不动（页面与 APP 共用同一份）
├── uni.scss                      # 新增断点常量：$bp-md / $bp-lg / $web-max
├── App.vue                       # 全局样式追加「桌面媒体查询」区块
├── common/
│   ├── web.js                    # 【新增】断点与桌面判定（isDesktop()/contentWidth()）
│   └── ...                       # 其余不动
├── components/
│   ├── tab-bar.vue               # 桌面隐藏（CSS）→ 顶部导航接管
│   ├── web-header.vue            # 【新增】桌面顶部导航（首页/分类/书单/购物车/我的 + 搜索）
│   ├── web-footer.vue            # 【新增】桌面页脚（可选，阶段 4）
│   └── ...                       # 其余组件仅追加桌面样式
└── pages/**                      # 仅追加桌面断点样式，不改 <script> 逻辑
```

复用原则：
1. **能用 CSS 解决的不用 JS**：桌面/移动端差异优先写 `@media (min-width: 1024px)`，避免模板里堆 `v-if`。
2. **确需平台分支用条件编译**：`// #ifdef H5` / `#ifndef H5`，保证 APP 打包产物不受影响。
3. **不引入新依赖**：不加 UI 库、不加 vue-router（沿用 uni 路由）、不加 Pinia（沿用现有单例状态 + 事件总线）。
4. **接口零改动**：`api/` 与后端不因 Web 端做任何变更。

### 2.3 视觉与交互规范（与 APP 一致 + 桌面扩展）

沿用 `docs/线上书店App_UI设计规范.md`，并扩展三条：

1. **颜色/字号/圆角/阴影一律取 `uni.scss` Token**，Web 端不新增硬编码色值。
2. **断点体系**（新增常量）：
   - `< 960px`：移动端视图，与 APP 完全一致（底部 tabBar、两列栅格、自绘导航）；uni-app H5 在此区间会让 rpx 随视口宽度线性放大（与 APP 观感一致）。
   - `≥ 960px`：桌面视图；uni-app H5 的 rpx 计算会把设备宽度上限锁死在 960 并按 375 基准渲染（源码：`width = width <= maxWidth ? width : baseWidth`，默认 `maxWidth=960, baseWidth=375`），因此桌面样式全部改用 px / `%` / flex 接管，内容区最大宽 **1200px 居中**。
3. **桌面布局映射**：

| 区域 | 移动端（<960px） | 桌面（≥960px） |
| --- | --- | --- |
| 导航 | 自绘 `nav-bar`（Logo+搜索）/ `page-nav`（返回+标题） | 顶部 `web-header`：Logo + 搜索框 + 一级导航 + 购物车（角标）+ 我的 |
| 底部 Tab | 自绘 `tab-bar`（5 项） | 隐藏，功能并入顶部导航 |
| 首页楼层 | 2 列栅格 | 5 列栅格，轮播拉宽 |
| 分类页 | 左 9 类侧栏 + 右 3 列宫格 | 左侧栏固定 + 右 5 列宫格 |
| 图书列表 | 2 列 | 4–5 列 + 顶部排序条 |
| 图书详情 | 单列堆叠 | 左封面（480px）+ 右信息 + 下方简介/评价分栏 |
| 购物车 | 列表 + 底部结算栏 | 左侧列表 + 右侧吸顶结算卡片 |
| 确认订单 | 单列 | 左地址+商品 / 右金额卡片 |
| 悬停反馈 | `hover-class`（触摸） | 追加 `@media (hover:hover)` 的 `:hover` 样式 |

### 2.4 分阶段计划

| 阶段 | 目标 | 主要改动 | 验证方式 |
| --- | --- | --- | --- |
| **阶段 0｜跑通 H5 构建** | Web 端能在浏览器打开并连上后端 | `manifest.json` h5 节点（端口 5175 避让管理端 5173/5174、标题、hash 路由）；后端 `application-local.yml` CORS 白名单补 `http://localhost:5175`、`http://127.0.0.1:5175`；**实测 rpx 上限=960 与 refresher H5 支持性** | 浏览器打开首页/分类页能取到真实数据；CORS 预检 200；桌面/手机截图正常；购物车角标有值 |
| **阶段 1｜移动端浏览器对齐** | 手机浏览器体验与 APP 一致 | `nav-bar.vue` 的 `hideKeyboard` 加 `#ifndef H5`；`100vh` 与地址栏抖动处理；`env(safe-area-inset-bottom)`=0 兜底；输入焦点/键盘收起；刷新后路由与返回行为；**登录守卫 + 登录后回跳 + 403 空态**（2.7①） | Chrome DevTools 手机模拟逐页走查（375/414 宽），全流程下单一次 |
| **阶段 2｜桌面布局** | ≥960px 具备真正 PC 形态 | 新增 `common/web.js`、`components/web-header.vue`；`uni.scss` 断点常量；`App.vue` 桌面媒体查询；各页栅格列数与详情页/购物车/结算分栏 | 960/1280/1440 三档逐页截图；手机档回归无变化 |
| **阶段 3｜桌面交互增强** | 桌面可用性补齐 | `@media (hover:hover)` 悬停态、键盘提交（`@confirm` 已支持回车）、宽屏下空/加载/失败态居中、面包屑或返回语义、页面 `title`；**写操作防重复提交锁**（2.7③） | 键盘 Tab/Enter 走通搜索→详情→加购→结算；连点下单只产生 1 笔订单 |
| **阶段 4｜联调与交付** | 全量页面自测 + 文档同步 | 19 页逐页清单自测；APP 与 Web 同源视觉对比；`docs/` 更新（概要设计、用户手册、测试报告补充 Web 端）；**安全自测清单**（2.7 四项用例） | 按自测清单过一遍；静态站点 `dist` 产物用静态服务器验证 |
| **阶段 5（可选）｜部署** | 静态站点部署与同源 | 视需要加 CLI 构建（方案 B）或直接发布 HBuilderX 发行产物；部署后 baseUrl 改同源，CORS 白名单加生产域名；**确认两端存储键前缀与退出登录清键范围**（2.7②） | 部署地址全流程跑通；admin 与 H5 同浏览器交替登录互不影响 |

### 2.5 已知风险与规避方式（含 APP 期的历史坑）

| # | 风险 | 影响 | 规避方式 |
| --- | --- | --- | --- |
| 1 | **rpx 在宽屏被放大**：H5 下 rpx 编译为 rem，默认 `rpxCalcMaxDeviceWidth=960`；当视口 > 960 时，根字号被锁回 375 基准 | 桌面断点设错会导致 960 上下字号突变 | 桌面断点必须对齐 960px（`@media (min-width: 960px)`），断点以上用 px / `%` / flex 重写桌面样式；内容区外层加 `max-width:1200px;margin:0 auto` |
| 2 | **fixed 元素不受容器 max-width 约束**（导航、底栏、结算栏） | 桌面导航全宽、与内容错位 | 桌面下 fixed 元素内部再包一层 `max-width:1200px;margin:0 auto`，与 `float-bar` 同理 |
| 3 | **scroll-view `refresher-enabled` 在 H5 的支持性** | 4 个演示页下拉刷新失效 | 阶段 0 实测：uni-h5 内置完整 `Refresher` 组件，H5 支持 `refresher-enabled`，无需降级 |
| 4 | **移动端浏览器 100vh 含地址栏** | 底部被裁/多出空白 | 沿用现有 CSS 高度模式（`flex:1;height:0`），必要时用 `--window-height` 而非 `100vh` |
| 5 | **`uni.hideKeyboard` 不存在于 H5** | 控制台告警 | `#ifndef H5` 包裹（1 处） |
| 6 | **CORS 未放行** | 桌面/手机浏览器请求全失败 | H5 dev 端口 5175，同步写入 `application-local.yml`；生产环境补域名 |
| 7 | **后端地址写死 127.0.0.1** | 手机浏览器通过局域网 IP 访问时请求打到自己 | 阶段 5 部署后改为同源相对地址；临时期用「我的 → 服务器地址」页面（调试入口）或浏览器控制台设 `localStorage.bookstore_base_url` |
| 8 | **刷新页面后页面栈丢失**，`navigateBack` 失败 | 二级页刷新后返回异常 | `page-nav.vue` 已有 `fail → reLaunch 首页` 兜底，桌面补充面包屑/首页入口 |
| 9 | **HBuilderX 与 CLI 共存**（仅方案 B） | 影响 App 打包 | 阶段 5 前不引入；若引入，先验证 HBuilderX 仍能云打包 |
| 10 | **视觉漂移**：Web 端改样式误伤 APP | APP 回归 | 所有桌面样式写在 `@media` 内，禁止修改移动端基础规则；每阶段结束回归 APP 关键页 |
| 11 | **102 张封面首屏加载** | H5 首屏慢 | 图片 `lazy-load`（H5 支持）+ 列表分页已存在 |
| 12 | **uni.$off 误用**（历史 bug） | 事件监听被全部移除 | 沿用 `offCartChanged(handler)` 带引用的写法，Web 端不新增全局事件 |
| 13 | **URL 直达绕过页面入口**（H5 新增面） | 未登录/越权页面被直接打开，出现「先渲染后报错」中间态 | 前端登录守卫（见 2.7①），受保护页 `onLoad` 首行校验，未登录直接跳登录并带回跳参数 |
| 14 | **多标签页互踢**（H5 特有，APP 单实例无此问题） | 后登录覆盖 token，旧标签页下次请求 401 | 不做多标签同步；401 文案统一为「登录已失效，请重新登录」 |
| 15 | **重复提交** | 鼠标连点/回车重复下单、重复支付 | 写操作按钮统一加「提交中」锁（见 2.7③） |
| 16 | **file:// 直接打开发行产物** | origin 为 `null`，CORS 全挂、localStorage 受限，演示白屏 | 演示一律走静态服务器；文档中写明禁止 `file://` 演示 |

### 2.6 本次范围与不做的事

**做**：19 个业务页面（剔除调试页 `mine/server`）在移动端浏览器与桌面浏览器的适配；沿用现有接口、数据结构、登录态、购物车与订单闭环。

**不做**：不新增后台管理功能（已有 `bookstore-admin`）；不改动后端接口；不引入新 UI 框架/路由/状态库；不做 PWA、SEO 站点地图、多语言、SSR。

### 2.7 鉴权与安全专项（复核后补充）

针对「越权访问 / token 冲突 / H5 特有安全点」三条质疑逐条核查源码后的结论与处置。**先说结论：①采纳（但问题定性需更正）、②不采纳（已核查不冲突）、③部分采纳。**

#### ① 越权访问 —— 采纳，但后端没有越权漏洞，真正要补的是前端路由守卫

后端归属校验已完备（不存在"改个 id 看别人数据"的问题）：

- `OrderServiceImpl.requireOwnedOrder()`（`service/impl/OrderServiceImpl.java:388-397`）：订单详情 / 支付 / 取消 / 确认收货统一走它，`!orders.getUserId().equals(userId)` 抛 `FORBIDDEN`；
- `AddressServiceImpl`（36/50/91/108 行）：地址增删改查均带 `eq(Address::getUserId, userId)`；下单时 `OrderServiceImpl:105` 校验地址归属；
- `CartServiceImpl`、`ReviewServiceImpl:141`：购物车条目与评价一律按 `userId` 过滤；
- `/api/admin/**` 强制 ADMIN 角色，用户端 token 访问直接 403。

**H5 真正新增的攻击面是 URL 直达**：APP 只能靠内部跳转进入订单/地址页，H5 可以在地址栏直接输 `#/pages/order/detail?id=5`。数据层后端挡得住，但会出现"页面先渲染 → 请求 401/403 → Toast → 跳登录"的中间态，且 `request.js` 的 401 处理是 `reLaunch('/pages/login/login')`，**登录后会丢失来源页**。因此本项落到前端：

1. 新增 `common/guard.js`：`requireLogin()`（未登录跳登录并带 `redirect` 参数）、受保护页面清单；
2. 受保护页（订单列表/详情/确认、地址、评价编辑、购物车结算、我的）`onLoad` 首行调用，**不发请求先判断**，避免 401 中间态；
3. 登录页支持 `?redirect=` 回跳（H5 特有，APP 端行为不变，用 `#ifdef H5` 或直接共用）；
4. 后端返回 403 的页面统一渲染「无权访问」空态，而不是只弹一条 Toast 后留白屏。

> 注：uni-app H5 无全局路由守卫，只能在页面 `onLoad` 校验，或用 `uni.addInterceptor('navigateTo'/'redirectTo')` 统一拦截；URL 直达场景只有 `onLoad` 兜得住，故以页面内校验为主。

#### ② admin 与 H5 token key 冲突 —— 不采纳（已核查不冲突）

- 管理后台：`bookstore-admin/src/utils/auth.js:9-11` → `bookstore_admin_token` / `bookstore_admin_info` / `bookstore_admin_base_url`；
- 用户端：`bookstore-app/common/auth.js` → `bookstore_token` / `bookstore_user`，后端地址 `bookstore_base_url`。

键名前缀本就不同，**即使将来同域部署也不会互相覆盖**；开发期两者端口不同（admin 5173/5174、H5 5175），localStorage 按 origin 隔离，更不会互踢。

写入两条约束，防止后续被改坏：

- 禁止把两端 token key 统一为同名；Web 端新增存储键一律沿用 `bookstore_` 前缀并带端标识；
- 退出登录只清自己前缀的键（现状已满足，阶段 5 同域部署时复验）。

**另记一条真实但影响可控的 H5 特有行为**：同一浏览器多标签页登录不同账号，后登录会覆盖 `bookstore_token`，旧标签页下次请求 401 被踢（APP 单实例无此问题）。本期不做多标签同步，仅统一 401 文案为「登录已失效，请重新登录」。

#### ③ H5 特有安全点 —— 部分采纳

| 点 | 核查结论 | 处置 |
| --- | --- | --- |
| 重复提交 | **成立**。`OrderServiceImpl.create()` 无防重（仅支付回调 `markPaidByOrderNo` 有条件更新幂等），H5 鼠标连点/回车比触屏更易触发重复下单 | 采纳：新增 `common/once.js` 防连点，下单 / 支付 / 确认收货 / 取消 / 提交评价的按钮统一加锁（与 `book/detail.vue` 现有 `navigating` 锁同一思路）。后端幂等键涉及协议变更，本期不做，仅作为可选增强记录 |
| XSS | **当前无注入点**。业务代码未使用 `rich-text` / `v-html` / `innerHTML`（`unpackage/` 编译产物里的 quill 与本项目无关），评价、收货人、地址、书名等用户可控内容全部走插值渲染，uni-app 默认转义 | 采纳为**规范约束**：Web 端新增代码禁止用 `rich-text`/`v-html` 渲染用户内容，禁止本地拼接 HTML。不列为必修缺陷 |
| file 协议限制 | **成立**。HBuilderX 发行产物双击以 `file://` 打开时，origin 为 `null` → 所有接口 CORS 失败、部分浏览器 localStorage 受限，表现为白屏/网络异常 | 采纳：演示统一用静态服务器（`python -m http.server` / `npx serve` / HBuilderX 内置预览）；用户手册写明「禁止用 file:// 打开演示」 |

**阶段 4 安全自测清单**（验收必跑）：

1. 未登录直接访问 `#/pages/order/list`、`#/pages/address/list` → 直接进登录页，登录后回到原页面；
2. 登录用户 A，手动把订单详情 URL 的 id 改成用户 B 的订单 → 显示「无权访问」空态，无数据泄露；
3. 下单按钮连点 5 次 → 只产生 1 笔订单；支付按钮连点 → 不重复扣状态；
4. admin（5173）与 H5（5175）在同一浏览器交替登录 → 互不影响，各自退出只清自己的键。

---

## 三、已确认决策（2026-10-17 用户确认）

1. **技术路线：A —— 同工程编译 H5**。`bookstore-app` 内做 H5 适配，不新增工程、不新增依赖，HBuilderX「运行到浏览器 / 发行 → 网站 H5」产出静态站点。方案 B（加 CLI）推迟到阶段 5，仅在部署确有需要时评估。
2. **桌面布局：响应式渐进**。<960px 保持 APP 原样（rpx 随宽度线性缩放）；≥960px 切换为桌面布局（rpx 被 uni-app H5 固定为 375 基准，桌面样式用 px 接管），顶部导航 + 多列栅格 + 1200px 居中 + 详情页与购物车分栏。
3. **范围：全量 19 页**，剔除调试页 `pages/mine/server`。

### 执行顺序

按 **阶段 0 → 阶段 4** 依次执行，每阶段结束提交「改动内容 / 影响范围 / 自测方式」，确认后再进入下一阶段；阶段 5 视部署需要再议。

---

## 四、阶段执行记录

### 阶段 0｜跑通 H5 构建（已完成 2026-10-17）

| 改动 | 内容 |
| --- | --- |
| `bookstore-app/manifest.json` | H5 devServer 端口 5173 → **5175**（避让管理端 5173/5174） |
| `bookstore-server/src/main/resources/application-local.yml` | CORS 白名单追加 `http://localhost:5175`、`http://127.0.0.1:5175` |

实测结论（已回写风险表）：

1. **rpx 不是运行时无限放大**：uni-app H5 把 rpx 编译成 rem，运行时 `width = width <= rpxCalcMaxDeviceWidth ? width : rpxCalcBaseDeviceWidth`（默认 960/375），视口 > 960 时根字号锁回 375 基准。**桌面断点必须与 960 对齐**，否则 960 上下字号会突变。
2. **下拉刷新无需降级**：uni-h5 内置完整 `Refresher` 组件，`scroll-view refresher-enabled` 在 H5 可用。
3. 编译链路：`cli.exe launch web --project <app路径> --browser Chrome` 可直接命令行运行 H5。

环境坑：沙箱注入的 `SERVER__PORT`（本次为 62834）会被 Spring Boot 宽松绑定为 `server.port`，导致后端起不来；启动命令需 `env -u SERVER__PORT -u SERVER__HOST mvn spring-boot:run`。

### 阶段 1｜移动端浏览器对齐（已完成 2026-10-17）

| 改动 | 内容 |
| --- | --- |
| `common/guard.js`（新增） | 登录守卫：`requireLogin()`、`saveRedirect/takeRedirect/clearRedirect`、`safeRedirect`（只放行站内路径） |
| `common/request.js` | 401 跳登录前记录来源页；业务错误抛出带 `code` 的 Error（供页面区分 403） |
| `components/nav-bar.vue` | `uni.hideKeyboard()` 用 `#ifndef H5` 包裹（H5 无此 API） |
| `pages/login/login.vue`、`pages/login/register.vue` | 支持登录后回跳（回跳目标存本地，不挂 URL） |
| `pages/order/list.vue`、`order/detail.vue`、`order/confirm.vue`、`address/list.vue`、`address/edit.vue`、`review/edit.vue` | `onLoad`（列表页另加 `onShow`）首行 `requireLogin()` |
| `pages/order/detail.vue` | 新增 403 分支：越权查看他人订单显示「无权查看该订单」空态 |
| 17 个页面 | `100vh` 后追加 `100dvh`（移动端浏览器地址栏伸缩不再顶掉底部），跳过调试页 `mine/server` |

自测：`tools/verify_web_guard.cjs`（CDP 驱动无头 Chrome，无第三方依赖）**5 项全通过** —— 未登录直达被拦、回跳目标写入、登录后回跳、回跳后订单渲染、清 token 后再被拦。

影响范围：APP 端同步受益（未登录进受保护页不再「先渲染再跳登录」）；移动端布局无回归（桌面截图字节级一致）。

### 阶段 2｜桌面布局（已完成 2026-10-17）

| 改动 | 内容 |
| --- | --- |
| `common/web.js`（新增） | 宽屏状态 `isWide`（matchMedia ≥960px）、`navHeight`、站点导航 `SITE_NAV`、搜索跳转 `goSearch` |
| `common/web.scss`（新增） | 全局桌面媒体查询：`.page` 1200px 居中、栅格 4/5 列、`float-bar` / `sort-bar` 内容居中、`.tabbar-holder` 高度覆盖 |
| `components/web-header.vue`（新增） | 桌面站点顶栏：Logo、一级导航、搜索框、购物车角标、我的订单/我的 |
| `components/nav-bar.vue` | 桌面渲染 `web-header`，移动端保持原样 |
| `components/page-nav.vue` | 桌面在返回标题行之上叠加 `web-header`，行高/占位响应式 |
| `components/tab-bar.vue` | 桌面 `v-if="!isWide"` 隐藏 |
| `pages/index/index.vue` | `topHolder` 改用 `navHeight` |
| `pages/book/list.vue` | `sortTop` 改用 `navHeight`；`onLoad` 读取 `takeSearchKeyword()` 兜底 |
| `pages/book/detail.vue` | 桌面分栏（左封面、右信息卡/数量/简介/评价），顶部自挂 `web-header`，隐藏移动端返回键 |
| `pages/cart/cart.vue` | 顶部自挂 `web-header`，桌面 `top-bar` 下移、`title-row` 居中、结算栏不再让出 TabBar |
| `pages/mine/mine.vue` | 顶部自挂 `web-header`，桌面去除 hero 的 statusBar padding |
| `App.vue` | `@import './common/web.scss'` |

实测结论：

1. **断点 960 与 rpx 行为切换点对齐**：<960 保持 APP 原样；960–1279 4 列；≥1280 5 列。
2. **固定元素不使用 transform**：`float-bar` / `sort-bar` 用 `padding-inline: max(24px, calc((100% - 1200px)/2 + 24px))` 把内容推到主容器区域，避免页面切换动画的 transform 影响 fixed 定位基准。
3. **所有一级入口在桌面都有站点顶栏**：`nav-bar` / `page-nav` 内嵌 `web-header`，`detail` / `cart` / `mine` 自挂 `web-header`；底部 tabBar 桌面隐藏。
4. **搜索可用**：桌面顶栏输入关键词回车 → 跳转 `/pages/book/list?keyword=...` 并正确显示结果（`tools/verify_web_search.cjs` 验证通过）。

截图资产：

- `tools/web_shot_mobile.png`：390×844 首页，与 APP 一致
- `tools/web_shot_mobile_list.png`：390×844 列表页，2 列 + 排序条 + tabBar
- `tools/web_shot_1024.png`：1024×768 首页，4 列
- `tools/web_shot_1280.png`：1280×800 首页，5 列
- `tools/web_shot_desktop.png`：1440×900 首页
- `tools/web_shot_detail.png`：1440×900 图书详情分栏
- `tools/web_shot_category.png`：1440×900 分类页（顶栏 + 返回行 + 侧栏 + 5 列）
- `tools/web_shot_cart.png`：1440×900 购物车空态
- `tools/web_shot_mine.png`：1440×900 个人中心
- `tools/web_shot_orderlist.png`：1440×900 未登录订单页被守卫拦截后的登录页
- `tools/web_search_result.png`：桌面顶栏搜索回车后的结果页

影响范围：纯 H5 视觉/布局改动，APP 端编译被 `#ifdef H5` 或 `isWide`（H5 only 才为 true）隔离，移动端无回归；`page-nav` 的行高计算改为响应式，对 APP 无影响（APP 恒为窄屏）。

---

### 阶段 3｜桌面交互增强（已完成 2026-10-17）

| 改动 | 内容 |
| --- | --- |
| `common/once.js`（新增） | 统一防重复提交锁：同名 key 执行中再触发直接丢弃；`isRunning()` 供按钮态使用 |
| `common/cart-action.js` | `addBookToCart()` 加 `once('cart:add')` 锁，覆盖首页/分类/榜单/书单详情所有卡片加购 |
| `common/buy-now.js` | `buyNow()` 加 `once('order:buy-now')` 锁，详情页与低价优选页「立即购买」同时受益 |
| `common/web.scss` | 追加 `@media (hover: hover) and (pointer: fine)`：卡片/按钮/胶囊 hover 态与 `cursor: pointer`；输入框 `:focus-visible` 焦点环；桌面 `.state-box` min-height 居中 |
| `components/web-header.vue` | 顶栏 Logo/导航/链接的 hover 态与指针形态；搜索框已有 `:focus-within` 品牌边框 |
| `components/entry-grid.vue` | 宫格入口 hover 态（标签色品牌色）与 `cursor: pointer` |
| `pages/login/login.vue`、`pages/login/register.vue`、`pages/address/edit.vue` | 密码/确认密码/详细地址输入框加 `@confirm` 与 `confirm-type`，桌面按回车即可提交 |
| `common/web.js`、`App.vue` | H5 hash 路由变化同步 `document.title`；App.vue `onLaunch` 中 `// #ifdef H5` 启动 |

实测结论：

1. **H5 标题同步**：监听 `hashchange`，19 个页面映射；首页 →「首页 - 线上书店」，分类页 →「分类 - 线上书店」。
2. **键盘提交**：登录页密码框回车 → 1 次 `POST /api/user/login` → 登录成功；地址编辑页详细地址回车同理。
3. **鼠标悬停态**：`matchMedia('(hover: hover) and (pointer: fine)').matches` 在桌面无头 Chrome 为 `true`；`.book-card:hover` 触发抬升阴影，`button`/`card`/`nav` 等可点元素 `cursor: pointer`。
4. **防重复提交**：低价优选页连点某本可售图书的「加购」两次 → 只产生 1 次 `POST /api/cart/add`（CDP Network 监控）。
5. **测试兜底**：调试中发现低价优选页首张卡片为「已售罄」会被既有库存校验正确拦截，必须选择有库存卡片才能验证防重；同时发现桌面登录页因 `page-nav` 叠加 `web-header` 出现 3 个 input，脚本改按 `type` 定位后通过。

自测：`tools/verify_web_interaction.cjs`（CDP 驱动无头 Chrome，无第三方依赖）**7 项全通过**。

截图资产：

- `tools/web_shot_desktop.png`：1440×900 首页回归
- `tools/web_shot_mobile.png`：390×844 首页回归
- `tools/web_shot_1280.png`：1280×800 首页 5 列回归
- `tools/web_shot_hover.png`：1440×900 首页，鼠标悬停图书卡片（可见轻微抬升阴影）

影响范围：所有改动均被 `#ifdef H5` 或 `(hover:hover)` 媒体查询隔离，APP 端无回归；防重锁新增于公共入口，APP 端同步受益。

---

### 执行顺序更新

阶段 0、1、2、3 已完成。按原计划进入 **阶段 4｜联调与交付**：19 页逐页清单自测、APP 与 Web 同源视觉对比、`docs/` 更新、阶段 4 安全自测清单。确认后执行。