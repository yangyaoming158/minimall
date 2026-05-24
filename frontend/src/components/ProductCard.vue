<script setup lang="ts">
import { computed } from 'vue'
import ProductCover from '@/components/ProductCover.vue'
import Pill from '@/components/atoms/Pill.vue'
import PriceText from '@/components/atoms/PriceText.vue'
import type { Product } from '@/types/product'

const props = defineProps<{ product: Product }>()
const emit = defineEmits<{ (e: 'view', productId: string): void }>()

const offShelf = computed(() => props.product.status === 'OFF_SHELF')

function onView() {
  emit('view', props.product.productId)
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    onView()
  }
}
</script>

<template>
  <article
    class="card"
    :class="{ 'card--off-shelf': offShelf }"
    role="link"
    tabindex="0"
    :aria-label="product.name"
    @click="onView"
    @keydown="onKey"
  >
    <div class="card__cover">
      <ProductCover
        :product-id="product.productId"
        :name="product.name"
        aspect="4:5"
        grade="list"
        size="full"
      />
    </div>

    <div class="card__body">
      <header class="card__head">
        <h3 class="card__name" :title="product.name">{{ product.name }}</h3>
        <Pill :tone="offShelf ? 'neutral' : 'success'" soft>
          {{ offShelf ? '已下架' : '在售' }}
        </Pill>
      </header>

      <p class="card__desc">{{ product.description || '暂无描述' }}</p>

      <footer class="card__foot">
        <PriceText :amount="product.price" size="md" />
      </footer>
    </div>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition:
    box-shadow var(--dur-2) var(--ease),
    transform var(--dur-2) var(--ease),
    border-color var(--dur-2) var(--ease);
  outline: none;
}

.card:hover {
  border-color: var(--ink-300);
  box-shadow: var(--shadow-2);
  transform: translateY(-2px);
}

.card:focus-visible {
  box-shadow: var(--shadow-press);
  border-color: var(--ink-900);
}

.card__cover {
  width: 100%;
  overflow: hidden;
  background: var(--canvas-darker);
}

.card__cover :deep(.cover) {
  border-radius: 0;
  width: 100%;
  transition: transform var(--dur-3) var(--ease);
}

.card:hover .card__cover :deep(.cover) {
  transform: scale(1.04);
}

.card--off-shelf {
  opacity: 0.65;
}

.card--off-shelf:hover {
  border-color: var(--ink-100);
  transform: none;
  box-shadow: none;
}

.card--off-shelf:hover .card__cover :deep(.cover) {
  transform: none;
}

.card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
}

.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card__name {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-h2-size);
  font-weight: var(--t-h2-weight);
  line-height: var(--t-h2-lh);
  color: var(--ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}

.card__desc {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--ink-500);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: calc(13px * 1.5 * 2);
}

.card__foot {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (prefers-reduced-motion: reduce) {
  .card,
  .card__cover :deep(.cover) {
    transition: none;
  }
  .card:hover,
  .card:hover .card__cover :deep(.cover) {
    transform: none;
  }
}
</style>
