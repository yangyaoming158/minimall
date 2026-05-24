<script setup lang="ts">
withDefaults(
  defineProps<{
    vertical?: boolean
    inset?: boolean
  }>(),
  { vertical: false, inset: false },
)
</script>

<template>
  <component
    :is="vertical ? 'span' : 'hr'"
    class="hairline"
    :class="{ 'hairline--vertical': vertical, 'hairline--inset': inset }"
    :role="vertical ? 'separator' : undefined"
    :aria-orientation="vertical ? 'vertical' : undefined"
  />
</template>

<style scoped>
.hairline {
  border: 0;
  background: var(--ink-100);
  margin: 0;
}

hr.hairline {
  height: 1px;
  width: 100%;
}

.hairline--vertical {
  display: inline-block;
  width: 1px;
  height: 1em;
  vertical-align: middle;
}

.hairline--inset {
  margin-left: var(--space-16);
  margin-right: var(--space-16);
}
</style>
