<template>
  <view class="book-card" hover-class="hover-dim" @tap="$emit('tap', book)">
    <view class="cover-wrap">
      <cover-box :book="book" />

      <!-- 左上角角标优先级：榜单名次 > 页面指定（如「新书」）> 折扣 > 分类名 -->
      <view v-if="rank" class="cover-tag rank" :class="{ gold: rank <= 1, silver: rank == 2, bronze: rank == 3 }">{{ rank }}</view>
      <view v-else-if="cornerTag" class="cover-tag">{{ cornerTag }}</view>
      <view v-else-if="discountLabel" class="cover-tag discount">{{ discountLabel }}</view>
      <view v-else-if="book.categoryName" class="cover-tag muted">{{ book.categoryName }}</view>

      <!-- 售罄遮罩：与其让用户点进去才发现买不了，不如在列表里就说清楚 -->
      <view v-if="soldOut" class="sold-mask">
        <text class="sold-text">售罄</text>
      </view>
    </view>

    <view class="info">
      <text class="title ellipsis-2">{{ book.title }}</text>
      <text class="author ellipsis">{{ authorText }}</text>
      <text class="desc ellipsis">{{ descText }}</text>

      <!-- 评分 + 促销标签：让读者在列表页就能判断「值不值得点进去」 -->
      <view class="meta-row">
        <view class="rating">
          <template v-if="hasRating">
            <text class="star">★</text>
            <text class="score">{{ ratingText }}</text>
            <text class="cnt">({{ reviewCount }})</text>
          </template>
          <template v-else>
            <text class="star empty">☆</text>
            <text class="no-rating">暂无评分</text>
          </template>
        </view>
        <view v-if="tags.length" class="tags">
          <text v-for="t in tags" :key="t" class="promo">{{ t }}</text>
        </view>
      </view>

      <view class="bottom">
        <view class="price-wrap">
          <text class="rmb">¥</text>
          <text class="num">{{ priceText }}</text>
          <text v-if="showOriginal && originalText" class="origin">¥{{ originalText }}</text>
        </view>
      </view>

      <!-- 操作区：单按钮（加购）或双按钮（加购 + 立即购买） -->
      <view v-if="action === 'both'" class="actions">
        <view class="act outline" hover-class="hover-scale" @tap.stop="onAdd">{{ soldOut ? '已售罄' : '加购' }}</view>
        <view class="act solid" :class="{ disabled: soldOut }" hover-class="hover-scale" @tap.stop="onBuy">
          立即购买
        </view>
      </view>
      <view v-else class="actions single">
        <text v-if="action === 'none'" class="sales">已售 {{ salesText }}</text>
        <view
          v-else
          class="act solid"
          :class="{ disabled: soldOut }"
          hover-class="hover-scale"
          @tap.stop="onAdd"
        >
          {{ soldOut ? '售罄' : '加购' }}
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 双列图书卡片：首页楼层、分类、榜单、新书、低价、书单、个人中心共用。
 *
 * 三种展示强度，靠 props 切换而不是复制三份模板：
 *   action='cart'   标准卡片（价格 + 加购）
 *   action='both'   低价场景（价格 + 加购 + 立即购买）
 *   action='none'   纯展示（价格 + 销量）
 * showOriginal=true 时加出「原价删除线 + 折扣角标」，用于新书页与低价页。
 *
 * 封面由 cover-box 兜底：真实图 → 渐变块 + 书名首字，不会出现黑洞。
 */
import { computed } from 'vue'
import { price } from '@/common/format'
import { discountText } from '@/common/cover'
import { addBookToCart } from '@/common/cart-action'
import CoverBox from '@/components/cover-box.vue'

const props = defineProps({
  /** 后端 BookVO（演示数据集字段与之同构） */
  book: { type: Object, required: true },
  /** cart=加购 / both=加购+立即购买 / none=只展示销量 */
  action: { type: String, default: 'cart' },
  /** 榜单序号，传入后左上角显示排名 */
  rank: { type: [Number, String], default: '' },
  /** 左上角角标文案，如「新书」 */
  cornerTag: { type: String, default: '' },
  /** 是否显示原价删除线与折扣角标 */
  showOriginal: { type: Boolean, default: false }
})

const emit = defineEmits(['tap', 'added', 'buy'])

