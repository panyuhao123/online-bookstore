<template>
  <div class="dash">
    <div class="app-header">
      <div class="head-left">
        <h2 class="app-title">首页总览</h2>
        <span class="app-muted">数据截至 {{ updatedAt }}</span>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <!-- ==================== KPI 带 ==================== -->
    <div class="kpis">
      <div v-for="k in kpis" :key="k.key" class="kpi" :class="'kpi--' + k.tone">
        <span class="kpi__icon"><el-icon><component :is="k.icon" /></el-icon></span>
        <div class="kpi__body">
          <div class="kpi__label">
            {{ k.label }}
            <span v-if="k.alert" class="kpi__dot" :title="k.alert"></span>
          </div>
          <div class="kpi__value num">{{ k.value }}</div>
          <div class="kpi__foot" :class="{ 'is-alert': k.alert }">{{ k.foot }}</div>
        </div>
      </div>
    </div>

    <!-- ==================== 状态计数条：既是分布，也是筛选入口 ==================== -->
    <div class="app-card strip">
      <span class="strip__title">订单状态</span>
      <div class="strip__chips">
        <button
          v-for="s in statusChips"
          :key="s.status"
          type="button"
          class="chip"
          :style="{ '--dc': s.color }"
          @click="$router.push('/order?status=' + s.status)"
        >
          <i></i>{{ s.text }}<b class="num">{{ s.count }}</b>
        </button>
      </div>
      <span class="strip__total">全部 <b class="num">{{ totalOrders }}</b> 单<span class="strip__hint">含已取消</span></span>
    </div>

    <!-- ==================== 趋势 + 环形分布 ==================== -->
    <div class="grid-main">
      <div class="app-card">
        <div class="card-head">
          <div class="card-title">近 {{ trendDays }} 天{{ trendMetric === 'amount' ? '销售额' : '订单量' }}</div>
          <div class="card-tools">
            <el-radio-group v-model="trendMetric" size="small" @change="renderTrend">
              <el-radio-button value="amount">销售额</el-radio-button>
              <el-radio-button value="orderCount">订单量</el-radio-button>
            </el-radio-group>
            <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
              <el-radio-button :value="7">7 天</el-radio-button>
              <el-radio-button :value="14">14 天</el-radio-button>
              <el-radio-button :value="30">30 天</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <div ref="trendRef" class="chart chart--trend" />
        <div class="card-foot">
          仅统计已支付 / 已发货 / 已完成 —— 待支付与已取消不计入，避免趋势虚高。
        </div>
      </div>

      <div class="app-card">
        <div class="card-head">
          <div class="card-title">状态分布</div>
          <span class="app-muted">含待支付与已取消</span>
        </div>
        <div ref="donutRef" class="chart chart--donut" />
      </div>
    </div>

    <!-- ==================== 三栏清单：待办 / 热销 / 最近订单 ==================== -->
    <div class="grid-three">
      <div class="app-card">
        <div class="card-head">
          <div class="card-title">待办与预警</div>
          <span class="app-muted">点条目直达处理页</span>
        </div>
        <ul class="todo">
          <li v-for="t in todos" :key="t.key" @click="$router.push(t.to)">
            <span class="todo__dot" :class="'todo__dot--' + t.tone"></span>
            <span class="todo__name">{{ t.name }}</span>
            <b class="num" :class="{ 'is-alert': t.alert }">{{ t.value }}</b>
            <span class="todo__go">›</span>
          </li>
        </ul>
        <div v-if="!todos.length" class="empty">暂无待处理事项</div>
        <div class="flow">
          订单流转：用户下单（待支付）→ 模拟支付（已支付）→ 后台发货（已发货）→ 用户确认收货（已完成）
        </div>
      </div>

      <div class="app-card">
        <div class="card-head">
          <div class="card-title">热销 Top 5</div>
          <router-link class="link" to="/book">全部图书</router-link>
        </div>
        <ol class="rank">
          <li v-for="(b, i) in topList.slice(0, 5)" :key="b.bookId || b.title">
            <span class="rank__no" :class="{ 'rank__no--top': i < 3 }">{{ i + 1 }}</span>
            <span class="rank__name" :title="b.title">{{ b.title }}</span>
            <span class="rank__bar"><i :style="{ width: barWidth(b.sales) }"></i></span>
            <b class="num">{{ b.sales }}</b>
          </li>
        </ol>
        <div v-if="!topList.length" class="empty">暂无销量数据</div>
      </div>

      <div class="app-card">
        <div class="card-head">
          <div class="card-title">最近订单</div>
          <router-link class="link" to="/order">全部订单</router-link>
        </div>
        <ul class="recent">
          <li v-for="o in recentOrders" :key="o.id" @click="$router.push('/order')">
            <div class="recent__main">
              <span class="recent__no">{{ o.orderNo }}</span>
              <span class="recent__user">{{ o.userNickname || o.userPhone || '—' }}</span>
            </div>
            <div class="recent__side">
              <span class="num recent__amount">¥{{ money(o.totalAmount) }}</span>
              <span class="pill" :class="'pill--' + statusTone(o.status)">{{ statusText(o.status) }}</span>
            </div>
          </li>
        </ul>
        <div v-if="!recentOrders.length" class="empty">暂无订单</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { STATUS_META } from '@/utils/order-status'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { overview, salesTrend, topBooks } from '@/api/admin'
