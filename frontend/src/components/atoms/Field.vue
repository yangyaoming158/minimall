<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label?: string
  hint?: string
  error?: string
}>()

const showError = computed(() => Boolean(props.error))
const helper = computed(() => props.error ?? props.hint ?? '')
const helperTone = computed<'error' | 'hint'>(() =>
  showError.value ? 'error' : 'hint',
)
</script>

<template>
  <div class="field" :class="{ 'field--error': showError }">
    <label v-if="label" class="field__label">{{ label }}</label>
    <div class="field__slot">
      <slot />
    </div>
    <p v-if="helper" :class="['field__helper', `field__helper--${helperTone}`]">
      {{ helper }}
    </p>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-700);
  line-height: 1;
}

.field__slot {
  display: flex;
  flex-direction: column;
}

.field__helper {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
}

.field__helper--hint {
  color: var(--ink-500);
}

.field__helper--error {
  color: var(--danger);
}
</style>
