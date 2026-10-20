<template>
  <div class="order-page">
    <div class="app-header">
      <div class="head-left">
        <h2 class="app-title">订单管理</h2>
        <span class="app-muted">下单 → 支付 → 发货 → 确认收货，全流程在此闭环</span>
      </div>
      <div class="head-right">
        <el-button :icon="Download" :loading="exporting" @click="handleExport">导出 CSV</el-button>
        <el-button type="danger" plain :icon="Delete" @click="handleCancelTimeout">取消超时订单</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- ==================== 汇总条：先回答「这批单有多大、有没有积压」 ==================== -->
    <div class="app-card summary">
      <div class="summary__cell">
        <span class="summary__label">筛选结果</span>
        <b class="summary__value num">{{ total }}</b>
        <span class="summary__foot">单 · 当前筛选条件</span>
      </div>
      <div class="summary__cell">
        <span class="summary__label">待发货</span>
        <b class="summary__value num" :class="{ 'is-alert': stats.pendingShipCount > 0 }">
          {{ stats.pendingShipCount || 0 }}
        </b>
        <span class="summary__foot">单 · 已支付未发货</span>
      </div>
      <div class="summary__cell">
        <span class="summary__label">累计销售额</span>
        <b class="summary__value num">¥{{ money(stats.salesAmount) }}</b>
        <span class="summary__foot">全店 · 已支付及之后</span>
      </div>
      <div class="summary__dist">
        <div class="summary__dist-head">
          <span class="summary__label">全部订单状态分布</span>
          <span class="summary__total">共 {{ totalAllOrders }} 单 · 含已取消</span>
        </div>
        <div class="dist-bar">
          <i
            v-for="s in distSegments"
            :key="s.status"
            :style="{ width: s.width, background: s.color }"
            :title="s.text + ' ' + s.count + ' 单'"
          ></i>
        </div>
        <div class="dist-legend">
          <span v-for="s in statusChips" :key="s.status">
            <i :style="{ background: s.color }"></i>{{ s.text }} <b class="num">{{ s.count }}</b>
          </span>
        </div>
      </div>
    </div>

    <!-- ==================== 筛选卡：关键词 + 状态胶囊 + 日期区间 ==================== -->
    <div class="app-card filter">
      <div class="filter__row">
        <el-input
          v-model="query.keyword"
          placeholder="订单号 / 手机号"
          clearable
          style="width: 210px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="下单起始日"
          end-placeholder="下单结束日"
          style="width: 260px"
          @change="onDateChange"
        />
        <div class="quick">
          <button
            v-for="q in QUICK_RANGES"
            :key="q.key"
            type="button"
            class="quick__btn"
            :class="{ on: activeQuick === q.key }"
            @click="applyQuick(q)"
          >
            {{ q.label }}
          </button>
        </div>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
      </div>

      <div class="filter__row filter__row--status">
        <span class="filter__label">状态</span>
        <div class="chips">
          <button
            type="button"
            class="chip"
            :class="{ on: query.status === null }"
            style="--dc: #6C4DF2"
            @click="pickStatus(null)"
          >
            <i></i>全部<b class="num">{{ totalAllOrders }}</b>
          </button>
          <button
            v-for="s in statusChips"
            :key="s.status"
            type="button"
            class="chip"
            :class="{ on: query.status === s.status }"
            :style="{ '--dc': s.color }"
            @click="pickStatus(s.status)"
          >
            <i></i>{{ s.text }}<b class="num">{{ s.count }}</b>
          </button>
        </div>
        <span v-if="rangeText" class="filter__echo">{{ rangeText }}</span>
      </div>
    </div>

    <!-- ==================== 批量操作条：只在有勾选时出现 ==================== -->
    <transition name="bulk">
      <div v-if="selected.length" class="app-card bulkbar">
        <span class="bulkbar__text">
          已选 <b class="num">{{ selected.length }}</b> 单
          <span class="bulkbar__sub">
            其中可发货 <b class="num">{{ shippableCount }}</b> 单
            <template v-if="selected.length > shippableCount">
              （其余 {{ selected.length - shippableCount }} 单非「已支付」，发货时会被跳过）
            </template>
          </span>
        </span>
        <div class="bulkbar__actions">
          <el-button link @click="clearSelection">取消选择</el-button>
          <el-button
            type="primary"
            :icon="Van"
            :loading="batchShipping"
            :disabled="shippableCount === 0"
            @click="handleBatchShip"
          >
            批量发货
          </el-button>
        </div>
      </div>
    </transition>

    <!-- ==================== 列表 ==================== -->
    <div class="app-card">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="list"
        row-key="id"
        :border="false"
        class="order-table"
        @selection-change="onSelectionChange"
      >
        <!-- 只有「已支付」可勾选：让不可发货的行压根选不中，比勾完再提示一遍更省事 -->
        <el-table-column type="selection" width="44" :selectable="isSelectable" />

        <el-table-column label="订单号 / 下单时间" min-width="200">
          <template #default="{ row }">
            <div class="cell-main num">{{ row.orderNo }}</div>
            <div class="cell-sub">{{ formatTime(row.createTime) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="下单用户" min-width="150">
          <template #default="{ row }">
            <div class="cell-main">{{ row.userNickname || '—' }}</div>
            <div class="cell-sub num">{{ maskPhone(row.userPhone) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="num cell-amount">¥{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="件数" width="80" align="center">
          <template #default="{ row }">
            <span class="num cell-sub-strong">{{ row.totalQuantity }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="pill" :class="'pill--' + statusTone(row.status)">
              {{ row.statusText || statusText(row.status) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 2"
              type="primary"
              size="small"
              :loading="row.shipping"
              @click="handleShip(row)"
            >
              发货
            </el-button>
            <span v-else-if="row.status === 3" class="cell-sub">已发货</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有符合条件的订单" :image-size="72" />
        </template>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="handleSearch"
      />
    </div>

    <!-- ==================== 详情抽屉 ==================== -->
    <el-drawer v-model="detailVisible" title="订单详情" size="520px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="下单用户">
          {{ maskPhone(detail.userPhone) }}（{{ detail.userNickname || '-' }}）
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <span class="pill" :class="'pill--' + statusTone(detail.status)">
            {{ detail.statusText || statusText(detail.status) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="订单金额">¥{{ money(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="商品件数">{{ detail.totalQuantity }} 件</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatTime(detail.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ formatTime(detail.shipTime) }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="收货信息">{{ detail.addressSnapshot }}</el-descriptions-item>
        <el-descriptions-item label="订单备注">{{ detail.remark || '无' }}</el-descriptions-item>
      </el-descriptions>

      <div class="items-title">商品明细</div>
      <el-table v-if="detail" :data="detail.items" border size="small">
        <el-table-column prop="bookTitle" label="书名" min-width="160" show-overflow-tooltip />
        <el-table-column label="单价" width="90">
          <template #default="{ row }">¥{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column label="小计" width="100">
          <template #default="{ row }">¥{{ money(row.subtotal) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && detail.status === 2" type="primary" @click="handleShip(detail, true)">
          确认发货
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshLeft, Search, Delete, Download, Van } from '@element-plus/icons-vue'
import {
  pageOrders, getOrder, shipOrder, batchShipOrders, exportOrders, cancelTimeoutOrders
} from '@/api/order'
import { overview } from '@/api/admin'

/** 与后端 OrderStatus 枚举保持一致：1待支付 2已支付 3已发货 4已完成 5已取消 */
const STATUS_META = [
  { status: 1, text: '待支付', color: '#E09520', tone: 'warn' },
  { status: 2, text: '已支付', color: '#6C4DF2', tone: 'brand' },
  { status: 3, text: '已发货', color: '#3E7BDB', tone: 'info' },
  { status: 4, text: '已完成', color: '#1F9D63', tone: 'ok' },
  { status: 5, text: '已取消', color: '#B4BAC6', tone: 'gray' }
]

/**
 * 快捷区间在前端算好日期字符串再传给后端，而不是把「近 7 天」这种语义传给后端：
 * 后端只认 createTimeStart / createTimeEnd，区间定义留在界面这一层，
 * 换文案（把「近 7 天」改成「近一周」）就不用动接口。
 */
const QUICK_RANGES = [
  { key: 'all', label: '全部' },
  { key: 'today', label: '今日', days: 1 },
  { key: 'd7', label: '近 7 天', days: 7 },
  { key: 'd30', label: '近 30 天', days: 30 },
  { key: 'd180', label: '近 6 个月', days: 180 }
]

const route = useRoute()
const list = ref([])
const detail = ref(null)
const loading = ref(false)
const detailVisible = ref(false)
const total = ref(0)
const stats = ref({})
const dateRange = ref(null)
const activeQuick = ref('all')
const tableRef = ref()
const selected = ref([])
const batchShipping = ref(false)
const exporting = ref(false)

const query = reactive({
  keyword: '',
  status: null,
  pageNum: 1,
  pageSize: 10
})

function money(value) {
  return Number(value || 0).toFixed(2)
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '—'
}

/** 手机号脱敏：列表页会被投屏演示，完整号码没必要整屏铺开 */
function maskPhone(phone) {
  const s = String(phone || '')
  return /^\d{11}$/.test(s) ? s.slice(0, 3) + '****' + s.slice(7) : s || '—'
}

function statusTone(status) {
  const hit = STATUS_META.find((i) => i.status === Number(status))
  return hit ? hit.tone : 'gray'
}

function statusText(status) {
  const hit = STATUS_META.find((i) => i.status === Number(status))
  return hit ? hit.text : '未知'
}

const statusChips = computed(() => {
  const rows = (stats.value && stats.value.statusCounts) || []
  return STATUS_META.map((meta) => {
    const hit = rows.find((i) => Number(i.status) === meta.status)
    return { ...meta, count: Number(hit ? hit.orderCount : 0) }
  })
})

/**
 * 「全部」胶囊与分布条的合计：五种状态之和（含已取消）。
 *
 * 不用 stats.orderCount —— 那个字段不含已取消，用它做分母会让分布条少一段，
 * 点「全部」拉出来的条数（含已取消）也对不上。
 */
const totalAllOrders = computed(() => statusChips.value.reduce((acc, i) => acc + i.count, 0))

/** 100% 堆叠条：宽度按占比算，四舍五入后可能不足 100%，用最后一段兜底对齐 */
const distSegments = computed(() => {  const rows = statusChips.value
  const sum = rows.reduce((acc, i) => acc + i.count, 0) || 1
  let used = 0
  return rows.map((row, index) => {
    const isLast = index === rows.length - 1
    const width = isLast ? Math.max(0, 100 - used) : Math.round((row.count / sum) * 1000) / 10
    used += width
    return { ...row, width: width + '%' }
  })
})

const rangeText = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return ''
  const [start, end] = dateRange.value
  const days = Math.round((new Date(end) - new Date(start)) / 86400000) + 1
  return `${start} ~ ${end} · 共 ${days} 天`
})

function toDateStr(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function applyQuick(q) {
  activeQuick.value = q.key
  if (!q.days) {
    // 「全部」= 清掉日期条件，而不是传一个很宽的区间
    dateRange.value = null
    handleSearch()
    return
  }
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - (q.days - 1))
  dateRange.value = [toDateStr(start), toDateStr(end)]
  handleSearch()
}

function pickStatus(status) {
  query.status = status
  handleSearch()
}

/** 手动选日期时取消快捷区间的高亮 —— 否则会同时亮着「近 7 天」和一个自定义区间 */
function onDateChange() {
  activeQuick.value = dateRange.value && dateRange.value.length === 2 ? '' : 'all'
  handleSearch()
}

async function loadStats() {
  try {
    stats.value = (await overview()) || {}
  } catch (e) {
    stats.value = {}
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageOrders(buildParams({ pageNum: query.pageNum, pageSize: query.pageSize }))
    // 追加行内 loading 标记，避免发货按钮的 loading 串到同表其他行
    list.value = (data.list || []).map((row) => ({ ...row, shipping: false }))
    total.value = data.total || 0
    // 列表重载后旧勾选已不在当前页，必须清空，否则批量条会显示一批看不见的单
    clearSelection()
  } finally {
    loading.value = false
  }
}

/** 列表与导出共用同一套筛选参数：空值传 undefined（axios 不序列化），避免后端收到空串 */
function buildParams(extra) {
  return {
    keyword: query.keyword || undefined,
    status: query.status === null ? undefined : query.status,
    createTimeStart: dateRange.value && dateRange.value[0] ? dateRange.value[0] : undefined,
    createTimeEnd: dateRange.value && dateRange.value[1] ? dateRange.value[1] : undefined,
    ...extra
  }
}

function handleSearch() {
  query.pageNum = 1
  load()
}

function handleReset() {
  query.keyword = ''
  query.status = null
  dateRange.value = null
  activeQuick.value = 'all'
  handleSearch()
}

/* 支持从工作台的计数条 / 待办清单 / 顶栏搜索直接跳进来并预置条件 */
function applyRouteStatus() {
  const status = route.query.status
  if (status !== undefined && status !== null && status !== '') {
    query.status = Number(status)
  }
  const keyword = route.query.keyword
  if (keyword) {
    query.keyword = String(keyword)
  }
}

/**
 * 同一个路由换 query（比如在订单页又用顶栏搜了一次）不会重新挂载组件，
 * 只改 query 不监听的话，地址栏变了、列表却没变 —— 看起来像搜索失灵。
 */
watch(
  () => route.query,
  (next, prev) => {
    if (route.path !== '/order') return
    if (JSON.stringify(next) === JSON.stringify(prev)) return
    applyRouteStatus()
    handleSearch()
  }
)

async function openDetail(row) {
  detail.value = await getOrder(row.id)
  detailVisible.value = true
}

async function handleShip(row, inDrawer) {
  ElMessageBox.confirm('确认订单「' + row.orderNo + '」已发货？', '发货确认', {
    type: 'warning',
    confirmButtonText: '确认发货',
    cancelButtonText: '取消'
  })
    .then(async () => {
      row.shipping = true
      await shipOrder(row.id)
      ElMessage.success('发货成功')
      if (inDrawer) {
        detail.value.status = 3
        detail.value.statusText = '已发货'
      }
      // 状态变了就要重读列表与分布：只改本地那一行会让汇总条数字停在旧值
      await Promise.all([load(), loadStats()])
    })
    .catch(() => {})
    .finally(() => {
      row.shipping = false
    })
}

/** 只有「已支付」的订单能勾选 —— 不可发货的行压根不给勾，比勾完再弹一次提示省事 */
function isSelectable(row) {
  return row.status === 2
}

function onSelectionChange(rows) {
  selected.value = rows || []
}

function clearSelection() {
  selected.value = []
  if (tableRef.value) {
    tableRef.value.clearSelection()
  }
}

const shippableCount = computed(() => selected.value.filter((row) => row.status === 2).length)

/**
 * 批量发货。
 *
 * 后端是「逐单条件更新 + 返回跳过原因」，所以这里不把「有非已支付的单」当成错误：
 * 发出去几单就说几单，剩下的把原因原样转述给操作员。
 */
async function handleBatchShip() {
  const ids = selected.value.map((row) => row.id)
  batchShipping.value = true
  try {
    const result = await batchShipOrders(ids)
    const shipped = Number(result.shipped || 0)
    const skipped = result.skipped || []

    if (skipped.length === 0) {
      ElMessage.success(`已发货 ${shipped} 单`)
    } else if (shipped === 0) {
      ElMessage.warning(`没有可发货的订单：${skipped[0].reason}`)
    } else {
      ElMessage.warning(`已发货 ${shipped} 单，${skipped.length} 单被跳过（${skipped[0].reason}）`)
    }
    // 状态与分布都变了，列表和汇总条一起重读
    await Promise.all([load(), loadStats()])
  } finally {
    batchShipping.value = false
  }
}

/**
 * 导出 CSV。
 *
 * 分页条件不带 pageNum / pageSize：后端导出的是「当前筛选下的全部」（上限 5000 行），
 * 传了分页参数反而会让操作员以为只导出了当前这 10 条。
 */
async function handleExport() {
  exporting.value = true
  try {
    const response = await exportOrders(buildParams())
    const blob = response.data
    // 后端异常时会把 text/plain 的提示写进同一个响应，这里先认类型再决定下载
    if (blob && blob.type && blob.type.includes('json')) {
      ElMessage.error('导出失败，请稍后重试')
      return
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '订单_' + new Date().toISOString().slice(0, 10) + '.csv'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    // 释放 Blob：不释放会在页面存活期间一直占内存（导出多次后尤其明显）
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功，已开始下载')
  } finally {
    exporting.value = false
  }
}

/**
 * 手动取消超时未支付订单。
 * 阈值做成可输入：演示时填 0 可一键清理全部未支付订单，不必等定时任务到点。
 * 请求失败由 axios 拦截器统一提示，这里只处理「用户取消输入」。
 */
async function handleCancelTimeout() {
  try {
    const { value } = await ElMessageBox.prompt(
      '取消超过该时长仍未支付的订单，并回补库存与销量。填 0 表示把所有未支付订单都视为超时。',
      '取消超时订单',
      {
        type: 'warning',
        inputValue: '30',
        inputPattern: /^\d{1,4}$/,
        inputErrorMessage: '请输入 0 ~ 1440 之间的整数（分钟）',
        confirmButtonText: '执行',
        cancelButtonText: '取消'
      }
    )
    const count = await cancelTimeoutOrders(Number(value))
    ElMessage.success(count > 0 ? `已取消 ${count} 笔超时订单，库存已回补` : '没有需要取消的超时订单')
    await Promise.all([load(), loadStats()])
  } catch (e) {
    // 用户关闭输入框：静默返回
  }
}

onMounted(() => {
  applyRouteStatus()
  loadStats()
  load()
})
</script>

<style scoped>
.order-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.head-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.head-right {
  display: flex;
  gap: 8px;
}

/* ------------------------------ 汇总条 ------------------------------ */
.summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 138px)) minmax(0, 1fr);
  gap: 18px;
  align-items: center;
  padding: 14px 18px;
}

.summary__cell {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 5px;
  min-width: 0;
  padding-right: 18px;
  border-right: 1px solid var(--line);
}

.summary__label {
  width: 100%;
  font-size: 12px;
  color: var(--ink-3);
}

.summary__value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.4px;
  color: var(--ink-1);
}

.summary__value.is-alert {
  color: var(--danger-text);
}

.summary__foot {
  font-size: 11px;
  color: var(--ink-4);
}

.summary__dist {
  min-width: 0;
}

.summary__dist-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.summary__total {
  font-size: 11.5px;
  color: var(--ink-3);
  white-space: nowrap;
}

.dist-bar {
  display: flex;
  height: 7px;
  margin: 8px 0 9px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--gray-weak);
}

.dist-bar i {
  display: block;
  height: 100%;
}

.dist-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 11px;
  color: var(--ink-3);
}

.dist-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dist-legend i {
  width: 6px;
  height: 6px;
  border-radius: 2px;
}

.dist-legend b {
  color: var(--ink-1);
}

/* ------------------------------ 批量操作条 ------------------------------ */
.bulkbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-color: var(--brand-line);
  background: var(--brand-weak);
}

.bulkbar__text {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
  color: var(--ink-2);
}

.bulkbar__text > b {
  font-size: 15px;
  color: var(--brand-active);
}

.bulkbar__sub {
  font-size: 11.5px;
  color: var(--ink-3);
}

.bulkbar__sub b {
  font-size: 12px;
  color: var(--ink-1);
}

.bulkbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bulk-enter-active,
.bulk-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.bulk-enter-from,
.bulk-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ------------------------------ 表格 ------------------------------ */
.order-table :deep(td.el-table__cell) {
  padding: 9px 0;
}

.order-table :deep(.el-table__row) {
  cursor: default;
}

.items-title {
  margin: 20px 0 10px;
  font-weight: 600;
  color: var(--ink-1);
}

@media (max-width: 1280px) {
  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary__cell {
    border-right: none;
  }
}
</style>