import { pageOrders } from '@/api/order'

const BRAND = '#6C4DF2'

const loading = ref(false)
const updatedAt = ref('—')
const overviewData = ref({})
const statusCounts = ref([])

const trendDays = ref(7)
const trendMetric = ref('amount')
const trendList = ref([])
const topList = ref([])
const recentOrders = ref([])

const trendRef = ref(null)
const donutRef = ref(null)
let trendChart = null
let donutChart = null

function money(value) {
  return Number(value || 0).toFixed(2)
}

function n(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

/**
 * 状态计数条与环图的分母：五种状态之和（含已取消）。
 *
 * 这里刻意不用 orderCount —— 那个字段按约定不含已取消，
 * 而分布图必须让五段加起来等于总数，否则环图会缺一块、计数条与 KPI 也会对不上。
 */
const totalOrders = computed(() => {
  const sum = (statusCounts.value || []).reduce((acc, i) => acc + Number(i.orderCount || 0), 0)
  return n(sum)
})

/** 状态计数：后端固定返回 5 行，这里只做「文案 + 颜色」映射 */
const statusChips = computed(() =>
  STATUS_META.map((meta) => {
    const hit = (statusCounts.value || []).find((i) => Number(i.status) === meta.status)
    return { ...meta, count: n(hit ? hit.orderCount : 0) }
  })
)

const kpis = computed(() => {
  const d = overviewData.value
  const pending = Number(d.pendingShipCount || 0)
  return [
    {
      key: 'onSale',
      label: '在售图书',
      value: n(d.onSaleBookCount),
      foot: `共 ${n(d.bookCount)} 本 · ${n(d.categoryCount)} 个分类`,
      icon: 'Reading',
      tone: 'brand'
    },
    {
      key: 'user',
      label: '注册用户',
      value: n(d.userCount),
      foot: '累计注册账号',
      icon: 'User',
      tone: 'info'
    },
    {
      key: 'order',
      label: '有效订单',
      value: n(d.orderCount),
      foot: '不含已取消',
      icon: 'Tickets',
      tone: 'ok'
    },
    {
      key: 'sales',
      label: '累计销售额',
      value: '¥' + money(d.salesAmount),
      foot: '已支付 / 已发货 / 已完成',
      icon: 'Money',
      tone: 'brand'
    },
    {
      key: 'pending',
      label: '待发货',
      value: n(pending),
      foot: pending > 0 ? '已支付但未发货' : '暂无待处理',
      icon: 'Van',
      tone: pending > 0 ? 'warn' : 'gray',
      alert: pending > 0 ? '有 ' + pending + ' 单已支付但未发货' : ''
    }
  ]
})

/** 待办清单：只列「有数才显示」的项，全 0 时给出空态而不是一排 0 */
const todos = computed(() => {
  const d = overviewData.value
  const unpaid = countOf(1)
  const low = Number(d.lowStockBookCount || 0)
  const list = []
  if (Number(d.pendingShipCount || 0) > 0) {
    list.push({ key: 'ship', name: '待发货订单', value: d.pendingShipCount, tone: 'warn', to: '/order?status=2', alert: true })
  }
  if (unpaid > 0) {
    list.push({ key: 'unpaid', name: '待支付订单', value: unpaid, tone: 'brand', to: '/order?status=1' })
  }
  if (low > 0) {
    list.push({ key: 'stock', name: '库存告急图书', value: low, tone: 'danger', to: '/book', alert: true })
  }
  list.push({ key: 'cancelled', name: '已取消（仅统计）', value: countOf(5), tone: 'gray', to: '/order?status=5' })
  return list
})

function countOf(status) {
  const hit = (statusCounts.value || []).find((i) => Number(i.status) === status)
  return hit ? Number(hit.orderCount || 0) : 0
}

function statusText(status) {
  const hit = STATUS_META.find((i) => i.status === Number(status))
  return hit ? hit.text : '未知'
}

function statusTone(status) {
  const hit = STATUS_META.find((i) => i.status === Number(status))
  return hit ? hit.tone : 'gray'
}

function barWidth(sales) {
  const max = Math.max(...topList.value.map((i) => Number(i.sales || 0)), 1)
  return Math.max(4, Math.round((Number(sales || 0) / max) * 100)) + '%'
}

/* ------------------------------- 图表 ------------------------------- */

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)

  const isAmount = trendMetric.value === 'amount'
  const values = trendList.value.map((i) => Number((isAmount ? i.amount : i.orderCount) || 0))
  const labels = trendList.value.map((i) => String(i.date || '').slice(5))

  trendChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: isAmount ? 'line' : 'shadow' },
        valueFormatter: (v) => (isAmount ? '¥' + Number(v || 0).toFixed(2) : Number(v || 0) + ' 单')
      },
      grid: { left: 56, right: 16, top: 22, bottom: 30 },
      xAxis: {
        type: 'category',
        data: labels,
        boundaryGap: !isAmount,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#E4E7EC' } },
        axisLabel: { color: '#8C93A1', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#F1F2F5' } },
        axisLabel: { color: '#8C93A1', fontSize: 11 }
      },
      series: [
        isAmount
          ? {
              name: '销售额',
              type: 'line',
              smooth: true,
              showSymbol: false,
              symbolSize: 6,
              lineStyle: { width: 2.2, color: BRAND },
              itemStyle: { color: BRAND },
              areaStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: 'rgba(108,77,242,0.22)' },
                  { offset: 1, color: 'rgba(108,77,242,0)' }
                ])
              },
              data: values
            }
          : {
              name: '订单量',
              type: 'bar',
              barMaxWidth: 18,
              itemStyle: { color: BRAND, borderRadius: [4, 4, 0, 0] },
              data: values
            }
      ]
    },
    true
  )
}

