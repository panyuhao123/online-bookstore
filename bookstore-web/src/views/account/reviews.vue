<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyReviews } from '@/api/review'
import { formatDateTime } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'

const router = useRouter()
const reviews = ref([])
const total = ref(0)
const loading = ref(false)
const activeTab = ref('all') // all | published
const pageNum = ref(1)
const pageSize = ref(10)

async function loadReviews() {
  loading.value = true
  try {
    const res = await pageMyReviews({ pageNum: pageNum.value, pageSize: pageSize.value })
    reviews.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(loadReviews)
</script>

<template>
  <div class="page-container reviews-page">
    <h2 class="page-title">我的评价</h2>

    <div class="card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="已发表评价" name="all" />
      </el-tabs>

      <el-table :data="reviews" v-loading="loading" style="width: 100%">
        <el-table-column label="图书" min-width="280">
          <template #default="{ row }">
            <div class="book-cell" @click="router.push('/books/' + row.bookId)">
              <img :src="row.bookCoverUrl || placeholderCover(row.bookTitle)" class="table-cover" />
              <div class="book-info">
                <p class="book-title text-ellipsis-2">{{ row.bookTitle }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="180">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="300">
          <template #default="{ row }">
            <p class="review-content">{{ row.content || '此用户未填写评价内容' }}</p>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="160" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="loadReviews"
        />
      </div>

      <el-empty v-if="reviews.length === 0 && !loading" description="暂无评价记录" />
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 16px;
}

.book-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
}

.table-cover {
  width: 50px;
  height: 66px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.book-info {
  flex: 1;
}

.book-title {
  font-size: 14px;
  line-height: 1.4;
}

.review-content {
  line-height: 1.6;
  color: var(--text-regular);
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}
</style>
