<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">图书管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增图书</el-button>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="书名 / 作者"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 160px"
                   @change="handleSearch">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px"
                   @change="handleSearch">
          <el-option label="上架中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image v-if="row.coverUrl" :src="fileUrl(row.coverUrl)" :preview-src-list="[fileUrl(row.coverUrl)]"
                      class="cover" fit="cover" preview-teleported />
            <span v-else class="app-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="author" label="作者" min-width="120" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90">
          <template #default="{ row }">
            <span :class="{ 'stock-warn': row.stock <= 5 }">{{ row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="90" />
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
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的图书" />
        </template>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="handleSearch"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑图书' : '新增图书'" width="640px"
               @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="封面">
          <el-upload
            class="cover-uploader"
            :show-file-list="false"
            accept="image/jpeg,image/png,image/webp"
            :before-upload="beforeUpload"
            :http-request="doUpload"
          >
            <img v-if="form.coverUrl" :src="fileUrl(form.coverUrl)" class="cover-preview" />
            <el-icon v-else class="cover-placeholder"><Plus /></el-icon>
          </el-upload>
          <span class="app-muted upload-tip">支持 jpg / png / webp，不超过 5MB</span>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="书名" prop="title">
              <el-input v-model="form.title" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者" prop="author">
              <el-input v-model="form.author" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择" style="width: 100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版社">
              <el-input v-model="form.publisher" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="价格" prop="price">
              <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" :precision="0" :step="1"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000"
                    show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import {
  pageBooks, createBook, updateBook, updateBookStatus, deleteBook, uploadCover
} from '@/api/book'
import { listCategories } from '@/api/category'
import { fileUrl } from '@/utils/url'

const MAX_SIZE_MB = 5

const list = ref([])
const categories = ref([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const total = ref(0)

const query = reactive({
  keyword: '',
  categoryId: null,
  status: null,
  pageNum: 1,
  pageSize: 10
})

const emptyForm = {
  id: null,
  title: '',
  author: '',
  publisher: '',
  categoryId: null,
  price: 39.9,
  stock: 100,
  coverUrl: '',
  description: ''
}
const form = reactive({ ...emptyForm })

const rules = {
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }],
  author: [{ required: true, message: '请输入作者', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格' }, { type: 'number', min: 0.01, message: '价格必须大于 0' }],
  stock: [{ required: true, message: '请输入库存' }, { type: 'number', min: 0, message: '库存不能为负数' }]
}

async function load() {
  loading.value = true
  try {
    const data = await pageBooks(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categories.value = await listCategories().catch(() => [])
}

function handleSearch() {
  query.pageNum = 1
  load()
}

function handleReset() {
  query.keyword = ''
  query.categoryId = null
  query.status = null
  handleSearch()
}

function resetForm() {
  Object.assign(form, emptyForm)
  if (formRef.value) formRef.value.clearValidate()
}

function openCreate() {
  Object.assign(form, emptyForm)
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    author: row.author || '',
    publisher: row.publisher || '',
    categoryId: row.categoryId,
    price: Number(row.price),
    stock: row.stock,
    coverUrl: row.coverUrl || '',
    description: row.description || ''
  })
  dialogVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

/** 上传前的前端校验：只过一遍格式与大小，真正的黑白名单仍以后端为准 */
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
    form.coverUrl = data.url
    ElMessage.success('封面上传成功')
  } catch (e) {
    ElMessage.error('封面上传失败')
  }
}

async function handleStatusChange(row, val) {
  try {
    await updateBookStatus(row.id, val)
    ElMessage.success(val === 1 ? '已上架' : '已下架')
    load()
  } catch (e) {
    // 失败时把开关回滚，避免界面与实际状态不一致
    row.status = val === 1 ? 0 : 1
  }
}

function handleDelete(row) {
  ElMessageBox.confirm('确定删除《' + row.title + '》吗？删除后不可恢复。', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await deleteBook(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        title: form.title.trim(),
        author: form.author.trim(),
        publisher: form.publisher ? form.publisher.trim() : null,
        categoryId: form.categoryId,
        price: form.price,
        stock: form.stock,
        coverUrl: form.coverUrl || null,
        description: form.description || null
      }
      if (form.id) {
        await updateBook(form.id, payload)
        ElMessage.success('修改成功')
      } else {
        await createBook(payload)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      load()
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  await loadCategories()
  load()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.cover {
  width: 44px;
  height: 60px;
  border-radius: 4px;
}

.stock-warn {
  color: #e6a23c;
  font-weight: 600;
}

.cover-uploader :deep(.el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;
}

.cover-uploader :deep(.el-upload:hover) {
  border-color: #409eff;
}

.cover-preview {
  width: 90px;
  height: 120px;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  width: 90px;
  height: 120px;
  font-size: 28px;
  color: #8c939d;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-tip {
  margin-left: 12px;
}
</style>
