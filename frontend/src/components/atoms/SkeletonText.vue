<script setup lang="ts">
import { computed } from 'vue'
import Skeleton from './Skeleton.vue'

const props = withDefaults(
  defineProps<{
    lines?: number
    lineHeight?: string
    gap?: string
  }>(),
  {
    lines: 3,
    lineHeight: '12px',
    gap: '10px',
  },
)

// Final line is shorter (60%) for a natural ragged-right edge.
const widths = computed(() =>
  Array.from({ length: props.lines }, (_, i) =>
    i === props.lines - 1 && props.lines > 1 ? '60%' : '100%',
  ),
)

const containerStyle = computed(() => ({ rowGap: props.gap }))
</script>

<template>
  <div class="sktext" :style="containerStyle">
    <Skeleton
      v-for="(w, i) in widths"
      :key="i"
      :width="w"
      :height="lineHeight"
      block
    />
  </div>
</template>

<style scoped>
.sktext {
  display: flex;
  flex-direction: column;
}
</style>
