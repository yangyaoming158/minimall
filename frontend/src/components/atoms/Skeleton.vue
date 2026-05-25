<script setup lang="ts">
import { computed } from 'vue'

type Radius = 'sm' | 'md' | 'lg' | 'xl' | 'full'

const props = withDefaults(
  defineProps<{
    width?: string
    height?: string
    radius?: Radius
    block?: boolean
  }>(),
  {
    width: '100%',
    height: '16px',
    radius: 'sm',
    block: false,
  },
)

const style = computed(() => ({
  width: props.width,
  height: props.height,
}))
</script>

<template>
  <span
    class="skeleton"
    :class="[`skeleton--r-${radius}`, { 'skeleton--block': block }]"
    :style="style"
    aria-hidden="true"
  />
</template>

<style scoped>
.skeleton {
  display: inline-block;
  background: linear-gradient(
    90deg,
    var(--ink-100) 0%,
    var(--canvas-darker) 50%,
    var(--ink-100) 100%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.6s linear infinite;
  border-radius: var(--radius-sm);
}

.skeleton--block {
  display: block;
}

.skeleton--r-sm {
  border-radius: var(--radius-sm);
}

.skeleton--r-md {
  border-radius: var(--radius-md);
}

.skeleton--r-lg {
  border-radius: var(--radius-lg);
}

.skeleton--r-xl {
  border-radius: var(--radius-xl);
}

.skeleton--r-full {
  border-radius: 999px;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
    background: var(--ink-100);
  }
}
</style>
