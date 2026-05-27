<script setup lang="ts">
import HeroReel from '@/components/HeroReel.vue'

withDefaults(
  defineProps<{
    headline: string
    subtitle: string
    reelSeed?: string
  }>(),
  { reelSeed: 'login' },
)
</script>

<template>
  <aside class="hero">
    <HeroReel :seed="reelSeed" />
    <div class="hero__wash" aria-hidden="true" />
    <div class="hero__body">
      <h2 class="hero__headline">{{ headline }}</h2>
      <p class="hero__sub">{{ subtitle }}</p>
    </div>
    <span class="hero__caption">
      <span class="hero__caption-dot" aria-hidden="true" />
      In season
    </span>
  </aside>
</template>

<style scoped>
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
  padding: 48px;
  background: var(--canvas);
  overflow: hidden;
  isolation: isolate;
}

/*
 * Asymmetric wash: anchored at the lower-left (where the headline sits) and
 * fading to transparent toward the upper-right (where the reel is at full
 * presence). Replaces the prior radial "speech bubble" that visually
 * fragmented the panel into text + cards zones.
 */
.hero__wash {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(
    115deg,
    rgba(247, 246, 242, 0.88) 0%,
    rgba(247, 246, 242, 0.64) 32%,
    rgba(247, 246, 242, 0.18) 62%,
    rgba(247, 246, 242, 0) 82%
  );
  pointer-events: none;
}

.hero__body {
  position: relative;
  max-width: 480px;
  z-index: 2;
}

.hero__headline {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--t-display-xl-size);
  line-height: var(--t-display-xl-lh);
  font-weight: var(--t-display-xl-weight);
  letter-spacing: var(--t-display-xl-track);
  color: var(--ink-900);
  animation: hero-rise var(--dur-3) var(--ease) both;
}

.hero__sub {
  margin: 16px 0 0;
  font-family: var(--font-sans);
  font-size: var(--t-body-size);
  line-height: var(--t-body-lh);
  color: var(--ink-500);
  max-width: 36ch;
  animation: hero-rise var(--dur-3) var(--ease) 80ms both;
}

.hero__caption {
  position: absolute;
  right: 32px;
  bottom: 32px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: var(--t-caption-size);
  line-height: var(--t-caption-lh);
  color: var(--ink-500);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  z-index: 2;
  animation: hero-rise var(--dur-3) var(--ease) 160ms both;
}

.hero__caption-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-terracotta);
}

@keyframes hero-rise {
  from { transform: translateY(12px); opacity: 0; }
  to   { transform: translateY(0); opacity: 1; }
}

@media (max-width: 899px) {
  .hero {
    min-height: 120px;
    padding: 24px 24px 20px;
    justify-content: flex-end;
  }

  .hero__headline {
    font-size: 28px;
  }

  .hero__sub,
  .hero__caption {
    display: none;
  }

  .hero__wash {
    background: linear-gradient(
      to bottom,
      rgba(247, 246, 242, 0) 0%,
      rgba(247, 246, 242, 0.6) 60%,
      rgba(247, 246, 242, 0.92) 100%
    );
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero__headline,
  .hero__sub,
  .hero__caption {
    animation: none;
  }
}
</style>
