<script setup lang="ts">
type Option = { value: string; label: string }

const props = defineProps<{
  options: Option[]
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function select(v: string): void {
  if (v === props.modelValue) return
  emit('update:modelValue', v)
}
</script>

<template>
  <div class="pill-group" role="tablist">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="pill-group__pill"
      :class="{ 'pill-group__pill--active': opt.value === modelValue }"
      role="tab"
      :aria-selected="opt.value === modelValue"
      @click="select(opt.value)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

<style scoped>
.pill-group {
  display: inline-flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pill-group__pill {
  padding: 8px 16px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  color: var(--ink-700);
  border: 1px solid var(--ink-300);
  border-radius: 999px;
  background: transparent;
  transition: background-color var(--dur-2) var(--ease),
    border-color var(--dur-2) var(--ease),
    color var(--dur-2) var(--ease);
}

.pill-group__pill:hover:not(.pill-group__pill--active) {
  border-color: var(--ink-700);
  color: var(--ink-900);
  background: var(--ink-100);
}

.pill-group__pill--active {
  background: var(--ink-900);
  color: var(--surface);
  border-color: var(--ink-900);
  cursor: default;
}
</style>
