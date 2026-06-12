<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const props = defineProps<{
  /** Backend enum name, e.g. PAID, OFF_SHELF, FAILED. */
  value: string
  /** Optional display label; defaults to the raw value. */
  label?: string
  /** Optional explicit tone; otherwise inferred from a built-in map. */
  tone?: Tone
}>()

// Default tones for the Phase 2 status enums shared across pages. Pages may
// override via the `tone` prop when a status needs a different emphasis.
const TONE_BY_VALUE: Record<string, Tone> = {
  // product
  ON_SHELF: 'success',
  OFF_SHELF: 'neutral',
  // order
  PENDING_PAYMENT: 'warning',
  PAID: 'success',
  CANCELLED: 'danger',
  CLOSED: 'neutral',
  // payment / notification
  PENDING: 'warning',
  SUCCESS: 'success',
  SENT: 'success',
  FAILED: 'danger',
  // inventory
  IN_STOCK: 'success',
  OUT_OF_STOCK: 'warning',
  ACTIVE: 'success',
  INACTIVE: 'neutral',
}

const resolvedTone = computed<Tone>(() => props.tone ?? TONE_BY_VALUE[props.value] ?? 'neutral')
const text = computed(() => props.label ?? props.value)
</script>

<template>
  <span class="status-tag" :class="`tone-${resolvedTone}`">{{ text }}</span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.5;
  white-space: nowrap;
}

.tone-success {
  color: var(--success);
  background: var(--success-soft);
}
.tone-warning {
  color: var(--warning);
  background: var(--warning-soft);
}
.tone-danger {
  color: var(--danger);
  background: var(--danger-soft);
}
.tone-info {
  color: var(--info);
  background: var(--info-soft);
}
.tone-neutral {
  color: var(--neutral);
  background: var(--neutral-soft);
}
</style>
