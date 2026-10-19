<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useBooklistStore } from '@/stores/booklist'
import { useUserStore } from '@/stores/user'
import { OFFICIAL_BOOKLISTS } from '@/data/official-booklists'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const booklistStore = useBooklistStore()
const userStore = useUserStore()

const activeTab = ref('official') // official | mine

// 官方书单：定义在 @/data/official-booklists，这里只做展示。
// 卡片不再显示书目数量 —— 官方书单是「规则」而非固定清单，数量要等打开时
// 经接口解析才知道，写死一个数字等于给用户看假数据。
const officialLists = OFFICIAL_BOOKLISTS

const showMineTab = computed(() => userStore.isLogin)

function createNew() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push('/booklists/new')
}

async function deleteList(id) {
  try {
    await ElMessageBox.confirm('确认删除该书单？', '提示', { type: 'warning' })
    booklistStore.deleteBooklist(id)
    ElMessage.success('已删除')
  } catch (e) {
    /* 取消 */
  }
}

function goDetail(id) {
  router.push('/booklists/' + id)
}
</script>

<template>
  <div class="page-container booklist-page">
    <div class="page-header">
      <h2>书单</h2>
      <el-button type="primary" @click="createNew">
        <el-icon><Plus /></el-icon> 新建书单
      </el-button>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="booklist-tabs">
      <el-tab-pane label="官方书单" name="official">
        <div class="booklist-grid">
          <div
            v-for="list in officialLists"
            :key="list.id"
            class="booklist-card card"
            @click="goDetail(list.id)"
          >
            <div class="card-covers">
              <el-icon :size="48" color="var(--brand-weak)"><Collection /></el-icon>
            </div>
            <div class="card-body">
              <h4 class="list-name text-ellipsis">{{ list.name }}</h4>
              <p class="list-desc text-ellipsis-2">{{ list.description }}</p>
              <p class="list-count"><el-tag size="small" type="danger" effect="plain">{{ list.tag }}</el-tag></p>
            </div>
            <div class="card-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
        <el-empty v-if="officialLists.length === 0" description="暂无官方书单" />
      </el-tab-pane>

      <el-tab-pane label="我的书单" name="mine" :disabled="!showMineTab">
        <template v-if="!userStore.isLogin">
          <el-result icon="info" title="请先登录" sub-title="登录后查看和管理你的书单">
            <template #extra>
              <el-button type="primary" @click="router.push('/login')">去登录</el-button>
            </template>
          </el-result>
        </template>
        <template v-else-if="booklistStore.booklists.length > 0">
          <div class="booklist-grid">
            <div
              v-for="list in booklistStore.booklists"
              :key="list.id"
              class="booklist-card card mine"
            >
              <div class="card-covers" @click="goDetail(list.id)">
                <el-icon :size="48" color="var(--brand-weak)"><Collection /></el-icon>
              </div>
              <div class="card-body" @click="goDetail(list.id)">
                <h4 class="list-name text-ellipsis">{{ list.name }}</h4>
                <p class="list-desc text-ellipsis-2">{{ list.description || '暂无描述' }}</p>
                <p class="list-count">{{ list.books.length }} 本书</p>
              </div>
              <div class="card-actions">
                <el-button link type="primary" size="small" @click.stop="router.push('/booklists/' + list.id + '/edit')">
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
                <el-button link type="danger" size="small" @click.stop="deleteList(list.id)">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else description="暂无书单，点击右上角新建">
          <el-button type="primary" @click="createNew">新建书单</el-button>
        </el-empty>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 20px;
  margin: 0;
}

.booklist-tabs {
  margin-top: 8px;
}

.booklist-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.booklist-card {
  padding: 16px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
}

.booklist-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-3px);
}

.booklist-card.mine {
  cursor: default;
}

.card-covers {
  height: 120px;
  background: linear-gradient(135deg, var(--brand-weak) 0%, #fff 100%);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
}

.booklist-card:hover .card-covers {
  transform: scale(1.02);
}

.card-body {
  flex: 1;
}

.list-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--text-primary);
}

.list-desc {
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.5;
  min-height: 38px;
  margin-bottom: 6px;
}

.list-count {
  font-size: 12px;
  color: var(--text-regular);
}

.card-arrow {
  position: absolute;
  top: 16px;
  right: 16px;
  color: var(--text-sub);
  opacity: 0;
  transition: all 0.2s;
}

.booklist-card:hover .card-arrow {
  opacity: 1;
  right: 12px;
  color: var(--brand);
}

.card-actions {
  display: flex;
  gap: 8px;
  border-top: 1px solid var(--border-light);
  padding-top: 8px;
  margin-top: 4px;
  justify-content: flex-end;
}
</style>
