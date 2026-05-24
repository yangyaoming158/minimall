<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'

const year = new Date().getFullYear()
</script>

<template>
  <footer class="app-footer">
    <div class="app-footer__inner">
      <BrandMark size="sm" />
      <span class="app-footer__copy">© {{ year }} MiniMall · 演示项目</span>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  border-top: 1px solid var(--ink-100);
  background: var(--canvas);
}

.app-footer__inner {
  max-width: 1280px;
  margin: 0 auto;
  height: 80px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.app-footer__copy {
  font-family: var(--font-sans);
  font-size: var(--t-caption-size);
  color: var(--ink-500);
}

@media (max-width: 639px) {
  .app-footer__inner {
    padding: 0 16px;
  }
}
</style>
