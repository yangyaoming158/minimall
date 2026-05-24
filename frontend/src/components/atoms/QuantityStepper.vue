<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: number
    min?: number
    max?: number
    step?: number
    disabled?: boolean
  }>(),
  {
    min: 1,
    max: 999,
    step: 1,
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

// Integer-only clamp. The view contracts that consume this component
// (ProductDetailView, CheckoutView via query) treat quantity as an
// integer count of units — fractional values are never valid.
function clamp(n: number): number {
  return Math.max(props.min, Math.min(props.max, Math.floor(n)))
}

const canDec = computed(() => !props.disabled && props.modelValue > props.min)
const canInc = computed(() => !props.disabled && props.modelValue < props.max)

function decrement(): void {
  if (!canDec.value) return
  const next = clamp(props.modelValue - props.step)
  if (next !== props.modelValue) emit('update:modelValue', next)
}

function increment(): void {
  if (!canInc.value) return
  const next = clamp(props.modelValue + props.step)
  if (next !== props.modelValue) emit('update:modelValue', next)
}

function onInput(e: Event): void {
  const raw = (e.target as HTMLInputElement).value
  // Defer empty-state handling to blur so the user can transiently clear
  // the field while typing without us slamming it back to min mid-edit.
  if (raw === '') return
  const parsed = parseInt(raw, 10)
  if (!Number.isFinite(parsed)) return
  const next = clamp(parsed)
  if (next !== props.modelValue) emit('update:modelValue', next)
}

function onBlur(e: Event): void {
  const raw = (e.target as HTMLInputElement).value
  if (raw === '' || !Number.isFinite(parseInt(raw, 10))) {
    emit('update:modelValue', clamp(props.min))
    return
  }
  const next = clamp(parseInt(raw, 10))
  if (next !== props.modelValue) emit('update:modelValue', next)
}
</script>

<template>
  <div class="qty" :class="{ 'qty--disabled': disabled }">
    <button
      type="button"
      class="qty__btn qty__btn--dec"
      :disabled="!canDec"
      aria-label="Decrease quantity"
      @click="decrement"
    >
      <span aria-hidden="true">−</span>
    </button>
    <input
      type="number"
      class="qty__input"
      inputmode="numeric"
      :value="modelValue"
      :min="min"
      :max="max"
      :step="step"
      :disabled="disabled"
      aria-label="Quantity"
      @input="onInput"
      @blur="onBlur"
    />
    <button
      type="button"
      class="qty__btn qty__btn--inc"
      :disabled="!canInc"
      aria-label="Increase quantity"
      @click="increment"
    >
      <span aria-hidden="true">+</span>
    </button>
  </div>
</template>

<style scoped>
.qty {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--ink-300);
  border-radius: var(--radius);
  background: var(--surface);
  height: 40px;
  overflow: hidden;
  transition: border-color var(--dur-2) var(--ease);
}

.qty:hover:not(.qty--disabled) {
  border-color: var(--ink-700);
}

.qty--disabled {
  background: var(--ink-100);
  border-color: var(--ink-100);
}

.qty__btn {
  width: 40px;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-900);
  font-size: 18px;
  line-height: 1;
  transition: background-color var(--dur-2) var(--ease),
    color var(--dur-2) var(--ease);
}

.qty__btn:hover:not(:disabled) {
  background: var(--ink-100);
}

.qty__btn:active:not(:disabled) {
  background: var(--ink-300);
}

.qty__btn:disabled {
  color: var(--ink-300);
  cursor: not-allowed;
}

.qty__input {
  width: 56px;
  height: 100%;
  border: 0;
  background: transparent;
  text-align: center;
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 500;
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;
  -moz-appearance: textfield;
  appearance: textfield;
  outline: none;
}

.qty__input::-webkit-outer-spin-button,
.qty__input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.qty__input:disabled {
  color: var(--ink-300);
}
</style>
