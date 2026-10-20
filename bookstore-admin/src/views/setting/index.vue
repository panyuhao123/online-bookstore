<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">系统设置</h2>
    </div>

    <div class="app-card setting-card">
      <div class="stat-label">运费规则</div>
      <p class="app-muted setting-tip">
        这里是<b>经营参数</b>，改完保存即刻生效（该配置不加缓存，前台下次请求就会读到新值），
        不需要重启后端。金额单位为元，包邮门槛填 0 表示全场包邮。
      </p>

      <el-form v-loading="freightLoading" label-width="140px" class="freight-form">
        <el-form-item label="收取运费">
          <el-switch v-model="freight.enabled" :active-value="1" :inactive-value="0"
                     inline-prompt active-text="收" inactive-text="免" />
        </el-form-item>
        <el-form-item label="满额包邮门槛">
          <el-input-number v-model="freight.freeThreshold" :min="0" :precision="2" :step="10"
                           :disabled="freight.enabled === 0" style="width: 180px" />
          <span class="app-muted unit">元</span>
        </el-form-item>
        <el-form-item label="未达门槛运费">
          <el-input-number v-model="freight.baseFee" :min="0" :precision="2" :step="1"
                           :disabled="freight.enabled === 0" style="width: 180px" />
          <span class="app-muted unit">元</span>
        </el-form-item>
        <el-form-item label="当前规则">
          <span class="freight-tip">{{ freightTip }}</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingFreight" :disabled="freightMissing"
                     @click="saveFreight">保存运费规则</el-button>
          <el-button :disabled="savingFreight" @click="loadFreight">还原</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="freightMissing" type="warning" :closable="false" show-icon
                title="数据库缺少运费配置项，请检查 V6 迁移是否已执行" />
    </div>

    <div class="app-card setting-card">
      <div class="stat-label">后端服务地址</div>
      <p class="app-muted setting-tip">
        演示时若后端跑在另一台机器（或手机联调同一局域网），把地址改成
        http://&lt;电脑局域网IP&gt;:8080，保存后页面会重新加载。
      </p>
      <el-input v-model="baseUrl" placeholder="如 http://127.0.0.1:8080" style="max-width: 420px">
        <template #append>
          <el-button :loading="testing" @click="handleTest">测试连接</el-button>
        </template>
      </el-input>
      <div class="setting-actions">
        <el-button type="primary" :disabled="testing" @click="handleSave">保存并使用</el-button>
        <el-button @click="baseUrl = DEFAULT_BASE_URL">恢复默认</el-button>
      </div>

      <el-divider>当前登录</el-divider>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="账号">{{ admin ? admin.username : '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ admin ? admin.realName || '-' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ admin && admin.status === 1 ? '正常' : admin && admin.status === 0 ? '已停用' : '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getBaseUrl, setBaseUrl, DEFAULT_BASE_URL, getAdmin } from '@/utils/auth'
import request, { setRequestBaseUrl } from '@/api/request'
import { listConfigs, updateConfig } from '@/api/config'

const baseUrl = ref(getBaseUrl())
const testing = ref(false)
const admin = getAdmin()

/** 运费规则的三个配置键，与 V6 迁移里的种子数据同名 */
const KEY_ENABLED = 'freight.enabled'
const KEY_THRESHOLD = 'freight.free-threshold'
const KEY_FEE = 'freight.base-fee'

const freight = reactive({
  enabled: 1,
  freeThreshold: 0,
  baseFee: 0
})
const freightLoading = ref(false)
const savingFreight = ref(false)
const freightMissing = ref(false)

/** 上一次从服务端读到的值：保存时只提交真正改动过的键 */
const saved = { enabled: 1, freeThreshold: 0, baseFee: 0 }

const freightTip = computed(() => {
  if (freight.enabled === 0) return '全场包邮，不收取运费'
  return '满 ¥' + Number(freight.freeThreshold).toFixed(2) + ' 包邮，未达门槛收 ¥'
    + Number(freight.baseFee).toFixed(2)
})

function pickValue(list, key) {
  const item = (list || []).find((c) => c.configKey === key)
  return item ? String(item.configValue) : null
}

async function loadFreight() {
  freightLoading.value = true
  try {
    const list = await listConfigs()
    const enabled = pickValue(list, KEY_ENABLED)
    const threshold = pickValue(list, KEY_THRESHOLD)
    const fee = pickValue(list, KEY_FEE)
    freightMissing.value = enabled === null || threshold === null || fee === null
    freight.enabled = enabled !== null && Number(enabled) === 1 ? 1 : 0
    freight.freeThreshold = threshold !== null ? Number(threshold) : 0
    freight.baseFee = fee !== null ? Number(fee) : 0
    Object.assign(saved, {
      enabled: freight.enabled,
      freeThreshold: freight.freeThreshold,
      baseFee: freight.baseFee
    })
  } catch (e) {
    // 提示已由拦截器统一弹出，这里保持页面可用（不白屏）
  } finally {
    freightLoading.value = false
  }
}

/**
 * 只提交改动过的键。
 *
 * 后端是按 key 单独改值的接口，全量提交会把「没动的字段」也写一遍 ——
 * 一旦某个字段被别的管理员刚改过，这里就会把它覆盖回旧值。
 */
async function saveFreight() {
  const changes = []
  if (freight.enabled !== saved.enabled) {
    changes.push([KEY_ENABLED, String(freight.enabled)])
  }
  if (freight.freeThreshold !== saved.freeThreshold) {
    changes.push([KEY_THRESHOLD, Number(freight.freeThreshold).toFixed(2)])
  }
  if (freight.baseFee !== saved.baseFee) {
    changes.push([KEY_FEE, Number(freight.baseFee).toFixed(2)])
  }
  if (!changes.length) {
    ElMessage.info('运费规则没有改动')
    return
  }
  savingFreight.value = true
  let done = 0
  try {
    for (const [key, value] of changes) {
      await updateConfig(key, value)
      done += 1
    }
    ElMessage.success('已保存 ' + done + ' 项，前台立即生效')
  } finally {
    savingFreight.value = false
    // 无论成败都以服务端为准重新读一次，避免界面显示的与库里不一致
    loadFreight()
  }
}

/** 连接测试用免登录的分类接口：能返回数据即同时证明「后端活着」与「数据库连通」 */
async function handleTest() {
  if (!baseUrl.value) {
    ElMessage.warning('请先填写后端地址')
    return
  }
  testing.value = true
  setRequestBaseUrl(baseUrl.value)
  const ok = await request.get('/api/category/list').then(() => true).catch(() => false)
  ElMessage[ok ? 'success' : 'error'](ok ? '连接成功，别忘了点「保存并使用」' : '连接失败，请检查地址与后端状态')
  testing.value = false
}

function handleSave() {
  setBaseUrl(baseUrl.value.trim())
  ElMessage.success('已保存，正在重新加载')
  setTimeout(() => window.location.reload(), 500)
}

onMounted(loadFreight)
</script>

<style scoped>
.setting-card {
  max-width: 720px;
}

.setting-card + .setting-card {
  margin-top: 16px;
}

.setting-tip {
  margin: 8px 0 12px;
  line-height: 1.7;
}

.setting-actions {
  margin: 16px 0;
  display: flex;
  gap: 10px;
}

.freight-form {
  max-width: 560px;
}

.unit {
  margin-left: 8px;
}

.freight-tip {
  color: #409eff;
  font-weight: 600;
}
</style>
