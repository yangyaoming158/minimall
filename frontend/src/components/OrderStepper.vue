<script setup lang="ts">
import { computed } from 'vue'
import type { OrderStatus } from '@/types/order'

type StepState = 'finished' | 'current' | 'unfinished'

const props = withDefaults(
  defineProps<{
    status: OrderStatus
    closedAt?: string | null
    updatedAt?: string | null
  }>(),
  {
    closedAt: null,
    updatedAt: null,
  },
)

const STEPS = ['下单', '已支付', '完成'] as const

function formatDateTime(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 19)
}

const terminalPill = computed<{ label: string; timestamp: string } | null>(() => {
  switch (props.status) {
    case 'CANCELLED':
      return { label: '已取消', timestamp: formatDateTime(props.closedAt ?? props.updatedAt) }
    case 'CLOSED':
      return { label: '已关闭', timestamp: formatDateTime(props.closedAt ?? props.updatedAt) }
    case 'REFUNDED':
      return { label: '已退款', timestamp: formatDateTime(props.updatedAt) }
    default:
      return null
  }
})

const stepStates = computed<StepState[]>(() => {
  if (props.status === 'PENDING_PAYMENT') {
    return ['finished', 'current', 'unfinished']
  }
  if (props.status === 'PAID') {
    return ['finished', 'finished', 'finished']
  }
  return ['unfinished', 'unfinished', 'unfinished']
})

function lineState(i: number): 'finished' | 'unfinished' {
  // Line i connects step i and step i+1; finished only when both endpoints are finished
  const a = stepStates.value[i]
  const b = stepStates.value[i + 1]
  return a === 'finished' && b === 'finished' ? 'finished' : 'unfinished'
}
</script>

<template>
  <div v-if="terminalPill" class="stepper-pill" role="status">
    <span class="stepper-pill__label">{{ terminalPill.label }}</span>
    <span v-if="terminalPill.timestamp" class="stepper-pill__sep">·</span>
    <span v-if="terminalPill.timestamp" class="stepper-pill__time">{{ terminalPill.timestamp }}</span>
  </div>

  <ol v-else class="stepper" role="list" aria-label="订单进度">
    <template v-for="(label, i) in STEPS" :key="label">
      <li class="stepper__step" :class="[`stepper__step--${stepStates[i]}`]">
        <span class="stepper__dot" aria-hidden="true" />
        <span class="stepper__label">{{ label }}</span>
      </li>
      <span
        v-if="i < STEPS.length - 1"
        class="stepper__line"
        :class="[`stepper__line--${lineState(i)}`]"
        aria-hidden="true"
      />
    </template>
  </ol>
</template>

<style scoped>
.stepper {
  display: flex;
  align-items: center;
  gap: 12px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.stepper__step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 56px;
}

.stepper__dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 1.5px solid var(--ink-300);
  background: transparent;
  transition: background-color var(--dur-2) var(--ease),
    border-color var(--dur-2) var(--ease);
}

.stepper__label {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  color: var(--ink-300);
  white-space: nowrap;
  transition: color var(--dur-2) var(--ease);
}

.stepper__step--finished .stepper__dot {
  background: var(--ink-900);
  border-color: var(--ink-900);
}

.stepper__step--finished .stepper__label {
  color: var(--ink-700);
}

.stepper__step--current .stepper__dot {
  background: var(--accent-terracotta);
  border-color: var(--accent-terracotta);
  animation: stepper-pulse 1.2s ease-in-out infinite;
}

.stepper__step--current .stepper__label {
  color: var(--ink-900);
  font-weight: 600;
}

.stepper__line {
  flex: 1;
  height: 1.5px;
  border-radius: 1px;
  background: var(--ink-300);
  transition: background-color var(--dur-2) var(--ease);
  margin-bottom: 21px; /* align with dot row (8px gap + 13px label = 21px) */
}

.stepper__line--finished {
  background: var(--ink-900);
}

@keyframes stepper-pulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(181, 85, 47, 0.5);
  }
  50% {
    transform: scale(1.1);
    box-shadow: 0 0 0 4px rgba(181, 85, 47, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stepper__step--current .stepper__dot {
    animation: none;
  }
}

.stepper-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 1px solid var(--ink-300);
  border-radius: 999px;
  background: var(--canvas-darker);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-500);
  line-height: 1;
}

.stepper-pill__label {
  color: var(--ink-700);
}

.stepper-pill__sep {
  color: var(--ink-300);
}

.stepper-pill__time {
  font-family: var(--font-mono);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--ink-500);
}
</style>
