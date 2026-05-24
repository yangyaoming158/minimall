<script setup lang="ts">
import { computed } from 'vue'
import Button from '@/components/atoms/Button.vue'

type Tone = 'error' | 'forbidden' | 'notfound'

const props = withDefaults(
  defineProps<{
    tone?: Tone
    title: string
    description?: string
    showRetry?: boolean
    retryLabel?: string
    retryLoading?: boolean
    showHome?: boolean
    homeLabel?: string
  }>(),
  {
    tone: 'error',
    description: '',
    showRetry: false,
    retryLabel: '重试',
    retryLoading: false,
    showHome: false,
    homeLabel: '返回首页',
  },
)

const emit = defineEmits<{
  retry: []
  home: []
}>()

const artClass = computed(() => `error-state__art error-state__art--${props.tone}`)
</script>

<template>
  <section class="error-state" role="alert">
    <div :class="artClass" aria-hidden="true">
      <!-- error · unclosed circle suggesting interruption -->
      <svg
        v-if="tone === 'error'"
        viewBox="0 0 120 120"
        width="120"
        height="120"
      >
        <circle cx="60" cy="60" r="60" fill="var(--accent-terracotta-soft)" />
        <path
          d="M 60 28 A 32 32 0 1 1 28 60"
          fill="none"
          stroke="var(--accent-terracotta)"
          stroke-width="2.5"
          stroke-linecap="round"
        />
        <circle cx="60" cy="60" r="3" fill="var(--accent-terracotta)" />
      </svg>

      <!-- forbidden · two offset squares -->
      <svg
        v-else-if="tone === 'forbidden'"
        viewBox="0 0 120 120"
        width="120"
        height="120"
      >
        <circle cx="60" cy="60" r="60" fill="var(--ink-100)" />
        <rect
          x="40"
          y="40"
          width="32"
          height="32"
          fill="none"
          stroke="var(--ink-500)"
          stroke-width="2"
          rx="2"
        />
        <rect
          x="50"
          y="50"
          width="32"
          height="32"
          fill="none"
          stroke="var(--ink-500)"
          stroke-width="2"
          rx="2"
          opacity="0.6"
        />
      </svg>

      <!-- notfound · arc + dot -->
      <svg
        v-else
        viewBox="0 0 120 120"
        width="120"
        height="120"
      >
        <circle cx="60" cy="60" r="60" fill="var(--canvas-darker)" />
        <path
          d="M 46 50 A 14 14 0 1 1 60 64 L 60 72"
          fill="none"
          stroke="var(--ink-700)"
          stroke-width="2.5"
          stroke-linecap="round"
        />
        <circle cx="60" cy="84" r="3" fill="var(--ink-700)" />
      </svg>
    </div>

    <h2 class="error-state__title">{{ title }}</h2>
    <p v-if="description" class="error-state__description">{{ description }}</p>

    <div v-if="showRetry || showHome" class="error-state__actions">
      <Button
        v-if="showRetry"
        variant="primary"
        size="md"
        :loading="retryLoading"
        @click="emit('retry')"
      >
        {{ retryLabel }}
      </Button>
      <Button
        v-if="showHome"
        variant="ghost"
        size="md"
        @click="emit('home')"
      >
        {{ homeLabel }}
      </Button>
    </div>
  </section>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 64px 24px;
  gap: 16px;
}

.error-state__art {
  margin-bottom: 8px;
  line-height: 0;
}

.error-state__title {
  font-family: var(--font-sans);
  font-size: var(--t-h2-size);
  font-weight: var(--t-h2-weight);
  line-height: var(--t-h2-lh);
  color: var(--ink-900);
  margin: 0;
}

.error-state__description {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.6;
  color: var(--ink-500);
  max-width: 360px;
  margin: 0;
}

.error-state__actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
