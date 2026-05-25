<script setup lang="ts">
defineProps<{
  headline: string
  subtitle: string
}>()
</script>

<template>
  <aside class="hero">
    <div class="hero__body">
      <span class="hero__blob" aria-hidden="true" />
      <h2 class="hero__headline">{{ headline }}</h2>
      <p class="hero__sub">{{ subtitle }}</p>
    </div>
  </aside>
</template>

<style scoped>
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
  padding: 48px 48px;
  background: var(--canvas);
  overflow: hidden;
}

.hero__body {
  position: relative;
  max-width: 480px;
}

.hero__blob {
  position: absolute;
  left: -80px;
  top: -60px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: var(--canvas-darker);
  filter: blur(60px);
  z-index: 0;
}

.hero__headline {
  position: relative;
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--t-display-xl-size);
  line-height: var(--t-display-xl-lh);
  font-weight: var(--t-display-xl-weight);
  letter-spacing: var(--t-display-xl-track);
  color: var(--ink-900);
  z-index: 1;
}

.hero__sub {
  position: relative;
  margin: 16px 0 0;
  font-family: var(--font-sans);
  font-size: var(--t-body-size);
  line-height: var(--t-body-lh);
  color: var(--ink-500);
  max-width: 36ch;
  z-index: 1;
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

  .hero__sub {
    display: none;
  }

  .hero__blob {
    width: 200px;
    height: 200px;
    left: -40px;
    top: -40px;
  }
}
</style>
