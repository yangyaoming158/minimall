<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="操作日志" description="管理员操作审计日志、管理员 / 操作类型 / 资源类型 / 时间筛选、详情。" />
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
