<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="商品管理" description="商品列表、搜索、状态筛选、新增 / 编辑、上架 / 下架。" />
    <el-card shadow="never" class="placeholder">
      <el-empty description="本页将在 Task 12 实现" />
    </el-card>
  </div>
</template>

<style scoped>
.placeholder {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}
</style>