function renderDonut() {
  if (!donutRef.value) return
  if (!donutChart) donutChart = echarts.init(donutRef.value)

  const total = statusChips.value.reduce((sum, i) => sum + Number(String(i.count).replace(/,/g, '')), 0)
  const data = statusChips.value.map((i) => ({
    name: i.text,
    value: Number(String(i.count).replace(/,/g, '')),
    itemStyle: { color: i.color }
  }))

  donutChart.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}：{c} 单（{d}%）' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 7,
        itemHeight: 7,
        itemGap: 12,
        textStyle: { color: '#5B6270', fontSize: 11 }
      },
      series: [
        {
          type: 'pie',
          radius: ['58%', '78%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: {
            show: true,
            position: 'center',
            formatter: () => '{v|' + total + '}\n{t|订单总数}',
            rich: {
              v: { fontSize: 24, fontWeight: 700, color: '#1F2328', lineHeight: 30 },
              t: { fontSize: 11, color: '#8C93A1' }
            }
          },
          labelLine: { show: false },
          emphasis: { scale: true, scaleSize: 4 },
          data
        },
        // 无数据时给一圈灰环，避免中间数字孤零零地悬空
        ...(total === 0
          ? [{ type: 'pie', radius: ['58%', '78%'], center: ['50%', '44%'], silent: true, label: { show: false }, data: [{ value: 1, itemStyle: { color: '#F1F2F5' } }] }]
          : [])
      ]
    },
    true
  )
}

