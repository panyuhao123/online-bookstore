<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAddressList } from '@/api/address'
import { createOrder } from '@/api/order'
import { useCartStore } from '@/stores/cart'
import { formatPrice } from '@/utils/format'
import { bookCover, placeholderCover } from '@/utils/image'
import { ElMessage } from 'element-plus'

const router = useRouter()
const cartStore = useCartStore()

const addresses = ref([])
const selectedAddressId = ref(null)
const addressDrawer = ref(false)
const submitting = ref(false)

// 已勾选的购物车条目
const checkedItems = computed(() => cartStore.list.filter((i) => i.checked === 1))
const totalAmount = computed(() => cartStore.checkedAmount)
const totalQuantity = computed(() => checkedItems.value.reduce((s, i) => s + i.quantity, 0))

const selectedAddress = computed(() =>
  addresses.value.find((a) => a.id === selectedAddressId.value)
)

async function loadAddresses() {
  try {
    addresses.value = await getAddressList()
    // 默认选第一个（后端已把默认地址排最前）
    if (addresses.value.length > 0) {
      selectedAddressId.value = addresses.value[0].id
    }
  } catch (e) {
    /* 静默 */
  }
}

function openAddressDrawer() {
  addressDrawer.value = true
}

function selectAddress(id) {
  selectedAddressId.value = id
  addressDrawer.value = false
}

async function submitOrder() {
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (checkedItems.value.length === 0) {
    ElMessage.warning('没有可结算的商品')
    return
  }
  submitting.value = true
  try {
    const res = await createOrder({
      addressId: selectedAddressId.value,
      cartIds: checkedItems.value.map((i) => i.id)
    })
    ElMessage.success('下单成功')
    await cartStore.fetchCart()
    router.push('/orders/' + res.id)
  } catch (e) {
    /* 错误已在拦截器提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await cartStore.fetchCart()
  await loadAddresses()
})
</script>

<template>
  <div class="page-container order-confirm">
    <h2 class="page-title">确认订单</h2>

    <!-- 收货地址 -->
    <div class="section card">
      <div class="section-header">
        <h3>收货地址</h3>
        <el-button link type="primary" @click="openAddressDrawer">
          <el-icon><Edit /></el-icon> 选择其他地址
        </el-button>
      </div>
      <div v-if="selectedAddress" class="address-card">
        <el-icon :size="20" color="var(--brand)"><Location /></el-icon>
        <div class="address-info">
          <div class="address-top">
            <span class="receiver">{{ selectedAddress.receiver }}</span>
            <span class="phone">{{ selectedAddress.phone }}</span>
            <el-tag v-if="selectedAddress.isDefault === 1" size="small" type="danger">默认</el-tag>
          </div>
          <p class="address-detail">{{ selectedAddress.fullAddress }}</p>
        </div>
      </div>
      <div v-else class="no-address">
        <el-empty description="暂无收货地址" :image-size="80">
          <el-button type="primary" @click="router.push('/addresses')">去添加地址</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 商品清单 -->
    <div class="section card">
      <div class="section-header">
        <h3>商品清单</h3>
        <span class="count-text">共 {{ totalQuantity }} 件</span>
      </div>
      <el-table :data="checkedItems" style="width: 100%">
        <el-table-column label="商品" min-width="320">
          <template #default="{ row }">
            <div class="book-cell">
              <img :src="row.coverUrl || placeholderCover(row.title)" class="book-cover" />
              <div class="book-info">
                <p class="book-title text-ellipsis-2">{{ row.title }}</p>
                <p class="book-price">{{ formatPrice(row.price) }} × {{ row.quantity }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="center">
          <template #default="{ row }">
            <span>{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="100" align="center">
          <template #default="{ row }">
            {{ row.quantity }}
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="center">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.subtotal) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 金额汇总 + 提交 -->
    <div class="section card amount-section">
      <div class="amount-row">
        <span>商品总额：</span>
        <span>{{ formatPrice(totalAmount) }}</span>
      </div>
      <div class="amount-row">
        <span>运费：</span>
        <span class="free">免运费</span>
      </div>
      <div class="amount-row total">
        <span>应付金额：</span>
        <span class="total-price">{{ formatPrice(totalAmount) }}</span>
      </div>
      <div class="submit-row">
        <el-button type="primary" size="large" :loading="submitting" @click="submitOrder">
          提交订单
        </el-button>
      </div>
    </div>

    <!-- 地址选择抽屉 -->
    <el-drawer v-model="addressDrawer" title="选择收货地址" size="480px">
      <div class="address-list">
        <div
          v-for="addr in addresses"
          :key="addr.id"
          :class="['address-option', { active: addr.id === selectedAddressId }]"
          @click="selectAddress(addr.id)"
        >
          <div class="addr-top">
            <span class="receiver">{{ addr.receiver }}</span>
            <span class="phone">{{ addr.phone }}</span>
            <el-tag v-if="addr.isDefault === 1" size="small" type="danger">默认</el-tag>
          </div>
          <p class="addr-detail">{{ addr.fullAddress }}</p>
        </div>
        <div v-if="addresses.length === 0" class="no-addr">
          <el-empty description="暂无地址" />
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="router.push('/addresses')">
          <el-icon><Plus /></el-icon> 新增地址
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 16px;
}

.section {
  padding: 20px 24px;
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.section-header h3 {
  font-size: 16px;
}

.count-text {
  font-size: 13px;
  color: var(--text-sub);
}

.address-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: var(--bg-fill);
  padding: 16px;
  border-radius: var(--radius-md);
  border-left: 4px solid var(--brand);
}

.address-info {
  flex: 1;
}

.address-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.receiver {
  font-weight: 600;
  font-size: 15px;
}

.phone {
  color: var(--text-regular);
}

.address-detail {
  color: var(--text-regular);
  line-height: 1.5;
}

.no-address {
  padding: 20px;
}

.book-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
}

.book-cover {
  width: 56px;
  height: 74px;
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
  margin-bottom: 4px;
}

.book-price {
  font-size: 12px;
  color: var(--text-sub);
}

.price {
  color: var(--brand);
  font-weight: 600;
}

.amount-section {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.amount-row {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 12px;
  width: 100%;
  max-width: 320px;
  margin-bottom: 10px;
  font-size: 14px;
  color: var(--text-regular);
}

.amount-row.total {
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
  font-size: 16px;
  color: var(--text-primary);
}

.free {
  color: #67c23a;
}

.total-price {
  color: var(--brand);
  font-size: 24px;
  font-weight: 700;
}

.submit-row {
  margin-top: 16px;
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.address-option {
  padding: 14px 16px;
  border: 2px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
}

.address-option:hover {
  border-color: var(--brand);
}

.address-option.active {
  border-color: var(--brand);
  background: var(--brand-weak);
}

.addr-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.addr-detail {
  color: var(--text-regular);
  line-height: 1.5;
}

.no-addr {
  padding: 40px 0;
}
</style>
