<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <section class="page">
    <h1>404</h1>
    <p class="hint">页面不存在。</p>
    <el-button type="primary" @click="router.push('/products')">返回商品列表</el-button>
  </section>
</template>

<style scoped>
.page {
  background: #ffffff;
  border-radius: 8px;
  padding: 48px 32px;
  text-align: center;
}

.hint {
  color: #909399;
  margin-bottom: 16px;
}
</style>
