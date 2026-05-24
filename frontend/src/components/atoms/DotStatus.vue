<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'neutral' | 'terracotta' | 'success' | 'warn' | 'danger'

const props = withDefaults(
  defineProps<{
    tone?: Tone
    label?: string
    pulse?: boolean
  }>(),
  {
    tone: 'neutral',
    pulse: false,
  },
)

const classes = computed(() => [
  'dot-status',
  `dot-status--${props.tone}`,
  { 'dot-status--pulse': props.pulse },
])
</script>

<template>
  <span :class="classes">
    <span class="dot-status__dot" aria-hidden="true" />
    <span class="dot-status__label">
      <slot>{{ label }}</slot>
    </span>
  </span>
</template>

<style scoped>
.dot-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-700);
  line-height: 1;
}

.dot-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-status--neutral .dot-status__dot {
  background: var(--ink-500);
}

.dot-status--terracotta .dot-status__dot {
  background: var(--accent-terracotta);
}

.dot-status--success .dot-status__dot {
  background: var(--success);
}

.dot-status--warn .dot-status__dot {
  background: var(--warn);
}

.dot-status--danger .dot-status__dot {
  background: var(--danger);
}

.dot-status--pulse .dot-status__dot {
  animation: dot-pulse 1.2s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.25);
    opacity: 0.7;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dot-status--pulse .dot-status__dot {
    animation: none;
  }
}
</style>
