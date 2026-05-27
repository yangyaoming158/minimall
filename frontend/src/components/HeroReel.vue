<script setup lang="ts">
import { computed } from 'vue'
import ProductCover from '@/components/ProductCover.vue'

const props = withDefaults(
  defineProps<{ seed?: string }>(),
  { seed: 'login' },
)

const columnA = computed(() => buildColumn(props.seed, 'a'))
const columnB = computed(() => buildColumn(props.seed, 'b'))

function buildColumn(seed: string, suffix: string): string[] {
  return [1, 2, 3, 4].map((i) => `hero-${seed}-${suffix}${i}`)
}
</script>

<template>
  <div class="reel" aria-hidden="true">
    <div class="reel__col reel__col--a">
      <div class="reel__track">
        <div
          v-for="(id, i) in [...columnA, ...columnA]"
          :key="`a-${i}`"
          class="reel__card"
        >
          <ProductCover :productId="id" aspect="4:5" size="full" />
        </div>
      </div>
    </div>
    <div class="reel__col reel__col--b">
      <div class="reel__track">
        <div
          v-for="(id, i) in [...columnB, ...columnB]"
          :key="`b-${i}`"
          class="reel__card"
        >
          <ProductCover :productId="id" aspect="4:5" size="full" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reel {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 24px;
  z-index: 0;
  pointer-events: none;
  opacity: 0;
  -webkit-mask-image: linear-gradient(
    to bottom,
    transparent 0%,
    #000 22%,
    #000 78%,
    transparent 100%
  );
          mask-image: linear-gradient(
    to bottom,
    transparent 0%,
    #000 22%,
    #000 78%,
    transparent 100%
  );
  animation: reel-in 700ms linear 200ms forwards;
}

@keyframes reel-in {
  to { opacity: 0.42; }
}

.reel__col {
  position: relative;
  overflow: hidden;
}

.reel__track {
  display: flex;
  flex-direction: column;
  will-change: transform;
  animation: drift 56s linear infinite;
}

.reel__col--b .reel__track {
  animation-direction: reverse;
}

@keyframes drift {
  from { transform: translate3d(0, 0, 0); }
  to   { transform: translate3d(0, -50%, 0); }
}

.reel__card {
  aspect-ratio: 4 / 5;
  margin-bottom: 8px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.reel__card :deep(.cover) {
  width: 100%;
  height: 100%;
  border-radius: 0;
  background: transparent;
}

/* Reel cards are decorative — suppress the initial-mark text from ProductCover. */
.reel__card :deep(text) {
  display: none;
}

@media (max-width: 899px) {
  .reel { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .reel { animation: none; opacity: 0.4; }
  .reel__track { animation: none; }
}
</style>
