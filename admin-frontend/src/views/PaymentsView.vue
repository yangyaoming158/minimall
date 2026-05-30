<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="支付管理" description="支付单列表、状态筛选、paymentNo / orderNo 搜索、支付详情。" />
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
