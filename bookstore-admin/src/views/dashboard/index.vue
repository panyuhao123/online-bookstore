<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">首页总览</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-skeleton :loading="loading" animated>
      <template #template>
        <el-row :gutter="16">
          <el-col v-for="i in 4" :key="i" :span="6">
            <div class="app-card"><el-skeleton-item variant="h3" style="width: 50%" /></div>
          </el-col>
        </el-row>
      </template>
      <template #default>
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="app-card stat-card">
              <div class="stat-label">在售图书</div>
              <div class="stat-value">{{ data.onSaleBookCount }}</div>
              <div class="stat-extra">共 {{ data.bookCount }} 本图书</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="app-card stat-card">
              <div class="stat-label">注册用户</div>
              <div class="stat-value">{{ data.userCount }}</div>
              <div class="stat-extra">累计 {{ data.categoryCount }} 个分类</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="app-card stat-card">
              <div class="stat-label">有效订单</div>
              <div class="stat-value">{{ data.orderCount }}</div>
              <div class="stat-extra">已完成支付及后续状态</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="app-card stat-card">
              <div class="stat-label">累计销售额</div>
              <div class="stat-value">¥{{ money(data.salesAmount) }}</div>
              <div class="stat-extra">不含待支付与已取消</div>
            </div>
          </el-col>
        </el-row>

        <el-alert
          v-if="data.lowStockBookCount > 0"
          type="warning"
          show-icon
          :closable="false"
          class="tip-alert"
        >
          <template #title>
            有 {{ data.lowStockBookCount }} 本图书库存不足（≤ 5），建议前往图书管理补货
          </template>
        </el-alert>
      </template>
    </el-skeleton>

    <div class="app-card chart-card">
      <div class="chart-header">
        <span class="stat-label">销售趋势</span>
        <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="14">近 14 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="trendRef" class="chart" />
    </div>

    <div class="app-card chart-card">
      <div class="chart-header">
        <span class="stat-label">图书销量排行 Top 10</span>
      </div>
      <div ref="rankRef" class="chart" />
    </div>

    <div class="app-card quick-card">
      <div class="stat-label">快捷操作</div>
      <div class="quick-actions">
        <el-button type="primary" :icon="Plus" @click="$router.push('/book')">新增图书</el-button>
        <el-button :icon="Tickets" @click="$router.push('/order?status=2')">
          处理待发货（{{ data.pendingShipCount }}）
        </el-button>
        <el-button :icon="Reading" @click="$router.push('/book?status=0')">查看已下架</el-button>
        <el-button :icon="Document" @click="$router.push('/log')">操作日志</el-button>
      </div>
      <div class="app-muted order-flow">
        订单流转：用户下单（待支付）→ 模拟支付（已支付）→ 后台发货（已发货）→ 用户确认收货（已完成）
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { Refresh, Plus, Tickets, Reading, Document } from '@element-plus/icons-vue'
import { overview, salesTrend, topBooks } from '@/api/admin'

const loading = ref(false)
const data = ref({})

const trendDays = ref(7)
const trendRef = ref(null)
const rankRef = ref(null)
let trendChart = null
let rankChart = null

/** 金额统一两位小数展示，避免 204.0 这种显示 */
function money(value) {
  return Number(value || 0).toFixed(2)
}

function renderTrend(list) {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption(
    {
      tooltip: { trigger: 'axis', valueFormatter: (v) => '¥' + Number(v || 0).toFixed(2) },
      grid: { left: 56, right: 20, top: 24, bottom: 36 },
      xAxis: {
        type: 'category',
        data: list.map((i) => i.date.slice(5)),
        axisLine: { lineStyle: { color: '#dcdfe6' } }
      },
      yAxis: {
        type: 'value',
        name: '销售额(元)',
        nameTextStyle: { color: '#909399' }
      },
      series: [
        {
          name: '销售额',
          type: 'line',
          smooth: true,
          areaStyle: { opacity: 0.15 },
          itemStyle: { color: '#C8362F' },
          data: list.map((i) => Number(i.amount || 0))
        }
      ]
    },
    true
  )
}

function renderRank(list) {
  if (!rankRef.value) return
  if (!rankChart) rankChart = echarts.init(rankRef.value)
  // 后端按销量倒序返回，图表 y 轴自下而上排列，反转后第一名才在最上方
  const rows = [...list].reverse()
  rankChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 150, right: 40, top: 12, bottom: 24 },
      xAxis: { type: 'value', name: '销量', nameTextStyle: { color: '#909399' } },
      yAxis: {
        type: 'category',
        data: rows.map((i) => i.title),
        axisLabel: { width: 140, overflow: 'truncate' }
      },
      series: [
        {
          name: '销量',
          type: 'bar',
          barMaxWidth: 18,
          itemStyle: { color: '#C8362F', borderRadius: [0, 4, 4, 0] },
          data: rows.map((i) => i.sales)
        }
      ]
    },
    true
  )
}

/** 图表单独加载：统计卡片失败不应连累图表，反之亦然 */
async function loadCharts() {
  await Promise.all([loadTrend(), loadRank()])
}

async function loadTrend() {
  try {
    const list = await salesTrend(trendDays.value)
    await nextTick()
    renderTrend(list || [])
  } catch (e) {
    ElMessage.error('销售趋势加载失败')
  }
}

async function loadRank() {
  try {
    const list = await topBooks(10)
    await nextTick()
    renderRank(list || [])
  } catch (e) {
    ElMessage.error('销量排行加载失败')
  }
}

async function load() {
  loading.value = true
  try {
    data.value = await overview()
  } catch (e) {
    ElMessage.error('统计数据加载失败')
  } finally {
    loading.value = false
  }
  await loadCharts()
}

function handleResize() {
  if (trendChart) trendChart.resize()
  if (rankChart) rankChart.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) trendChart.dispose()
  if (rankChart) rankChart.dispose()
})
</script>

<style scoped>
.stat-card {
  min-height: 110px;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  margin: 8px 0;
  font-size: 26px;
  font-weight: 600;
}

.stat-extra {
  color: #c0c4cc;
  font-size: 12px;
}

.tip-alert {
  margin-top: 16px;
}

.chart-card {
  margin-top: 16px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart {
  height: 300px;
  margin-top: 8px;
}

.quick-card {
  margin-top: 16px;
}

.quick-actions {
  margin: 12px 0;
  display: flex;
  gap: 10px;
}

.order-flow {
  line-height: 1.8;
}
</style>
