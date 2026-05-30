<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="订单管理" description="订单列表、状态 / 用户 / 时间筛选、订单详情、事件时间线。" />
    <el-card shadow="never" class="placeholder">
      <el-empty description="本页将在 Task 14 实现" />
    </el-card>
  </div>
</template>

<style scoped>
.placeholder {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}
</style>
