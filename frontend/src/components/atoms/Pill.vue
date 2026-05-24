<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'neutral' | 'terracotta' | 'success' | 'warn' | 'danger'

const props = withDefaults(
  defineProps<{
    tone?: Tone
    soft?: boolean
  }>(),
  {
    tone: 'neutral',
    soft: true,
  },
)

const classes = computed(() => [
  'pill',
  `pill--${props.tone}`,
  { 'pill--soft': props.soft, 'pill--solid': !props.soft },
])
</script>

<template>
  <span :class="classes">
    <slot />
  </span>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  border-radius: 999px;
  white-space: nowrap;
}

.pill--soft.pill--neutral {
  background: var(--ink-100);
  color: var(--ink-700);
}

.pill--soft.pill--terracotta {
  background: var(--accent-terracotta-soft);
  color: var(--accent-terracotta);
}

.pill--soft.pill--success {
  background: var(--success-soft);
  color: var(--success);
}

.pill--soft.pill--warn {
  background: rgba(176, 122, 26, 0.12);
  color: var(--warn);
}

.pill--soft.pill--danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.pill--solid.pill--neutral {
  background: var(--ink-900);
  color: var(--surface);
}

.pill--solid.pill--terracotta {
  background: var(--accent-terracotta);
  color: var(--surface);
}

.pill--solid.pill--success {
  background: var(--success);
  color: var(--surface);
}

.pill--solid.pill--warn {
  background: var(--warn);
  color: var(--surface);
}

.pill--solid.pill--danger {
  background: var(--danger);
  color: var(--surface);
}
</style>
