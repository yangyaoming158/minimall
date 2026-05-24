<script setup lang="ts">
import Button from '@/components/atoms/Button.vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    showAction?: boolean
    actionLabel?: string
    actionDisabled?: boolean
  }>(),
  {
    description: '',
    showAction: false,
    actionLabel: '',
    actionDisabled: false,
  },
)

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <section class="empty" role="status">
    <div class="empty__art" aria-hidden="true">
      <svg viewBox="0 0 120 120" width="120" height="120">
        <circle cx="60" cy="60" r="60" fill="var(--canvas-darker)" />
        <circle
          cx="60"
          cy="56"
          r="22"
          fill="none"
          stroke="var(--ink-300)"
          stroke-width="2"
        />
        <line
          x1="36"
          y1="84"
          x2="84"
          y2="84"
          stroke="var(--ink-300)"
          stroke-width="2"
          stroke-linecap="round"
        />
        <line
          x1="44"
          y1="92"
          x2="76"
          y2="92"
          stroke="var(--ink-300)"
          stroke-width="2"
          stroke-linecap="round"
          opacity="0.6"
        />
      </svg>
    </div>

    <h2 class="empty__title">{{ title }}</h2>
    <p v-if="description" class="empty__description">{{ description }}</p>

    <div v-if="showAction && actionLabel" class="empty__action">
      <Button
        variant="primary"
        size="md"
        :disabled="actionDisabled"
        @click="emit('action')"
      >
        {{ actionLabel }}
      </Button>
    </div>
  </section>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 64px 24px;
  gap: 16px;
}

.empty__art {
  margin-bottom: 8px;
  line-height: 0;
}

.empty__title {
  font-family: var(--font-sans);
  font-size: var(--t-h2-size);
  font-weight: var(--t-h2-weight);
  line-height: var(--t-h2-lh);
  color: var(--ink-900);
  margin: 0;
}

.empty__description {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.6;
  color: var(--ink-500);
  max-width: 360px;
  margin: 0;
}

.empty__action {
  margin-top: 8px;
}
</style>
