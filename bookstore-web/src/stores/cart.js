import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getCartList,
  addToCart,
  updateCart,
  removeCart,
  removeCartBatch
} from '@/api/cart'

export const useCartStore = defineStore('cart', () => {
  const list = ref([])
  const checkedAmount = ref(0)
  const checkedCount = ref(0)
  const loading = ref(false)

  const totalCount = computed(() =>
    list.value.reduce((sum, item) => sum + (item.quantity || 0), 0)
  )

  const checkedIds = computed(() =>
    list.value.filter((i) => i.checked === 1).map((i) => i.id)
  )

  async function fetchCart() {
    loading.value = true
    try {
      const res = await getCartList()
      list.value = res.list || []
      checkedAmount.value = res.checkedAmount || 0
      checkedCount.value = res.checkedCount || 0
    } finally {
      loading.value = false
    }
  }

  async function add(bookId, quantity = 1) {
    await addToCart({ bookId, quantity })
    await fetchCart()
  }

  async function updateQuantity(cartId, quantity) {
    await updateCart(cartId, { quantity })
    await fetchCart()
  }

  async function toggleChecked(cartId, checked) {
    await updateCart(cartId, { checked: checked ? 1 : 0 })
    await fetchCart()
  }

  async function remove(cartId) {
    await removeCart(cartId)
    await fetchCart()
  }

  async function removeBatch(cartIds) {
    await removeCartBatch(cartIds)
    await fetchCart()
  }

  function reset() {
    list.value = []
    checkedAmount.value = 0
    checkedCount.value = 0
  }

  return {
    list,
    checkedAmount,
    checkedCount,
    loading,
    totalCount,
    checkedIds,
    fetchCart,
    add,
    updateQuantity,
    toggleChecked,
    remove,
    removeBatch,
    reset
  }
})
