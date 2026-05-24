<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  total: number
  pageSize: number
  current: number
}>()

const emit = defineEmits<{
  change: [page: number]
}>()

const pageCount = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
)

// Compact list with ellipses when there are many pages. Always shows
// first + last; surfaces a sliding window of current ± 1 in the middle.
const pages = computed<(number | '…')[]>(() => {
  const n = pageCount.value
  const c = props.current
  if (n <= 7) return Array.from({ length: n }, (_, i) => i + 1)

  const out: (number | '…')[] = [1]
  const left = Math.max(2, c - 1)
  const right = Math.min(n - 1, c + 1)

  if (left > 2) out.push('…')
  for (let i = left; i <= right; i++) out.push(i)
  if (right < n - 1) out.push('…')
  out.push(n)
  return out
})

const canPrev = computed(() => props.current > 1)
const canNext = computed(() => props.current < pageCount.value)

function go(page: number): void {
  if (page === props.current) return
  if (page < 1 || page > pageCount.value) return
  emit('change', page)
}

function prev(): void {
  if (canPrev.value) go(props.current - 1)
}

function next(): void {
  if (canNext.value) go(props.current + 1)
}
</script>

<template>
  <nav v-if="pageCount > 1" class="pager" aria-label="Pagination">
    <button
      type="button"
      class="pager__nav"
      :disabled="!canPrev"
      @click="prev"
    >
      ← Previous
    </button>

    <ul class="pager__pages">
      <li v-for="(p, i) in pages" :key="i" class="pager__item">
        <button
          v-if="p !== '…'"
          type="button"
          class="pager__page"
          :class="{ 'pager__page--current': p === current }"
          :aria-current="p === current ? 'page' : undefined"
          @click="go(p)"
        >
          {{ p }}
        </button>
        <span v-else class="pager__ellipsis" aria-hidden="true">…</span>
      </li>
    </ul>

    <button
      type="button"
      class="pager__nav"
      :disabled="!canNext"
      @click="next"
    >
      Next →
    </button>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  gap: 16px;
  font-family: var(--font-sans);
}

.pager__nav {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-700);
  padding: 6px 4px;
  transition: color var(--dur-2) var(--ease);
}

.pager__nav:hover:not(:disabled) {
  color: var(--ink-900);
}

.pager__nav:disabled {
  color: var(--ink-300);
  cursor: not-allowed;
}

.pager__pages {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.pager__page {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-500);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;
  transition: background-color var(--dur-2) var(--ease),
    color var(--dur-2) var(--ease);
}

.pager__page:hover:not(.pager__page--current) {
  color: var(--ink-900);
  background: var(--ink-100);
}

.pager__page--current {
  color: var(--surface);
  background: var(--ink-900);
  cursor: default;
}

.pager__ellipsis {
  min-width: 24px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-300);
  font-size: 13px;
}
</style>
