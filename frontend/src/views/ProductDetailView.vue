<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const productId = route.params.productId as string
</script>

<template>
  <section class="page">
    <h1>商品详情</h1>
    <p class="hint">商品 ID：{{ productId }}。占位页面，将在 Task 6 实现。</p>
  </section>
</template>

<style scoped>
.page {
  background: #ffffff;
  border-radius: 8px;
  padding: 32px;
}

.hint {
  color: #909399;
}
</style>
