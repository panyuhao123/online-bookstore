<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useBooklistStore } from '@/stores/booklist'
import { useUserStore } from '@/stores/user'
import { OFFICIAL_BOOKLISTS } from '@/data/official-booklists'
import { formatDateTime } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const booklistStore = useBooklistStore()
const userStore = useUserStore()

const activeTab = ref('picked') // picked | square | mine

// 精选书单：定义在 @/data/official-booklists，是「规则」不是固定清单 ——
// 卡片不显示书目数量，因为数量要等打开时经接口解析才知道，写死一个数字等于给用户看假数据。
const officialLists = OFFICIAL_BOOKLISTS

const squarePage = ref(1)
const squarePageSize = ref(12)
const squareKeyword = ref('')
const squareLoading = computed(() => booklistStore.squareLoading)

async function loadSquare() {
  try {
    const params = { pageNum: squarePage.value, pageSize: squarePageSize.value }
    if (squareKeyword.value.trim()) params.keyword = squareKeyword.value.trim()
    await booklistStore.fetchSquare(params)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function loadMine() {
  if (!userStore.isLogin) return
  try {
    await booklistStore.fetchMine()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function searchSquare() {
  squarePage.value = 1
  loadSquare()
}

function createNew() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push('/booklists/new')
}

function goDetail(id) {
  router.push('/booklists/' + id)
}

async function deleteList(list) {
  try {
    await ElMessageBox.confirm(`确认删除书单「${list.name}」？书单里的图书不会受影响。`, '删除书单', {
      type: 'warning'
    })
    await booklistStore.remove(list.id)
    ElMessage.success('已删除')
  } catch (e) {
    /* 用户取消，或接口报错（拦截器已提示） */
  }
}

/** 旧版本存在浏览器里的书单 → 一次性导入后端 */
const importing = ref(false)
async function importLegacy() {
  importing.value = true
  try {
    const res = await booklistStore.importLegacy()
    if (res.failed > 0) {
      ElMessage.warning(`导入 ${res.imported} 个书单、${res.books} 本书；${res.failed} 个失败已留在本地`)
    } else {
      ElMessage.success(`已导入 ${res.imported} 个书单、${res.books} 本书`)
    }
    await loadMine()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    importing.value = false
  }
}

async function discardLegacy() {
  try {
    await ElMessageBox.confirm(
      '这些书单只存在于当前浏览器，清除后无法恢复。确认放弃？',
      '放弃本地书单',
      { type: 'warning' }
    )
    booklistStore.discardLegacy()
    ElMessage.info('已放弃本地书单')
  } catch (e) {
    /* 取消 */
  }
}

function onTabChange(name) {
  if (name === 'square') loadSquare()
  if (name === 'mine') loadMine()
}

watch(squarePage, loadSquare)

onMounted(() => {
  loadSquare()
  loadMine()
})
</script>

<template>
  <div class="page-container booklist-page">
    <div class="page-header">
      <div>
        <h2>书单</h2>
        <p class="page-sub">官方精选、书单广场与我的收藏，换设备也能找到</p>
      </div>
      <el-button type="primary" @click="createNew">
        <el-icon><Plus /></el-icon> 新建书单
      </el-button>
    </div>

    <!-- 旧版本浏览器本地书单：登录后提示一次性导入，不做静默迁移 -->
    <el-alert
      v-if="userStore.isLogin && booklistStore.hasLegacy"
      class="legacy-alert"
      type="warning"
      show-icon
      :closable="false"
    >
      <template #title>
        检测到 {{ booklistStore.legacy.length }} 个旧版书单（共 {{ booklistStore.legacyBookCount }} 本书）
        <span class="legacy-note">它们只存在当前浏览器里，导入后即可跨设备查看</span>
      </template>
      <div class="legacy-actions">
        <el-button type="primary" size="small" :loading="importing" @click="importLegacy">
          导入到我的书单
        </el-button>
        <el-button size="small" @click="discardLegacy">不要了</el-button>
      </div>
    </el-alert>

    <el-tabs v-model="activeTab" class="booklist-tabs" @tab-change="onTabChange">
      <!-- 精选书单（前端规则，匿名可看） -->
      <el-tab-pane label="精选书单" name="picked">
        <div class="booklist-grid">
          <div
            v-for="list in officialLists"
            :key="list.id"
            class="booklist-card card"
            @click="goDetail(list.id)"
          >
            <div class="card-covers official">
              <el-icon :size="48" color="var(--brand)"><Collection /></el-icon>
              <span class="official-badge">官方</span>
            </div>
            <div class="card-body">
              <h4 class="list-name text-ellipsis">{{ list.name }}</h4>
              <p class="list-desc text-ellipsis-2">{{ list.description }}</p>
              <p class="list-count">
                <el-tag size="small" type="danger" effect="plain">{{ list.tag }}</el-tag>
                <span class="official-tip">由编辑挑选</span>
              </p>
            </div>
            <div class="card-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 书单广场（后端公开书单） -->
      <el-tab-pane label="书单广场" name="square">
        <div class="square-toolbar">
          <el-input
            v-model="squareKeyword"
            placeholder="搜索书单名称或简介"
            clearable
            style="width: 280px"
            @keyup.enter="searchSquare"
            @clear="searchSquare"
          >
            <template #append>
              <el-button @click="searchSquare">
                <el-icon><Search /></el-icon>
              </el-button>
            </template>
          </el-input>
          <span class="square-total">共 {{ booklistStore.squareTotal }} 个公开书单</span>
        </div>

        <div v-loading="squareLoading">
          <div v-if="booklistStore.square.length > 0" class="booklist-grid">
            <div
              v-for="list in booklistStore.square"
              :key="list.id"
              class="booklist-card card"
              @click="goDetail(list.id)"
            >
              <div class="card-covers">
                <img v-if="list.coverUrl" :src="list.coverUrl" class="cover-img" :alt="list.name" />
                <el-icon v-else :size="48" color="var(--brand-weak)"><Collection /></el-icon>
                <el-tag v-if="list.isPublic === 0" class="cover-tag" size="small" type="info">
                  仅自己可见
                </el-tag>
              </div>
              <div class="card-body">
                <h4 class="list-name text-ellipsis">{{ list.name }}</h4>
                <p class="list-desc text-ellipsis-2">{{ list.description || '暂无描述' }}</p>
                <p class="list-count">
                  {{ list.bookCount || 0 }} 本书
                  <span class="owner">· {{ list.mine ? '我' : list.ownerNickname || '书友' }}的书单</span>
                </p>
              </div>
              <div class="card-arrow">
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
          <el-empty v-else-if="!squareLoading" description="还没有公开书单">
            <el-button type="primary" @click="createNew">创建第一个</el-button>
          </el-empty>
        </div>

        <div v-if="booklistStore.squareTotal > squarePageSize" class="pager">
          <el-pagination
            v-model:current-page="squarePage"
            :page-size="squarePageSize"
            :total="booklistStore.squareTotal"
            layout="prev, pager, next, total"
            background
          />
        </div>
      </el-tab-pane>

      <!-- 我的书单 -->
      <el-tab-pane label="我的书单" name="mine" :disabled="!userStore.isLogin">
        <template v-if="!userStore.isLogin">
          <el-result icon="info" title="请先登录" sub-title="登录后查看和管理你的书单">
            <template #extra>
              <el-button type="primary" @click="router.push('/login')">去登录</el-button>
            </template>
          </el-result>
        </template>

        <div v-else v-loading="booklistStore.mineLoading">
          <div v-if="booklistStore.mine.length > 0" class="booklist-grid">
            <div
              v-for="list in booklistStore.mine"
              :key="list.id"
              :class="['booklist-card', 'card', 'mine', { hidden: list.status === 0 }]"
            >
              <div class="card-covers" @click="goDetail(list.id)">
                <img v-if="list.coverUrl" :src="list.coverUrl" class="cover-img" :alt="list.name" />
                <el-icon v-else :size="48" color="var(--brand-weak)"><Collection /></el-icon>
                <!-- 被平台隐藏时仍留在列表里：凭空消失会让用户以为数据丢了 -->
                <el-tag v-if="list.status === 0" class="cover-tag" size="small" type="danger" effect="dark">
                  已被平台隐藏
                </el-tag>
                <el-tag v-else-if="list.isPublic === 0" class="cover-tag" size="small" type="info">
                  仅自己可见
                </el-tag>
              </div>
              <div class="card-body" @click="goDetail(list.id)">
                <h4 class="list-name text-ellipsis">{{ list.name }}</h4>
                <p class="list-desc text-ellipsis-2">{{ list.description || '暂无描述' }}</p>
                <p class="list-count">
                  {{ list.bookCount || 0 }} 本书
                  <span class="owner">· {{ formatDateTime(list.updateTime || list.createTime) }}</span>
                </p>
              </div>
              <div class="card-actions">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click.stop="router.push('/booklists/' + list.id + '/edit')"
                >
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
                <el-button link type="danger" size="small" @click.stop="deleteList(list)">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else-if="!booklistStore.mineLoading" description="暂无书单，点击右上角新建">
            <el-button type="primary" @click="createNew">新建书单</el-button>
          </el-empty>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 12px;
}

.page-header h2 {
  font-size: 20px;
  margin: 0 0 4px;
}

.page-sub {
  font-size: 13px;
  color: var(--text-sub);
}

.legacy-alert {
  margin-bottom: 12px;
}

.legacy-note {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-sub);
}

.legacy-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.booklist-tabs {
  margin-top: 8px;
}

.square-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 4px;
}

.square-total {
  font-size: 13px;
  color: var(--text-sub);
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

.booklist-card.hidden .card-covers {
  filter: grayscale(1);
  opacity: 0.7;
}

.card-covers {
  position: relative;
  height: 120px;
  background: linear-gradient(135deg, var(--brand-weak) 0%, #fff 100%);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: transform 0.2s;
}

.card-covers.official {
  background: linear-gradient(135deg, #fff3e9 0%, #fde2d3 100%);
}

.booklist-card:hover .card-covers {
  transform: scale(1.02);
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-tag {
  position: absolute;
  top: 8px;
  left: 8px;
}

.official-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  font-size: 11px;
  color: #fff;
  background: var(--brand);
  padding: 2px 8px;
  border-radius: 10px;
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

.owner {
  color: var(--text-sub);
}

.official-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-sub);
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
