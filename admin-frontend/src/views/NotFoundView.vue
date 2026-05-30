<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="center-page">
    <el-result icon="info" title="404" sub-title="页面不存在。">
      <template #extra>
        <el-button type="primary" @click="router.push('/products')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.center-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--canvas);
}
</style>
