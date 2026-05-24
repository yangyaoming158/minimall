<script setup lang="ts">
import { computed, useSlots } from 'vue'

type Tone = 'info' | 'warn' | 'danger' | 'neutral'

const props = withDefaults(
  defineProps<{
    tone?: Tone
    title?: string
  }>(),
  { tone: 'info' },
)

const slots = useSlots()
const hasBody = computed(() => Boolean(slots.default))
const classes = computed(() => ['notice', `notice--${props.tone}`])
</script>

<template>
  <div :class="classes" role="status">
    <div v-if="title" class="notice__title">{{ title }}</div>
    <div v-if="hasBody" class="notice__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.notice {
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  background: var(--surface);
  font-family: var(--font-sans);
}

.notice__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-900);
  line-height: 1.4;
}

.notice__body {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ink-700);
  line-height: 1.5;
}

.notice--info {
  border-color: var(--ink-100);
}

.notice--info .notice__title {
  color: var(--ink-900);
}

.notice--warn {
  border-color: rgba(176, 122, 26, 0.3);
  background: rgba(176, 122, 26, 0.04);
}

.notice--warn .notice__title {
  color: var(--warn);
}

.notice--danger {
  border-color: rgba(178, 58, 42, 0.3);
  background: var(--danger-soft);
}

.notice--danger .notice__title {
  color: var(--danger);
}

.notice--neutral {
  border-color: var(--ink-100);
  background: var(--canvas-darker);
}
</style>
