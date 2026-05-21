<script setup lang="ts">
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
</script>

<template>
  <main class="app-shell">
    <section class="card">
      <h1>MiniMall</h1>
      <p>Customer storefront scaffold is ready.</p>
      <p class="hint">API base URL: {{ apiBaseUrl }}</p>
    </section>
  </main>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6f8;
}

.card {
  padding: 32px 48px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  text-align: center;
}

.hint {
  color: #909399;
  font-size: 13px;
}
</style>
