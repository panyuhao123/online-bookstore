<template>
  <!-- 桌面筛选面板（仅桌面渲染）：折叠分组，避免左栏被筛选项拉得过长 -->
  <view class="wf">
    <!-- ① 价格：快捷区间 + 自定义区间 -->
    <view class="wf-group">
      <view class="wf-head" @tap="open.price = !open.price">
        <text class="wf-title">价格</text>
        <text class="wf-arrow">{{ open.price ? '▾' : '›' }}</text>
      </view>
      <view v-if="open.price" class="wf-body">
        <view class="wf-chips">
          <text
            v-for="r in RANGES"
            :key="r.key"
            class="wf-chip"
            :class="{ on: activeRange === r.key }"
            @tap="pickRange(r)"
          >
            {{ r.label }}
          </text>
        </view>
        <view class="wf-inputs">
          <input
            class="wf-input"
            v-model="minText"
            type="digit"
            placeholder="最低价"
            placeholder-class="wf-ph"
          />
          <text class="wf-dash">—</text>
          <input
            class="wf-input"
            v-model="maxText"
            type="digit"
            placeholder="最高价"
            placeholder-class="wf-ph"
          />
        </view>
        <view class="wf-apply" @tap="applyPrice">确定</view>
      </view>
    </view>

    <!-- ② 评分 -->
    <view class="wf-group">
      <view class="wf-head" @tap="open.rating = !open.rating">
        <text class="wf-title">评分</text>
        <text class="wf-arrow">{{ open.rating ? '▾' : '›' }}</text>
      </view>
      <view v-if="open.rating" class="wf-body">
        <text
          v-for="r in RATINGS"
          :key="r.value"
          class="wf-line"
          :class="{ on: model.minRating === r.value }"
          @tap="setRating(r.value)"
        >
          {{ r.label }}
        </text>
      </view>
    </view>

    <!-- ③ 出版社：选项来自当前分类的真实图书，没有就不渲染这一组 -->
    <view v-if="publishers.length" class="wf-group">
      <view class="wf-head" @tap="open.publisher = !open.publisher">
        <text class="wf-title">出版社</text>
        <text class="wf-arrow">{{ open.publisher ? '▾' : '›' }}</text>
      </view>
      <view v-if="open.publisher" class="wf-body">
        <text
          v-for="p in shownPublishers"
          :key="p"
          class="wf-line"
          :class="{ on: model.publisher === p }"
          @tap="setPublisher(p)"
        >
          {{ p }}
        </text>
        <text v-if="publishers.length > 5" class="wf-toggle" @tap="allPub = !allPub">
          {{ allPub ? '收起' : '展开更多出版社' }}
        </text>
      </view>
    </view>

    <view v-if="hasFilter" class="wf-clear" @tap="clear">清空筛选条件</view>
  </view>
</template>

<script setup>
/**
 * 桌面筛选面板。
 *
 * 两组能力的来源不同，实现也不同，不要混为一谈：
 *   - 价格区间：查询接口本来就支持 minPrice / maxPrice（后端 BookQuery 已实现，
 *     还自带「填反了自动对调」），所以这里填完直接作为查询条件交给页面。
 *   - 评分 / 出版社：接口没有对应参数（图书表也没有出版时间字段），
 *     改为在页面已取到的结果集里做前端过滤 —— 演示数据量在百级，代价可接受，
 *     也不需要为了一个筛选条件去改后端。
 *
 * 出版社选项由页面从当前结果里统计后传入（props.publishers），
 * 组件不写死出版社名单，换数据集自动跟着变。
 */
import { ref, reactive, computed } from 'vue'

const RANGES = [
  { key: '0-30', label: '30 元以下', min: 0, max: 30 },
  { key: '30-60', label: '30-60 元', min: 30, max: 60 },
  { key: '60-100', label: '60-100 元', min: 60, max: 100 },
  { key: '100+', label: '100 元以上', min: 100, max: null }
]

const RATINGS = [
  { value: 0, label: '全部' },
  { value: 4, label: '4 星及以上' },
  { value: 3, label: '3 星及以上' }
]

const props = defineProps({
  /** 当前筛选条件（受控） */
  model: { type: Object, required: true },
  /** 当前结果里出现过的出版社，由页面统计传入 */
  publishers: { type: Array, default: () => [] }
})