const priceText = computed(() => price(props.book.price))
const originalText = computed(() =>
  props.book.originalPrice ? price(props.book.originalPrice) : ''
)
const discountLabel = computed(() => discountText(props.book.discount))
const soldOut = computed(() => Number(props.book.stock || 0) <= 0)

const salesText = computed(() => {
  const s = Number(props.book.sales || 0)
  return s >= 10000 ? (s / 10000).toFixed(1) + '万' : String(s)
})

/** 作者 · 出版社：两者都有时并列，只有其一时显示那一个 */
const authorText = computed(() => {
  const a = props.book.author || ''
  const p = props.book.publisher || ''
  if (a && p) return `${a} · ${p}`
  return a || p
})

/** 简介单行展示；没有简介时给占位文案，保证卡片等高 */
const descText = computed(() => props.book.description || '暂无简介')

/** avgRating 无评价时为 null，不能当 0 展示 */
const hasRating = computed(() => {
  const n = Number(props.book.avgRating)
  return !isNaN(n) && n > 0
})

const ratingText = computed(() => Number(props.book.avgRating).toFixed(1))
const reviewCount = computed(() => Number(props.book.reviewCount) || 0)

/**
 * 促销标签：数据集自带的（特价/满减/清仓）优先；
 * 没有时回落到「库存紧张 / 热销」这类由数据推导的标签。
 * 已售罄由封面遮罩表达，这里不再重复。
 */
const tags = computed(() => {
  const preset = props.book.promoTags
  if (Array.isArray(preset) && preset.length) return preset
  if (soldOut.value) return []
  const stock = Number(props.book.stock || 0)
  if (stock > 0 && stock <= 5) return ['仅剩 ' + stock + ' 件']
  if (Number(props.book.sales || 0) >= 1000) return ['热销']
  return []
})

async function onAdd() {
  if (soldOut.value) return
  const res = await addBookToCart(props.book, 1)
  if (res.ok) emit('added', props.book)
}

function onBuy() {
  if (soldOut.value) return
  emit('buy', props.book)
}
</script>

<style lang="scss" scoped>
.book-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background-color: $bg-card;
  border: 1rpx solid $border-color;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
  overflow: hidden;
}

.cover-wrap {
  position: relative;
  width: 100%;
  /* 封面随卡片宽度按 3:4 等比缩放（接近真实书封）：
     不同页面列宽不同（分类页窄、榜单页宽），固定高度会拉长或压扁，比例恒定才不变形 */
  height: 0;
  padding-top: 133.33%;
  background-color: $bg-fill;
}

