<script setup lang="ts">
import { computed } from 'vue'

type Variant = 'primary' | 'ghost' | 'text'
type Size = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    loading?: boolean
    disabled?: boolean
    full?: boolean
    type?: 'button' | 'submit' | 'reset'
  }>(),
  {
    variant: 'primary',
    size: 'md',
    loading: false,
    disabled: false,
    full: false,
    type: 'button',
  },
)

const classes = computed(() => [
  'btn',
  `btn--${props.variant}`,
  `btn--${props.size}`,
  { 'btn--full': props.full, 'btn--loading': props.loading },
])

const isDisabled = computed(() => props.disabled || props.loading)
</script>

<template>
  <button
    :type="type"
    :class="classes"
    :disabled="isDisabled"
    :aria-busy="loading"
  >
    <span v-if="loading" class="btn__dots" aria-hidden="true">
      <span class="btn__dot" />
      <span class="btn__dot" />
      <span class="btn__dot" />
    </span>
    <span v-else class="btn__label">
      <slot />
    </span>
  </button>
</template>

<style scoped>
.btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-weight: 500;
  line-height: 1;
  border-radius: var(--radius);
  cursor: pointer;
  user-select: none;
  transition: background-color var(--dur-2) var(--ease),
    color var(--dur-2) var(--ease),
    border-color var(--dur-2) var(--ease),
    transform var(--dur-1) var(--ease-in);
  will-change: transform;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}

.btn:disabled {
  cursor: not-allowed;
}

.btn--primary {
  background: var(--ink-900);
  color: var(--surface);
  border: 1px solid var(--ink-900);
}

.btn--primary:hover:not(:disabled) {
  background: var(--ink-700);
  border-color: var(--ink-700);
}

.btn--primary:disabled {
  background: var(--ink-300);
  border-color: var(--ink-300);
  color: var(--surface);
}

.btn--ghost {
  background: transparent;
  color: var(--ink-900);
  border: 1px solid var(--ink-300);
}

.btn--ghost:hover:not(:disabled) {
  border-color: var(--ink-900);
  background: var(--ink-100);
}

.btn--ghost:disabled {
  color: var(--ink-300);
  border-color: var(--ink-100);
}

.btn--text {
  background: transparent;
  color: var(--ink-700);
  border: 1px solid transparent;
  padding-left: 4px !important;
  padding-right: 4px !important;
}

.btn--text:hover:not(:disabled) {
  color: var(--ink-900);
}

.btn--text:disabled {
  color: var(--ink-300);
}

.btn--sm {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.btn--md {
  height: 40px;
  padding: 0 16px;
  font-size: 14px;
}

.btn--lg {
  height: 48px;
  padding: 0 20px;
  font-size: 15px;
}

.btn--full {
  width: 100%;
}

.btn__dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.btn__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.8;
  animation: btn-pulse 1.2s ease-in-out infinite;
}

.btn__dot:nth-child(2) {
  animation-delay: 0.15s;
}

.btn__dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes btn-pulse {
  0%, 80%, 100% {
    opacity: 0.25;
    transform: scale(0.9);
  }
  40% {
    opacity: 1;
    transform: scale(1.1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .btn {
    transition: none;
  }
  .btn:active:not(:disabled) {
    transform: none;
  }
  .btn__dot {
    animation: none;
  }
}
</style>
