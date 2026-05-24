<script setup lang="ts">
import { computed } from 'vue'

type Size = 'sm' | 'md' | 'lg' | 'xl'

const props = withDefaults(
  defineProps<{
    amount: number
    size?: Size
    currency?: string
  }>(),
  {
    size: 'md',
    currency: '¥',
  },
)

const formatted = computed(() => {
  const v = Number.isFinite(props.amount) ? props.amount : 0
  return v.toFixed(2)
})
</script>

<template>
  <span class="price" :class="[`price--${size}`]">
    <span class="price__currency">{{ currency }}</span><span class="price__amount">{{ formatted }}</span>
  </span>
</template>

<style scoped>
.price {
  display: inline-flex;
  align-items: baseline;
  font-family: var(--font-sans);
  color: var(--ink-900);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.price__currency {
  font-weight: 500;
  margin-right: 2px;
}

.price--sm {
  font-size: 14px;
  font-weight: 600;
}

.price--md {
  font-size: var(--t-price-md-size); /* 20px */
  font-weight: var(--t-price-md-weight); /* 600 */
}

.price--md .price__currency {
  font-size: 14px;
}

.price--lg {
  font-size: var(--t-price-lg-size); /* 32px */
  font-weight: var(--t-price-lg-weight); /* 700 */
}

.price--lg .price__currency {
  font-size: 20px;
}

.price--xl {
  font-family: var(--font-display);
  font-size: var(--t-amount-display-size); /* 56px */
  font-weight: var(--t-amount-display-weight); /* 700 */
  letter-spacing: -0.01em;
}

.price--xl .price__currency {
  font-size: 28px;
  letter-spacing: 0;
}
</style>
