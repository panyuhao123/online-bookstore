<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAddressList, addAddress, updateAddress, removeAddress, setDefaultAddress, geocodeAddress } from '@/api/address'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AddLocation, Search } from '@element-plus/icons-vue'

const addresses = ref([])
const drawerVisible = ref(false)
const editingId = ref(null)

const geocoding = ref(false)
const candidateDialog = ref(false)
const candidates = ref([])

const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  provinceAdcode: '',
  cityAdcode: '',
  districtAdcode: '',
  detail: '',
  isDefault: 0
})

const rules = {
  receiver: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  province: [{ required: true, message: '请输入省份', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  district: [{ required: true, message: '请输入区/县', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

async function loadAddresses() {
  try {
    addresses.value = await getAddressList()
  } catch (e) {
    /* 静默 */
  }
}

function openAdd() {
  editingId.value = null
  Object.assign(form, {
    receiver: '', phone: '',
    province: '', city: '', district: '',
    provinceAdcode: '', cityAdcode: '', districtAdcode: '',
    detail: '', isDefault: 0
  })
  drawerVisible.value = true
}

function openEdit(addr) {
  editingId.value = addr.id
  Object.assign(form, { ...addr })
  drawerVisible.value = true
}

/**
 * 地址自动解析 —— 核心方法。
 *
 * <p>流程：
 * <ol>
 *     <li>校验详细地址文本非空；</li>
 *     <li>调后端 /api/address/geocode（带 loading + try-catch）；</li>
 *     <li>后端返回 uniqueMatch=true → 直接回填省市区 + adcode；</li>
 *     <li>返回多条候选 → 弹对话框让用户点选；</li>
 *     <li>任何异常（网络/限流/高德错误）→ 弹错误提示，保留用户已输入的 detail。</li>
 * </ol>
 */
async function onGeocode() {
  if (!form.detail || form.detail.trim().length < 3) {
    ElMessage.warning('请先输入详细地址，再点自动解析')
    return
  }
  geocoding.value = true
  try {
    const result = await geocodeAddress(form.detail.trim())
    if (result.uniqueMatch || result.count === 1) {
      // 单条匹配 → 直接回填
      applyCandidate(result.candidates[0])
      ElMessage.success('已自动识别并回填省市区')
    } else {
      // 多条候选 → 弹窗让用户选
      candidates.value = result.candidates
      candidateDialog.value = true
    }
  } catch (e) {
    // 后端已返回 error message（如 "操作太频繁" / "高德错误[INVALID_KEY]"），
    // Axios 拦截器已经 ElMessage.error 弹过了 —— 这里静默即可
  } finally {
    geocoding.value = false
  }
}

/** 把候选地址回填到表单 */
function applyCandidate(c) {
  form.province = c.province || ''
  form.city = c.city || ''
  form.district = c.district || ''
  form.provinceAdcode = c.provinceAdcode || ''
  form.cityAdcode = c.cityAdcode || ''
  form.districtAdcode = c.districtAdcode || ''
}

/** 候选列表点击选择 */
function onSelectCandidate(c) {
  applyCandidate(c)
  candidateDialog.value = false
  ElMessage.success('已回填，请确认后保存')
}

/** 候选列表取消 —— 不清除任何已填内容 */
function onCancelCandidate() {
  candidateDialog.value = false
  // 用户可以手动编辑 province/city/district 三个 input
}

async function onSubmit(formEl) {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    try {
      // 提交时带上 adcode 字段（如果用户没解析，就是空字符串）
      const payload = {
        receiver: form.receiver,
        phone: form.phone,
        province: form.province,
        city: form.city,
        district: form.district,
        detail: form.detail,
        isDefault: form.isDefault
      }
      // adcode 可选 —— 有值才传（后端 Entity 字段允许 null）
      if (form.provinceAdcode) payload.provinceAdcode = form.provinceAdcode
      if (form.cityAdcode) payload.cityAdcode = form.cityAdcode
      if (form.districtAdcode) payload.districtAdcode = form.districtAdcode

      if (editingId.value) {
        await updateAddress(editingId.value, payload)
        ElMessage.success('地址已更新')
      } else {
        await addAddress(payload)
        ElMessage.success('地址已添加')
      }
      drawerVisible.value = false
      loadAddresses()
    } catch (e) {
      /* 错误已提示 */
    }
  })
}

async function onRemove(addr) {
  try {
    await ElMessageBox.confirm(`确认删除「${addr.receiver}」的地址？`, '提示', { type: 'warning' })
    await removeAddress(addr.id)
    ElMessage.success('已删除')
    loadAddresses()
  } catch (e) { /* 取消 */ }
}

async function onSetDefault(addr) {
  await setDefaultAddress(addr.id)
  ElMessage.success('已设为默认地址')
  loadAddresses()
}

onMounted(loadAddresses)
</script>

<template>
  <div class="page-container address-page">
    <div class="page-header">
      <h2>收货地址</h2>
      <el-button type="primary" @click="openAdd">
        <el-icon><AddLocation /></el-icon> 新增地址
      </el-button>
    </div>

    <div v-if="addresses.length > 0" class="address-grid">
      <div v-for="addr in addresses" :key="addr.id" class="address-card card">
        <div class="card-header">
          <span class="receiver">{{ addr.receiver }}</span>
          <span class="phone">{{ addr.phone }}</span>
          <el-tag v-if="addr.isDefault === 1" size="small" type="danger">默认</el-tag>
        </div>
        <p class="full-address">{{ addr.fullAddress }}</p>
        <div class="card-actions">
          <el-button v-if="addr.isDefault !== 1" link type="primary" @click="onSetDefault(addr)">设为默认</el-button>
          <el-button link type="primary" @click="openEdit(addr)">编辑</el-button>
          <el-button link type="danger" @click="onRemove(addr)">删除</el-button>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无收货地址" />

    <!-- 地址编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="editingId ? '编辑地址' : '新增地址'" size="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="form.receiver" placeholder="请输入收货人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>

        <!-- 详细地址 + 自动解析按钮（核心改动） -->
        <el-form-item label="详细地址" prop="detail">
          <div class="detail-row">
            <el-input
              v-model="form.detail"
              type="textarea"
              :rows="2"
              placeholder="请输入详细地址，如「北京市朝阳区望京街道SOHO T1」"
            />
            <el-button
              class="geocode-btn"
              type="primary"
              size="small"
              :icon="Search"
              :loading="geocoding"
              @click="onGeocode"
            >
              {{ geocoding ? '解析中...' : '自动解析' }}
            </el-button>
          </div>
          <div class="form-tip">💡 输入完整地址后点「自动解析」，将自动识别省市区</div>
        </el-form-item>

        <el-form-item label="省份" prop="province">
          <el-input v-model="form.province" placeholder="可手动输入，或先自动解析" />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" placeholder="可手动输入，或先自动解析" />
        </el-form-item>
        <el-form-item label="区/县" prop="district">
          <el-input v-model="form.district" placeholder="可手动输入，或先自动解析" />
        </el-form-item>

        <el-form-item label="默认地址">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit(formRef)">保存</el-button>
      </template>
    </el-drawer>

    <!-- 候选地址列表对话框（多条匹配时弹） -->
    <el-dialog
      v-model="candidateDialog"
      title="识别到多条候选地址，请选择"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="candidate-list">
        <div
          v-for="(c, idx) in candidates"
          :key="idx"
          class="candidate-item"
          @click="onSelectCandidate(c)"
        >
          <div class="candidate-rank">{{ idx + 1 }}</div>
          <div class="candidate-info">
            <div class="candidate-full">{{ c.formattedAddress }}</div>
            <div class="candidate-region">{{ c.province }} · {{ c.city }} · {{ c.district }}</div>
          </div>
          <el-button type="primary" size="small" link>使用</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="onCancelCandidate">取消，手动填写</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 20px; }

.address-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.address-card {
  padding: 20px;
  border: 2px solid transparent;
  transition: border-color 0.15s;
}
.address-card:hover { border-color: var(--brand); }

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.receiver { font-weight: 600; font-size: 16px; }
.phone { color: var(--text-regular); }

.full-address {
  color: var(--text-regular);
  line-height: 1.6;
  min-height: 48px;
  margin-bottom: 12px;
}

.card-actions {
  display: flex;
  gap: 12px;
  border-top: 1px solid var(--border-light);
  padding-top: 12px;
}

/* ====== 自动解析相关样式 ====== */
.detail-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: flex-start;
}
.detail-row :deep(.el-textarea) { flex: 1; }
.geocode-btn {
  flex-shrink: 0;
  height: 40px;
  margin-top: 2px;
}
.form-tip {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}

/* 候选列表对话框 */
.candidate-list {
  max-height: 360px;
  overflow-y: auto;
}
.candidate-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.candidate-item:hover {
  border-color: var(--brand);
  background: var(--brand-weak, #fff5f0);
}
.candidate-rank {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.candidate-info { flex: 1; min-width: 0; }
.candidate-full {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 4px;
  line-height: 1.4;
}
.candidate-region {
  font-size: 12px;
  color: var(--text-sub);
}
</style>
