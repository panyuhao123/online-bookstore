<template>
  <view class="page">
    <page-nav :title="isEdit ? '编辑地址' : '新增地址'" />
    <!-- 编辑态回填：先显示骨架，避免表单闪一下空值 -->
    <view v-if="loading" class="form">
      <view v-for="n in 4" :key="n" class="field">
        <view class="sk sk-anim" style="height: 28rpx; width: 60%" />
      </view>
    </view>

    <template v-else>
      <view class="form">
        <view class="field">
          <text class="label">收货人</text>
          <input
            class="input"
            v-model="form.receiver"
            placeholder="请输入收货人姓名"
            placeholder-class="ph"
            maxlength="20"
          />
        </view>

        <view class="field">
          <text class="label">手机号</text>
          <input
            class="input"
            v-model="form.phone"
            type="number"
            placeholder="请输入 11 位手机号"
            placeholder-class="ph"
            maxlength="11"
          />
        </view>

        <view class="field">
          <text class="label">所在地区</text>
          <!-- App / H5 端不支持 mode=region，改用 multiSelector + 本地三级数据 -->
          <picker
            v-if="regionReady"
            mode="multiSelector"
            :value="regionIndex"
            :range="regionRange"
            @change="onRegionChange"
            @columnchange="onRegionColumnChange"
          >
            <view class="picker" hover-class="hover-dim">
              <text :class="{ ph: !regionText }">{{ regionText || '请选择省 / 市 / 区' }}</text>
              <text class="arrow">›</text>
            </view>
          </picker>
          <view v-else class="picker" @tap="onRegionUnavailable">
            <text class="ph">地区数据加载失败，点击重试</text>
            <text class="arrow">›</text>
          </view>
        </view>

        <view class="field">
          <text class="label">详细地址</text>
          <input
            class="input"
          v-model="form.detail"
          placeholder="街道、楼牌号等"
          placeholder-class="ph"
          maxlength="60"
          confirm-type="done"
          @confirm="onSave"
        />
        </view>

        <view class="field switch-field">
          <text class="label">设为默认地址</text>
          <!-- switch 的 color 只接受字符串字面量，用常量绑定避免色值散落在模板里 -->
          <switch :checked="form.isDefault === 1" :color="SWITCH_COLOR" @change="onSwitch" />
        </view>
      </view>

      <view
        class="save-btn"
        :class="{ disabled: saving }"
        hover-class="hover-opacity"
        @tap="onSave"
      >
        {{ saving ? '保存中...' : '保存' }}
      </view>
      <view v-if="isEdit" class="del-btn" hover-class="hover-opacity" @tap="onDelete">删除该地址</view>
    </template>
    <web-footer v-if="isWide" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  addAddress,
  updateAddress,
  removeAddress,
  getAddressList
} from '@/api/address'
import { PROVINCES, getCities, getDistricts, resolveIndex } from '@/common/region'
import { requireLogin } from '@/common/guard'
import PageNav from '@/components/page-nav.vue'
import WebFooter from '@/components/web-footer.vue'
import { isWide } from '@/common/web'

/** 开关选中色，与 $brand 保持一致 */
const SWITCH_COLOR = '#C8362F'

const isEdit = ref(false)
const addressId = ref(null)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)

// 省市区三列的当前选中下标
const pIdx = ref(0)
const cIdx = ref(0)
const dIdx = ref(0)

