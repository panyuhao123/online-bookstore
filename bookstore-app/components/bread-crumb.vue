<template>
  <!-- 面包屑（仅桌面渲染）：最后一项是当前页，不可点 -->
  <view class="bc">
    <template v-for="(it, i) in items" :key="i">
      <text
        v-if="i < items.length - 1"
        class="bc-link"
        hover-class="hover-opacity"
        @tap="onTap(it)"
      >
        {{ it.name }}
      </text>
      <text v-else class="bc-cur">{{ it.name }}</text>
      <text v-if="i < items.length - 1" class="bc-sep">›</text>
    </template>
  </view>
</template>

<script setup>
/**
 * PC 面包屑。
 *
 * 为什么桌面要加这一层：移动端靠「返回键」回溯，桌面浏览器是多标签 + 前进后退，
 * 用户往往从搜索结果或直接输入地址进来，没有上一页可返回；
 * 面包屑同时承担「告诉用户当前在站内什么位置」和「给出上跳入口」两件事，
 * 是京东 / 当当列表页与详情页的标配。层级控制在 3~4 级以内。
 *
 * 最后一项不做链接：链接到当前页没有意义，反而让人怀疑是不是点错了。
 */
const props = defineProps({
  /** [{ name, url }]，最后一项 url 可省略 */
  items: { type: Array, required: true }
})

function onTap(it) {
  if (!it || !it.url) return
  uni.redirectTo({ url: it.url })
}
</script>

<style lang="scss" scoped>
.bc {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  padding: 12px 24px 0;
  font-size: 12px;
  color: $text-sub;
}

.bc-link {
  color: $text-sub;
}

.bc-cur {
  color: $text-regular;
}

.bc-sep {
  margin: 0 6px;
  color: $brand;
}

@media (hover: hover) and (pointer: fine) {
  .bc-link {
    cursor: pointer;
  }

  .bc-link:hover {
    color: $brand;
  }
}
</style>
