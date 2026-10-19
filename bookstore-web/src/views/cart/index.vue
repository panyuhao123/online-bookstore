<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useBooklistStore } from '@/stores/booklist'
import { formatPrice } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const cartStore = useCartStore()
const booklistStore = useBooklistStore()

const selectedRows = ref([])
const allChecked = computed(() =>
  cartStore.list.length > 0 && cartStore.list.every((i) => i.checked === 1)
)
const isIndeterminate = computed(() =>
  cartStore.list.some((i) => i.checked === 1) && !allChecked.value
)

async function loadCart() {
  try {
    await cartStore.fetchCart()
  } catch (e) {
    /* 静默 */
  }
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function toggleAll(val) {
  const checked = val ? 1 : 0
  for (const item of cartStore.list) {
    if (item.checked !== checked) {
      await cartStore.toggleChecked(item.id, val)
    }
  }
}

async function toggleItem(item, val) {
  await cartStore.toggleChecked(item.id, val)
}

async function changeQuantity(item, val) {
  if (val < 1) return
  if (val > item.stock) {
    ElMessage.warning('超出库存数量')
    return
  }
  await cartStore.updateQuantity(item.id, val)
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm(`确认删除「${item.title}」？`, '提示', { type: 'warning' })
    await cartStore.remove(item.id)
    ElMessage.success('已删除')
  } catch (e) {
    /* 取消 */
  }
}

async function batchRemove() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的商品')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedRows.value.length} 件商品？`, '提示', { type: 'warning' })
    const ids = selectedRows.value.map((i) => i.id)
    await cartStore.removeBatch(ids)
    ElMessage.success('批量删除成功')
  } catch (e) {
    /* 取消 */
  }
}

async function batchAddToBooklist() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择商品')
    return
  }
  const lists = booklistStore.booklists
  if (lists.length === 0) {
    ElMessage.info('暂无书单，请先创建书单')
    router.push('/booklists/new')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('选择要加入的书单', '加入书单', {
      inputType: 'select',
      inputOptions: lists.map((l) => ({ value: l.id, label: l.name })),
      confirmButtonText: '加入',
      cancelButtonText: '取消'
    })
    const books = selectedRows.value.map((i) => ({
      id: i.bookId,
      title: i.title,
      coverUrl: i.coverUrl,
      price: i.price,
      author: i.author
    }))
    booklistStore.addBooksToList(Number(value), books)
    ElMessage.success('已加入书单')
  } catch (e) {
    /* 取消 */
  }
}

function goCheckout() {
  if (cartStore.checkedCount === 0) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  router.push('/orders/confirm')
}

onMounted(loadCart)
</script>

<template>
  <div class="page-container cart-page">
    <div class="cart-header">
      <h2>我的购物车</h2>
      <span class="cart-count">共 {{ cartStore.list.length }} 件商品</span>
    </div>

    <div v-if="cartStore.list.length > 0" class="cart-content card">
      <!-- 操作栏 -->
      <div class="cart-toolbar">
        <el-checkbox :model-value="allChecked" :indeterminate="isIndeterminate" @change="toggleAll">全选</el-checkbox>
        <div class="toolbar-actions">
          <el-button size="small" @click="batchRemove" :disabled="selectedRows.length === 0">批量删除</el-button>
          <el-button size="small" type="primary" @click="batchAddToBooklist" :disabled="selectedRows.length === 0">加入书单</el-button>
        </div>
      </div>

      <!-- 购物车表格 -->
      <el-table
        :data="cartStore.list"
        row-key="id"
        @selection-change="handleSelectionChange"
        style="width: 100%"
      >
        <el-table-column width="50">
          <template #header>
            <el-checkbox :model-value="allChecked" :indeterminate="isIndeterminate" @change="toggleAll" />
          </template>
          <template #default="{ row }">
            <el-checkbox :model-value="row.checked === 1" @change="(val) => toggleItem(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="商品信息" min-width="320">
          <template #default="{ row }">
            <div class="book-cell" @click="router.push('/books/' + row.bookId)">
              <img :src="row.coverUrl || placeholderCover(row.title)" class="book-cover" />
              <div class="book-info">
                <p class="book-title text-ellipsis-2">{{ row.title }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="center">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="140" align="center">
          <template #default="{ row }">
            <el-input-number
              :model-value="row.quantity"
              :min="1"
              :max="row.stock"
              size="small"
              controls-position="right"
              @change="(val) => changeQuantity(row, val)"
            />
            <div v-if="!row.stockEnough" class="stock-warn">库存不足</div>
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="center">
          <template #default="{ row }">
            <span class="price subtotal">{{ formatPrice(row.subtotal) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 结算栏 -->
      <div class="settle-bar">
        <div class="settle-left">
          <el-checkbox :model-value="allChecked" :indeterminate="isIndeterminate" @change="toggleAll">全选</el-checkbox>
          <el-button link type="danger" @click="batchRemove" :disabled="selectedRows.length === 0">删除选中</el-button>
        </div>
        <div class="settle-right">
          <span class="settle-text">已选 <b>{{ cartStore.checkedCount }}</b> 件</span>
          <span class="settle-text">合计：<span class="settle-price">{{ formatPrice(cartStore.checkedAmount) }}</span></span>
          <el-button type="primary" size="large" @click="goCheckout">去结算</el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="cart-empty card">
      <el-result icon="info" title="购物车是空的" sub-title="快去挑选心仪的图书吧">
        <template #extra>
          <el-button type="primary" @click="router.push('/books')">去逛逛</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<style scoped>
.cart-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.cart-header h2 {
  font-size: 20px;
}

.cart-count {
  color: var(--text-sub);
  font-size: 13px;
}

.cart-content {
  overflow: hidden;
}

.cart-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-fill);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.book-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  cursor: pointer;
  padding: 8px 0;
}

.book-cover {
  width: 60px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.book-info {
  flex: 1;
  min-width: 0;
}

.book-title {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.4;
}

.price {
  color: var(--brand);
  font-weight: 600;
}

.subtotal {
  font-size: 15px;
}

.stock-warn {
  font-size: 12px;
  color: #e6a23c;
  margin-top: 4px;
}

.settle-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-fill);
  position: sticky;
  bottom: 0;
}

.settle-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.settle-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.settle-text {
  font-size: 14px;
  color: var(--text-regular);
}

.settle-text b {
  color: var(--brand);
  font-size: 16px;
}

.settle-price {
  color: var(--brand);
  font-size: 22px;
  font-weight: 700;
}

.cart-empty {
  padding: 40px;
}
</style>