const form = ref({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

const regionReady = computed(() => PROVINCES.length > 0)
const cities = computed(() => getCities(PROVINCES[pIdx.value]))
const districts = computed(() => getDistricts(PROVINCES[pIdx.value], cities.value[cIdx.value]))

const regionRange = computed(() => [PROVINCES, cities.value, districts.value])
const regionIndex = computed(() => [pIdx.value, cIdx.value, dIdx.value])

const regionText = computed(() => {
  const f = form.value
  return [f.province, f.city, f.district].filter(Boolean).join(' ')
})

/** 换省/换市后，后续列必须整体复位，否则下标会越界到上一个城市的数据 */
function onRegionColumnChange(e) {
  const { column, value } = e.detail
  if (column === 0) {
    pIdx.value = value
    cIdx.value = 0
    dIdx.value = 0
  } else if (column === 1) {
    cIdx.value = value
    dIdx.value = 0
  } else {
    dIdx.value = value
  }
}

function onRegionChange(e) {
  const v = e.detail.value || []
  pIdx.value = v[0] || 0
  cIdx.value = v[1] || 0
  dIdx.value = v[2] || 0
  form.value.province = PROVINCES[pIdx.value] || ''
  form.value.city = cities.value[cIdx.value] || ''
  form.value.district = districts.value[dIdx.value] || ''
}

function onRegionUnavailable() {
  uni.showToast({ title: '地区数据加载失败，请重启应用后重试', icon: 'none' })
}

function onSwitch(e) {
  form.value.isDefault = e.detail.value ? 1 : 0
}

/**
 * 表单校验：逐条给出可执行的具体提示，而不是笼统的「请填写完整」。
 * @returns {String} 错误文案，空串表示通过
 */
function validate() {
  const f = form.value
  const receiver = (f.receiver || '').trim()
  if (receiver.length < 2) return '收货人姓名至少 2 个字'
  if (!/^1[3-9]\d{9}$/.test(f.phone || '')) return '请输入正确的 11 位手机号'
  if (!f.province) return '请选择所在地区'
  const detail = (f.detail || '').trim()
  if (detail.length < 5) return '详细地址至少 5 个字，便于配送'
  return ''
}

async function onSave() {
  if (saving.value) return
  const msg = validate()
  if (msg) {
    uni.showToast({ title: msg, icon: 'none' })
    return
  }
  saving.value = true
  uni.showLoading({ title: '保存中', mask: true })
  try {
    // 提交前统一 trim，避免首尾空格被写进库
    const payload = {
      ...form.value,
      receiver: form.value.receiver.trim(),
      detail: form.value.detail.trim()
    }
    if (isEdit.value) {
      await updateAddress(addressId.value, payload)
    } else {
      await addAddress(payload)
    }
    uni.hideLoading()
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    uni.hideLoading()
  } finally {
    saving.value = false
  }
}

function onDelete() {
  if (deleting.value) return
  uni.showModal({
    title: '提示',
    content: '确定删除该收货地址吗？',
    success: async (res) => {
      if (!res.confirm) return
      deleting.value = true
      try {
        await removeAddress(addressId.value)
        uni.showToast({ title: '已删除', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
      } finally {
        deleting.value = false
      }
    }
  })
}

onLoad((options) => {
  if (!requireLogin()) return
  if (options && options.id) {
    isEdit.value = true
    addressId.value = options.id
    loading.value = true
    // 标题由 page-nav 的 :title 随 isEdit 响应式切换
    // 从列表取已有数据回填（列表接口返回字段最全）
    getAddressList()
      .then((list) => {
        const found = (list || []).find((i) => String(i.id) === String(options.id))
        if (!found) return
        form.value = {
          receiver: found.receiver,
          phone: found.phone,
          province: found.province || '',
          city: found.city || '',
          district: found.district || '',
          detail: found.detail || '',
          isDefault: found.isDefault
        }
        const idx = resolveIndex(found.province, found.city, found.district)
        if (idx) {
          pIdx.value = idx[0]
          cIdx.value = idx[1]
          dIdx.value = idx[2]
        } else {
          uni.showToast({ title: '原地区信息已变更，请重新选择', icon: 'none' })
        }
      })
      .catch(() => {})
      .finally(() => {
        loading.value = false
      })
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: calc(#{$sp-5} + #{$safe-bottom});
}

.form {
  margin: $gap-item $gap-page;
  border-radius: $radius-lg;
  background-color: $bg-card;
  box-shadow: $shadow-card;
  overflow: hidden;
}

.field {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 $gap-card;
  min-height: 104rpx;
  border-bottom: 1rpx solid $border-color;
}

.field:last-child {
  border-bottom: none;
}

.label {
  width: 180rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $text-main;
}

.input {
  flex: 1;
  min-width: 0;
  font-size: $fs-md;
  color: $text-main;
}

.picker {
  flex: 1;
  min-width: 0;
  height: 104rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  font-size: $fs-md;
  color: $text-main;
}

.arrow {
  font-size: 42rpx;
  color: $text-placeholder;
  line-height: 1;
}

.ph {
  color: $text-placeholder;
}

.switch-field {
  justify-content: space-between;
}

.save-btn {
  margin: $sp-5 $gap-page 0;
  height: $btn-h;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-pill;
}

.save-btn.disabled {
  background-color: $border-strong;
}

.del-btn {
  margin: $gap-item $gap-page 0;
  height: $btn-h;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-md;
  color: $brand;
  background-color: $bg-card;
  border-radius: $radius-pill;
  box-shadow: $shadow-card;
}
</style>