/* ------------------------------- 数据加载 ------------------------------- */

async function loadOverview() {
  try {
    const d = await overview()
    overviewData.value = d || {}
    statusCounts.value = (d && d.statusCounts) || []
  } catch (e) {
    ElMessage.error('统计数据加载失败')
  }
}

async function loadTrend() {
  try {
    const list = await salesTrend(trendDays.value)
    trendList.value = list || []
    await nextTick()
    renderTrend()
  } catch (e) {
    ElMessage.error('销售趋势加载失败')
  }
}

async function loadTop() {
  try {
    const list = await topBooks(5)
    topList.value = list || []
  } catch (e) {
    topList.value = []
  }
}

async function loadRecent() {
  try {
    const res = await pageOrders({ pageNum: 1, pageSize: 5 })
    recentOrders.value = (res && res.list) || []
  } catch (e) {
    recentOrders.value = []
  }
}

async function loadAll() {
  loading.value = true
  try {
    // 概览先落地：KPI、计数条、环图都依赖它，先渲染出来体感更快
    await loadOverview()
    await nextTick()
    renderDonut()
    await Promise.all([loadTrend(), loadTop(), loadRecent()])
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } finally {
    loading.value = false
  }
}

function handleResize() {
  if (trendChart) trendChart.resize()
  if (donutChart) donutChart.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) trendChart.dispose()
  if (donutChart) donutChart.dispose()
})
</script>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.head-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

/* ------------------------------ KPI 带 ------------------------------ */
.kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.kpi {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-card);
}

/* 右上角色晕：只用 ~30% 透明度，起分区作用而不抢数字 */
.kpi::after {
  content: '';
  position: absolute;
  top: -22px;
  right: -22px;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  opacity: 0.3;
  background: radial-gradient(circle, var(--tint) 0%, transparent 70%);
  pointer-events: none;
}

.kpi--brand {
  --tint: #6c4df2;
  --chip: var(--brand-weak);
  --chip-ink: var(--brand-active);
}

.kpi--info {
  --tint: #3e7bdb;
  --chip: var(--info-weak);
  --chip-ink: var(--info-text);
}

.kpi--ok {
  --tint: #1f9d63;
  --chip: var(--ok-weak);
  --chip-ink: var(--ok-text);
}

.kpi--warn {
  --tint: #e09520;
  --chip: var(--warn-weak);
  --chip-ink: var(--warn-text);
}

.kpi--gray {
  --tint: #8c93a1;
  --chip: var(--gray-weak);
  --chip-ink: var(--gray-text);
}

.kpi__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 10px;
  background: var(--chip);
  color: var(--chip-ink);
  font-size: 17px;
}

.kpi__body {
  min-width: 0;
}

.kpi__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-3);
}

.kpi__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--danger);
  box-shadow: 0 0 0 3px rgba(229, 69, 60, 0.14);
}

.kpi__value {
  margin: 6px 0 4px;
  font-size: 25px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.5px;
  color: var(--ink-1);
}