/* padding-top 撑出的容器没有内容高度，封面组件改为绝对定位铺满 */
.cover-wrap :deep(.cover-box) {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.cover-tag {
  position: absolute;
  top: 0;
  left: 0;
  max-width: 200rpx;
  padding: 4rpx 16rpx;
  font-size: $fs-xs;
  color: $color-white;
  background-color: $brand;
  border-radius: 0 0 $radius-sm 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cover-tag.muted {
  background-color: rgba(31, 33, 38, 0.55);
}

.cover-tag.discount {
  font-weight: 700;
  background: linear-gradient(135deg, #e4574e 0%, #a62a24 100%);
}

.cover-tag.rank {
  padding: 4rpx 14rpx;
  font-weight: 700;
  background-color: $brand-dark;
}

.cover-tag.rank.gold {
  background: linear-gradient(135deg, #f6d365 0%, #f0a00a 100%);
  color: #5c3d05;
}

.cover-tag.rank.silver {
  background: linear-gradient(135deg, #e8e8e8 0%, #b5b5b5 100%);
  color: #3a3a3a;
}

.cover-tag.rank.bronze {
  background: linear-gradient(135deg, #e6b98a 0%, #b47e4a 100%);
  color: #4f2e0c;
}

.sold-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(31, 33, 38, 0.35);
}

.sold-text {
  padding: 8rpx 24rpx;
  font-size: $fs-md;
  font-weight: 600;
  color: $color-white;
  background-color: rgba(31, 33, 38, 0.6);
  border-radius: $radius-pill;
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: $gap-card;
  min-height: 0;
}

/* 统一文本行高：让双列卡片 info 区域等高，同一行底部对齐 */
.title {
  font-size: $fs-base;
  font-weight: 600;
  line-height: 1.5;
  color: $text-main;
  min-height: 78rpx;
  max-height: 78rpx;
}

.author {
  display: block;
  margin-top: $sp-1;
  min-height: 33rpx;
  font-size: $fs-xs;
  line-height: 1.5;
  color: $text-sub;
}

.desc {
  display: block;
  margin-top: 6rpx;
  min-height: 35rpx;
  font-size: $fs-xs;
  line-height: 1.6;
  color: $text-placeholder;
}

/* ---- 评分 + 促销标签 ---- */
.meta-row {
  margin-top: $sp-2;
  min-height: 36rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.rating {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  min-width: 0;
}

.star {
  font-size: $fs-xs;
  color: $warning;
}

.star.empty {
  color: $text-placeholder;
}

.score {
  margin-left: 4rpx;
  font-size: $fs-xs;
  font-weight: 600;
  color: $text-regular;
}

.cnt {
  margin-left: 4rpx;
  font-size: $fs-xs;
  color: $text-sub;
}

.no-rating {
  margin-left: 4rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}

.tags {
  flex-shrink: 0;
  margin-left: $sp-1;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.promo {
  margin-left: 6rpx;
  padding: 2rpx 12rpx;
  font-size: $fs-xs;
  color: $color-white;
  background-color: $brand;
  border-radius: $radius-sm;
}

.bottom {
  margin-top: auto;
  min-height: 56rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.price-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  min-width: 0;
}

.rmb {
  font-size: $fs-xs;
  font-weight: 600;
  color: $price-color;
}

.num {
  font-size: $fs-xl;
  font-weight: 700;
  color: $price-color;
}

/* 原价：删除线 + 弱化，与现价形成对比 */
.origin {
  margin-left: $sp-1;
  font-size: $fs-xs;
  color: $text-placeholder;
  text-decoration: line-through;
}

.sales {
  flex-shrink: 0;
  font-size: $fs-xs;
  color: $text-sub;
}

/* ---- 操作区 ---- */
.actions {
  margin-top: $sp-2;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.actions.single {
  justify-content: flex-end;
}

.act {
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $fs-sm;
  font-weight: 600;
  border-radius: $radius-pill;
  transition: opacity 0.15s;
}

.actions .act {
  flex: 1;
  min-width: 0;
}

.actions .act + .act {
  margin-left: $sp-2;
}

.actions.single .act {
  flex: 0 0 auto;
  padding: 0 32rpx;
}

.act.solid {
  color: $color-white;
  background-color: $brand;
}

.act.outline {
  color: $brand;
  background-color: $bg-card;
  border: 1rpx solid $brand;
}

.act.disabled {
  background-color: $border-strong;
  color: $color-white;
  border-color: $border-strong;
}

/* ============================================================
   桌面（≥960px）：PC 书城卡片形态
   ------------------------------------------------------------
   移动端是「大封面 + 紧凑信息」的竖版卡片；桌面列宽只有约 210px，
   沿用移动端尺度会让卡片高达 480px，一屏放不下两个楼层，看着就是手机页面。
   这里只改尺度不改结构（封面仍是 3:4）：标题两行、简介显示出来、
   价格放大到 22px，让它是「商品卡」而不是「手机卡片放大」。
   移动端完全不受影响——下面全部写在媒体查询里。
   ============================================================ */
@media (min-width: 960px) {
  .book-card {
    border-radius: 8px;
  }

  .info {
    padding: 12px;
  }

  .title {
    font-size: 15px;
    min-height: 45px;
    max-height: 45px;
  }

  .author {
    min-height: 20px;
    font-size: 13px;
  }

  /* 桌面列宽下简介有空间显示两行；移动端仍是单行省略 */
  .desc {
    min-height: 38px;
    max-height: 38px;
    font-size: 12px;
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .meta-row {
    min-height: 22px;
  }

  .star,
  .score,
  .cnt,
  .no-rating,
  .origin,
  .sales {
    font-size: 12px;
  }

  .rmb {
    font-size: 13px;
  }

  .num {
    font-size: 22px;
  }

  .act {
    height: 36px;
    font-size: 14px;
    border-radius: 4px;
  }

  /* 单按钮卡片（首页楼层）：桌面拉满宽度，鼠标点击目标更大 */
  .actions.single .act {
    flex: 1;
    padding: 0;
  }

  .sold-text {
    padding: 6px 16px;
    font-size: 14px;
  }
}
</style>
