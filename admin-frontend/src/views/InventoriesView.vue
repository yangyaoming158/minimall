<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="库存管理" description="库存列表、搜索、状态筛选、初始化 / 调整库存、库存流水。" />
    <el-card shadow="never" class="placeholder">
      <el-empty description="本页将在 Task 13 实现" />
    </el-card>
  </div>
</template>

<style scoped>
.placeholder {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}
</style>
