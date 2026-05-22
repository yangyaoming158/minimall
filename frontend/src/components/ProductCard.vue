<script setup lang="ts">
import { computed } from 'vue'
import type { Product } from '@/types/product'

const props = defineProps<{ product: Product }>()
const emit = defineEmits<{ (e: 'view', productId: string): void }>()

const offShelf = computed(() => props.product.status === 'OFF_SHELF')

const priceText = computed(() => `¥${props.product.price.toFixed(2)}`)

function onView() {
  emit('view', props.product.productId)
}
</script>

<template>
  <article class="product-card" :class="{ 'is-off-shelf': offShelf }" @click="onView">
    <div class="card-head">
      <h3 class="product-name" :title="product.name">{{ product.name }}</h3>
      <el-tag :type="offShelf ? 'info' : 'success'" size="small" effect="light">
        {{ offShelf ? '已下架' : '在售' }}
      </el-tag>
    </div>

    <p class="product-desc">{{ product.description || '暂无描述' }}</p>

    <div class="card-foot">
      <span class="product-price">{{ priceText }}</span>
      <el-button
        type="primary"
        size="small"
        :plain="offShelf"
        @click.stop="onView"
      >
        查看详情
      </el-button>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 18px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.product-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.product-card.is-off-shelf {
  opacity: 0.6;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.product-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
  /* single-line ellipsis */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-desc {
  flex: 1;
  margin: 0 0 16px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
  /* clamp to 2 lines */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.product-price {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}
</style>