const emit = defineEmits(['change', 'clear'])

/** 三个分组默认展开价格（最常用），其余收起，避免左栏过长 */
const open = reactive({ price: true, rating: true, publisher: false })
const allPub = ref(false)

const minText = ref(props.model.minPrice == null ? '' : String(props.model.minPrice))
const maxText = ref(props.model.maxPrice == null ? '' : String(props.model.maxPrice))

const activeRange = computed(() => {
  const m = props.model
  const hit = RANGES.find((r) => r.min === m.minPrice && r.max === m.maxPrice)
  return hit ? hit.key : ''
})

const shownPublishers = computed(() =>
  allPub.value ? props.publishers : props.publishers.slice(0, 5)
)

const hasFilter = computed(
  () =>
    props.model.minPrice != null ||
    props.model.maxPrice != null ||
    Number(props.model.minRating || 0) > 0 ||
    !!props.model.publisher
)

function emitChange(patch) {
  emit('change', { ...props.model, ...patch })
}

function pickRange(r) {
  // 再点一次已选中的区间 = 取消该区间
  if (activeRange.value === r.key) {
    minText.value = ''
    maxText.value = ''
    emitChange({ minPrice: null, maxPrice: null })
    return
  }
  minText.value = r.min == null ? '' : String(r.min)
  maxText.value = r.max == null ? '' : String(r.max)
  emitChange({ minPrice: r.min, maxPrice: r.max })
}

/**
 * 自定义区间：只接受合法数字，填反了自动对调（与后端 BookQuery 的行为一致，
 * 用户在筛选框里填反顺序很常见，直接按他表达的范围过滤比提示报错更顺手）。
 */
function applyPrice() {
  let min = parseFloat(minText.value)
  let max = parseFloat(maxText.value)
  min = isNaN(min) || min < 0 ? null : min
  max = isNaN(max) || max < 0 ? null : max
  if (min != null && max != null && min > max) {
    const t = min
    min = max
    max = t
    minText.value = String(min)
    maxText.value = String(max)
  }
  emitChange({ minPrice: min, maxPrice: max })
}

function setRating(v) {
  emitChange({ minRating: v })
}

function setPublisher(p) {
  emitChange({ publisher: props.model.publisher === p ? '' : p })
}

function clear() {
  minText.value = ''
  maxText.value = ''
  emit('clear')
}
</script>

<style lang="scss" scoped>
.wf {
  margin-top: 10px;
}

.wf-group {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 6px;
  margin-bottom: 8px;
  overflow: hidden;
}

.wf-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-bottom: 1px solid $border-color;
}

.wf-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-main;
}

.wf-arrow {
  font-size: 11px;
  color: $text-placeholder;
}

.wf-body {
  padding: 9px 12px;
}

.wf-chips {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 6px;
}

.wf-chip {
  padding: 4px 8px;
  font-size: 12px;
  color: $text-regular;
  background-color: $bg-fill;
  border-radius: 3px;
}

.wf-chip.on {
  color: $brand;
  background-color: $brand-weak;
}

.wf-inputs {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.wf-input {
  flex: 1;
  min-width: 0;
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  color: $text-main;
  background-color: $bg-fill;
  border-radius: 3px;
}

.wf-dash {
  font-size: 12px;
  color: $text-placeholder;
}

.wf-apply {
  margin-top: 8px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: $color-white;
  background-color: $brand;
  border-radius: 3px;
}

.wf-line {
  display: block;
  padding: 4px 0;
  font-size: 12px;
  color: $text-regular;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wf-line.on {
  color: $brand;
  font-weight: 600;
}

.wf-toggle {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: $brand;
}

.wf-clear {
  padding: 6px 0;
  text-align: center;
  font-size: 12px;
  color: $text-placeholder;
}

@media (hover: hover) and (pointer: fine) {
  .wf-head,
  .wf-chip,
  .wf-line,
  .wf-apply,
  .wf-toggle,
  .wf-clear {
    cursor: pointer;
  }

  .wf-chip:hover {
    background-color: $border-color;
  }

  .wf-line:hover {
    color: $brand;
  }

  .wf-apply:hover {
    background-color: $brand-dark;
  }

  .wf-clear:hover {
    color: $brand;
  }
}
</style>
