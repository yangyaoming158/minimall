<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
</script>

<template>
  <div class="app-layout">
    <AppHeader />
    <main class="app-main">
      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </main>
    <AppFooter />
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--canvas);
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* Page transitions: outgoing fades 120ms, incoming fades + rises 220ms.
   Per docs/phase1-ui-redesign.md §5.1 and §7. */
.page-enter-active {
  transition: opacity var(--dur-2) var(--ease),
    transform var(--dur-2) var(--ease);
}

.page-leave-active {
  transition: opacity var(--dur-1) var(--ease);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
}

@media (max-width: 639px) {
  .app-main {
    padding: 24px 16px;
  }
}
</style>