.kpi__foot {
  font-size: 11.5px;
  color: var(--ink-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi__foot.is-alert {
  color: var(--danger-text);
}

/* ------------------------------ 状态计数条 ------------------------------ */
.strip {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 18px;
}

.strip__title {
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
}

.strip__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: var(--r-chip);
  background: var(--gray-weak);
  color: var(--ink-2);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.chip i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dc);
}

.chip b {
  font-weight: 700;
  color: var(--ink-1);
}

.chip:hover {
  background: #fff;
  border-color: var(--dc);
}

.strip__total {
  flex: none;
  font-size: 12px;
  color: var(--ink-3);
}

.strip__total b {
  color: var(--ink-1);
  font-size: 14px;
}

.strip__hint {
  margin-left: 6px;
  color: var(--ink-4);
}

/* ------------------------------ 卡片通用 ------------------------------ */
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-1);
}

.card-title::before {
  content: '';
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--brand);
}

.card-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-foot {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--ink-4);
  line-height: 1.7;
}

.link {
  font-size: 12px;
  color: var(--brand);
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}

.grid-main {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 14px;
}

.grid-three {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.chart {
  width: 100%;
}

.chart--trend {
  height: 240px;
}

.chart--donut {
  height: 268px;
}

/* ------------------------------ 待办清单 ------------------------------ */
.todo {
  margin: 0;
  padding: 0;
  list-style: none;
}

.todo li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.todo li + li {
  border-top: 1px solid var(--line);
}

.todo li:hover {
  background: var(--gray-weak);
}

.todo__dot {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
}

.todo__dot--warn {
  background: var(--warn);
}

.todo__dot--brand {
  background: var(--brand);
}

.todo__dot--danger {
  background: var(--danger);
}

.todo__dot--gray {
  background: #b4bac6;
}

.todo__name {
  flex: 1;
  font-size: 13px;
  color: var(--ink-2);
}

.todo b {
  font-size: 15px;
  color: var(--ink-1);
}

.todo b.is-alert {
  color: var(--danger-text);
}

.todo__go {
  color: var(--ink-4);
  font-size: 14px;
}

/* ------------------------------ 排行榜 ------------------------------ */
.rank {
  margin: 0;
  padding: 0;
  list-style: none;
}

.rank li {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) 46px 34px;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.rank__no {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--gray-weak);
  color: var(--ink-3);
  font-size: 11px;
  font-weight: 600;
}

.rank__no--top {
  background: var(--brand-weak);
  color: var(--brand-active);
}

.rank__name {
  font-size: 12.5px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank__bar {
  height: 5px;
  border-radius: 3px;
  background: var(--gray-weak);
  overflow: hidden;
}

.rank__bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #9882f6, var(--brand));
}

.rank b {
  font-size: 12.5px;
  color: var(--ink-1);
  text-align: right;
}

/* ------------------------------ 最近订单 ------------------------------ */
.recent {
  margin: 0;
  padding: 0;
  list-style: none;
}

.recent li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 8px;
  border-radius: 10px;
  cursor: pointer;
}

.recent li + li {
  border-top: 1px solid var(--line);
}

.recent li:hover {
  background: var(--gray-weak);
}

.recent__main {
  min-width: 0;
}

.recent__no {
  display: block;
  font-size: 12px;
  color: var(--ink-1);
  font-variant-numeric: tabular-nums;
}

.recent__user {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-4);
}

.recent__side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.recent__amount {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-1);
}

.empty {
  padding: 26px 0;
  text-align: center;
  font-size: 12px;
  color: var(--ink-4);
}

/* 三段卡片等高，待办条目少时用流程说明把底部补上，不留空白 */
.flow {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--bg);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* ------------------------------ 响应式 ------------------------------ */
@media (max-width: 1400px) {
  .kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1200px) {
  .grid-main,
  .grid-three {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
