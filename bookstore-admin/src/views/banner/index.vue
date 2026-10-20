<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">轮播图管理</h2>
      <div class="header-right">
        <span class="app-muted">已配置 {{ banners.length }} / {{ MAX_BANNERS }} 张</span>
        <el-button type="primary" :icon="Plus" :disabled="!canCreate" @click="openCreate">
          新增轮播图
        </el-button>
      </div>
    </div>

    <div class="app-card">
      <p class="app-muted tip">
        轮播图直接面向所有访客。排序值越小越靠前；「下架」只是不再展示，不会删除数据。
        未上传图片时用「背景样式」渲染，因此图片与背景样式至少要填一个。
      </p>

      <el-table v-loading="loading" :data="banners" border>
        <el-table-column label="预览" width="150">
          <template #default="{ row }">
            <div class="banner-thumb" :style="previewStyle(row)">
              <span v-if="row.title" class="thumb-text">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="title-cell">{{ row.title }}</div>
            <div v-if="row.subTitle" class="app-muted sub-title">{{ row.subTitle }}</div>
          </template>
        </el-table-column>
        <el-table-column label="跳转地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.linkUrl">{{ row.linkUrl }}</span>
            <span v-else class="app-muted">不跳转</span>
          </template>
        </el-table-column>
        <el-table-column label="背景样式" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.imageUrl" class="app-muted">使用上传图片</span>
            <span v-else class="mono">{{ row.bgColor || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              inline-prompt
              active-text="上架"
              inactive-text="下架"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有配置轮播图，首页将展示兜底文案" />
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑轮播图' : '新增轮播图'" width="680px"
               @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="如：新书上架" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subTitle" maxlength="100" show-word-limit
                    placeholder="如：海量新书 抢先阅读" />
        </el-form-item>
        <el-form-item label="背景图">
          <div class="upload-row">
            <el-upload
              :show-file-list="false"
              accept="image/jpeg,image/png,image/webp"
              :before-upload="beforeUpload"
              :http-request="doUpload"
            >
              <div class="upload-box">
                <img v-if="form.imageUrl" :src="fileUrl(form.imageUrl)" class="upload-preview" alt="" />
                <el-icon v-else class="upload-placeholder"><Picture /></el-icon>
              </div>
            </el-upload>
            <div class="upload-actions">
              <span class="app-muted">支持 jpg / png / webp，不超过 5MB；建议 1200×300。</span>
              <el-button v-if="form.imageUrl" link type="danger" @click="form.imageUrl = ''">
                移除图片，改用背景样式
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="背景样式" prop="bgColor">
          <div class="bg-field">
            <el-input v-model="form.bgColor" maxlength="120"
                      placeholder="颜色值（#4facfe）或渐变 linear-gradient(135deg,#4facfe,#00f2fe)" />
            <div class="preset-row">
              <span
                v-for="preset in BG_PRESETS"
                :key="preset"
                class="preset"
                :style="{ background: preset }"
                :title="preset"
                @click="form.bgColor = preset"
              />
            </div>
          </div>
        </el-form-item>
        <div class="preview-row">
          <span class="app-muted">效果预览</span>
          <div class="banner-thumb large" :style="previewStyle(form)">
            <span class="thumb-text">{{ form.title || '标题' }}</span>
            <span v-if="form.subTitle" class="thumb-sub">{{ form.subTitle }}</span>
          </div>
        </div>
        <el-form-item label="跳转地址" prop="linkUrl">
          <el-input v-model="form.linkUrl" maxlength="200"
                    placeholder="站内路径 /books?sort=sales，或 https:// 外链；留空表示不跳转" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序值">
              <el-input-number v-model="form.sort" :min="0" :max="999" :precision="0"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
                         inline-prompt active-text="上架" inactive-text="下架" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { formatTime } from '@/utils/format'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Picture } from '@element-plus/icons-vue'
import { listBanners, createBanner, updateBanner, updateBannerStatus, deleteBanner } from '@/api/banner'
import { uploadCover } from '@/api/book'
import { fileUrl } from '@/utils/url'

/** 与后端 AdminBannerServiceImpl.MAX_BANNERS 保持一致，仅用于前置提示与按钮禁用 */
const MAX_BANNERS = 8
const MAX_SIZE_MB = 5

/** 与 V6 迁移里的种子配色同一套，方便运营直接沿用 */
const BG_PRESETS = [
  'linear-gradient(135deg,#667eea,#764ba2)',
  'linear-gradient(135deg,#f093fb,#f5576c)',
  'linear-gradient(135deg,#4facfe,#00f2fe)',
  'linear-gradient(135deg,#43e97b,#38f9d7)',
  '#e8f3ff',
  '#fff4e6'
]

const banners = ref([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const canCreate = computed(() => banners.value.length < MAX_BANNERS)

const emptyForm = {
  id: null,
  title: '',
  subTitle: '',
  imageUrl: '',
  bgColor: BG_PRESETS[0],
  linkUrl: '',
  sort: 0,
  status: 1
}
const form = reactive({ ...emptyForm })

/** 站内路径或 http(s) 外链；与后端 BannerRequest.linkUrl 的正则同口径 */
function validateLink(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  if (/^\/[^\s]*$/.test(value) || /^https?:\/\/\S+$/.test(value)) {
    callback()
    return
  }
  callback(new Error('跳转地址必须是站内路径（/开头）或 http(s) 链接'))
}

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  linkUrl: [{ validator: validateLink, trigger: 'blur' }]
}

/** 有图用图、无图用背景样式 —— 与商城端首页 bannerStyle 同一套判断 */
function previewStyle(banner) {
  if (banner.imageUrl) {
    return { backgroundImage: 'url(' + fileUrl(banner.imageUrl) + ')', backgroundSize: 'cover', backgroundPosition: 'center' }
  }
  return { background: banner.bgColor || '#f2f3f5' }
}

async function load() {
  loading.value = true
  try {
    banners.value = await listBanners()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, emptyForm)
  if (formRef.value) formRef.value.clearValidate()
}

function openCreate() {
  Object.assign(form, emptyForm)
  // 新增默认排在现有最后，避免与已有轮播图争抢首位
  form.sort = banners.value.length + 1
  dialogVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    title: row.title || '',
    subTitle: row.subTitle || '',
    imageUrl: row.imageUrl || '',
    bgColor: row.bgColor || '',
    linkUrl: row.linkUrl || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

/** 前端只做格式与大小提示，真正的黑白名单以后端为准 */
function beforeUpload(file) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 jpg / png / webp 格式的图片')
    return false
  }
  if (file.size > MAX_SIZE_MB * 1024 * 1024) {
    ElMessage.error('图片不能超过 ' + MAX_SIZE_MB + 'MB')
    return false
  }
  return true
}

async function doUpload({ file }) {
  try {
    const data = await uploadCover(file)
    form.imageUrl = data.url
    ElMessage.success('背景图上传成功')
  } catch (e) {
    ElMessage.error('背景图上传失败')
  }
}

async function handleStatusChange(row, val) {
  try {
    await updateBannerStatus(row.id, val)
    ElMessage.success(val === 1 ? '已上架' : '已下架')
  } catch (e) {
    // 失败回滚开关，避免界面与实际状态不一致
    row.status = val === 1 ? 0 : 1
  }
}

function handleDelete(row) {
  ElMessageBox.confirm('确定删除轮播图「' + row.title + '」吗？删除后不可恢复。', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await deleteBanner(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!form.imageUrl && !form.bgColor) {
      ElMessage.warning('请上传背景图或填写背景样式（至少一个）')
      return
    }
    submitting.value = true
    try {
      const payload = {
        title: form.title.trim(),
        subTitle: form.subTitle ? form.subTitle.trim() : null,
        imageUrl: form.imageUrl || null,
        bgColor: form.bgColor || null,
        linkUrl: form.linkUrl ? form.linkUrl.trim() : null,
        sort: form.sort ?? 0,
        status: form.status
      }
      if (form.id) {
        await updateBanner(form.id, payload)
        ElMessage.success('修改成功')
      } else {
        await createBanner(payload)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      load()
    } finally {
      submitting.value = false
    }
  })
}

onMounted(load)
</script>

<style scoped>
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tip {
  margin: 0 0 12px;
  line-height: 1.7;
}

.banner-thumb {
  height: 56px;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #fff;
  font-size: 12px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

.banner-thumb.large {
  height: 72px;
  width: 100%;
  max-width: 360px;
  margin-top: 6px;
}

.thumb-text {
  font-weight: 600;
}

.thumb-sub {
  font-size: 11px;
  opacity: 0.9;
}

.title-cell {
  font-weight: 600;
}

.sub-title {
  font-size: 12px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.upload-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.upload-box {
  width: 150px;
  height: 48px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.upload-box:hover {
  border-color: var(--brand);
}

.upload-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-placeholder {
  font-size: 20px;
  color: #8c939d;
}

.upload-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.bg-field {
  width: 100%;
}

.preset-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.preset {
  width: 40px;
  height: 20px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  cursor: pointer;
}

.preview-row {
  display: flex;
  flex-direction: column;
  padding: 0 0 12px 90px;
  font-size: 12px;
}
</style>
