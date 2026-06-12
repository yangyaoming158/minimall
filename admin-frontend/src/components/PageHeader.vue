<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="text">
      <h2 class="title">{{ title }}</h2>
      <p v-if="description" class="desc">{{ description }}</p>
    </div>
    <div class="actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-16);
  margin-bottom: var(--space-20);
}

.title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--text-strong);
}

.desc {
  margin: var(--space-4) 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.actions {
  display: flex;
  gap: var(--space-8);
  flex-shrink: 0;
}
</style>
