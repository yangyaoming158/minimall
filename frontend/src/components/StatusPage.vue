<script setup lang="ts">
import { useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import Button from '@/components/atoms/Button.vue'

const props = withDefaults(
  defineProps<{
    code: string
    title: string
    body: string
    ctaLabel?: string
    ctaTo?: string
  }>(),
  {
    ctaLabel: '回到商品列表',
    ctaTo: '/products',
  },
)

const router = useRouter()

function onCta(): void {
  router.push(props.ctaTo)
}
</script>

<template>
  <section class="status">
    <p class="status__code" aria-hidden="true">{{ code }}</p>
    <h1 class="status__title">{{ title }}</h1>
    <p class="status__body">{{ body }}</p>
    <Button variant="primary" size="lg" class="status__cta" @click="onCta">
      {{ ctaLabel }}
    </Button>
    <div class="status__foot">
      <BrandMark dot size="sm" />
    </div>
  </section>
</template>

<style scoped>
.status {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 16px;
}

.status__code {
  margin: 0 0 16px;
  font-family: var(--font-display);
  font-size: var(--t-display-xl-size);
  line-height: var(--t-display-xl-lh);
  font-weight: var(--t-display-xl-weight);
  letter-spacing: var(--t-display-xl-track);
  color: var(--ink-300);
}

.status__title {
  margin: 0 0 12px;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  line-height: var(--t-h1-lh);
  font-weight: var(--t-h1-weight);
  color: var(--ink-900);
}

.status__body {
  margin: 0 0 32px;
  max-width: 36ch;
  font-family: var(--font-sans);
  font-size: var(--t-body-size);
  line-height: var(--t-body-lh);
  color: var(--ink-500);
}

.status__cta {
  min-width: 200px;
}

.status__foot {
  margin-top: 64px;
  opacity: 0.6;
}

@media (max-width: 639px) {
  .status__code {
    font-size: 64px;
  }
}
</style>
