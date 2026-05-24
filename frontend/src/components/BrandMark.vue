<script setup lang="ts">
defineProps<{ size?: 'sm' | 'md'; dot?: boolean }>()
</script>

<template>
  <span class="brand" :class="[`brand--${size ?? 'md'}`]">
    <span v-if="dot" class="brand__dot" aria-hidden="true" />
    <span class="brand__word">MiniMall</span>
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-900);
}

.brand__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-terracotta);
}

.brand__word {
  font-family: var(--font-display);
  font-weight: var(--t-brand-weight);
  letter-spacing: var(--t-brand-track);
  line-height: 1;
}

.brand--md .brand__word {
  font-size: var(--t-brand-size); /* 18px */
}

.brand--sm .brand__word {
  font-size: 14px;
}

.brand--sm .brand__dot {
  width: 5px;
  height: 5px;
}
</style>
