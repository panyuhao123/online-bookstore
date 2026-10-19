<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">系统设置</h2>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBaseUrl, setBaseUrl, DEFAULT_BASE_URL, getAdmin } from '@/utils/auth'
import request, { setRequestBaseUrl } from '@/api/request'

const baseUrl = ref(getBaseUrl())
const testing = ref(false)
const admin = getAdmin()

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
</script>

<style scoped>
.setting-card {
  max-width: 720px;
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
</style>
