<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const orderNo = route.params.orderNo as string
</script>

<template>
  <section class="page">
    <h1>支付</h1>
    <p class="hint">订单号：{{ orderNo }}。占位页面，将在 Task 9 实现。</p>
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
