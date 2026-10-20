<template>
  <div>
    <div class="app-header">
      <h2 class="app-title">分类管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增分类</el-button>
    </div>

    <!-- ==================== 汇总条：分类只有十几条，数字直接在前端算 ==================== -->
    <div class="app-card statbar">
      <div class="statbar__cell">
        <span class="statbar__label">分类总数</span>
        <b class="statbar__value num">{{ list.length }}</b>
        <span class="statbar__foot">全部一级分类</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">书目合计</span>
        <b class="statbar__value num">{{ bookTotal }}</b>
        <span class="statbar__foot">含已下架</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">空分类</span>
        <b class="statbar__value num" :class="{ 'is-alert': emptyCount > 0 }">{{ emptyCount }}</b>
        <span class="statbar__foot">可直接删除</span>
      </div>
      <div class="statbar__cell">
        <span class="statbar__label">书目最多</span>
        <b class="statbar__value statbar__value--text">{{ topCategory.name }}</b>
        <span class="statbar__foot">共 {{ topCategory.count }} 本</span>
      </div>
    </div>

    <!-- ==================== 筛选卡：分类是窄表，用胶囊比下拉更好点 ==================== -->
    <div class="app-card filter">
      <div class="filter__row">
        <el-input
          v-model="keyword"
          placeholder="分类名称"
          clearable
          style="width: 200px"
          :prefix-icon="Search"
        />
        <span class="filter__label">按书目数</span>
        <div class="chips">
          <button
            type="button"
            class="chip"
            :class="{ on: filterKey === 'all' }"
            style="--dc: #6c4df2"
            @click="filterKey = 'all'"
          >
            <i></i>全部<b class="num">{{ list.length }}</b>
          </button>
          <button
            type="button"
            class="chip"
            :class="{ on: filterKey === 'has' }"
            style="--dc: #1f9d63"
            @click="filterKey = 'has'"
          >
            <i></i>有图书<b class="num">{{ list.length - emptyCount }}</b>
          </button>
          <button
            type="button"
            class="chip"
            :class="{ on: filterKey === 'empty' }"
            style="--dc: #b4bac6"
            @click="filterKey = 'empty'"
          >
            <i></i>空分类<b class="num">{{ emptyCount }}</b>
          </button>
        </div>
        <span class="filter__echo">当前显示 {{ filteredList.length }} 条</span>
      </div>
    </div>

    <!-- ==================== 列表 ==================== -->
    <div class="app-card">
      <el-empty v-if="!list.length && !loading" description="暂无分类，点击右上角新增" />
      <el-table v-else v-loading="loading" :data="filteredList" :border="false" class="list-table">
        <el-table-column prop="id" label="ID" width="80">
          <template #default="{ row }"><span class="num cell-sub">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="分类名称" min-width="160">
          <template #default="{ row }"><span class="cell-main">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="sort" label="排序值" width="100">
          <template #default="{ row }"><span class="num cell-sub-strong">{{ row.sort }}</span></template>
        </el-table-column>
        <el-table-column label="图书数" width="120">
          <template #default="{ row }">
            <span v-if="row.bookCount > 0" class="num cell-sub-strong">{{ row.bookCount }}</span>
            <span v-else class="pill pill--gray">空</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }"><span class="cell-sub">{{ formatTime(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的分类" :image-size="72" />
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新增分类'" width="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：计算机" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="排序值" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
          <span class="app-muted sort-tip">数值越大越靠前</span>
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
import { formatTime } from '@/utils/format'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { listCategories, createCategory, updateCategory, deleteCategory } from '@/api/category'

const list = ref([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
/** 关键词与分类状态都在前端过滤：分类是全量返回（十几条），没必要为它再发请求 */
const keyword = ref('')
const filterKey = ref('all')

const form = reactive({ id: null, name: '', sort: 100 })

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

/** 书目合计：bookCount 由后端给出（管理端口径含已下架，用于删除保护） */
const bookTotal = computed(() =>
  list.value.reduce((acc, item) => acc + Number(item.bookCount || 0), 0)
)

const emptyCount = computed(() => list.value.filter((item) => !Number(item.bookCount)).length)

/**
 * 书目最多的分类。
 *
 * <p>用 reduce 而不是 sort 后取第一个：sort 会就地改变原数组顺序，
 * 而表格的展示顺序依赖后端返回的 sort 值，排序一次就会把表格顺序也改掉。
 */
const topCategory = computed(() => {
  if (!list.value.length) return { name: '—', count: 0 }
  const top = list.value.reduce((best, item) =>
    Number(item.bookCount || 0) > Number(best.bookCount || 0) ? item : best
  )
  return { name: top.name, count: Number(top.bookCount || 0) }
})

const filteredList = computed(() => {
  const kw = keyword.value.trim()
  return list.value.filter((item) => {
    if (filterKey.value === 'has' && !Number(item.bookCount)) return false
    if (filterKey.value === 'empty' && Number(item.bookCount)) return false
    return !kw || String(item.name).includes(kw)
  })
})

async function load() {
  loading.value = true
  try {
    list.value = (await listCategories()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = null
  form.name = ''
  form.sort = 100
  dialogVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  form.sort = row.sort
  dialogVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.id) {
        await updateCategory(form.id, { name: form.name, sort: form.sort })
        ElMessage.success('修改成功')
      } else {
        await createCategory({ name: form.name, sort: form.sort })
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      load()
    } finally {
      submitting.value = false
    }
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(
    '删除后该分类下的图书需改挂其他分类，确定删除「' + row.name + '」吗？',
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
    .then(async () => {
      await deleteCategory(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.sort-tip {
  margin-left: 10px;
}

/* 分类名是文字不是数字，用 22px 的等宽数字字号会撑破格子 */
.statbar__value--text {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
