<script setup lang="ts">
import StatusTag from '@/components/StatusTag.vue'
import type { AiAnalysisResponse, AiSuggestionRiskLevel } from '@/types/ai'

defineProps<{
  result: AiAnalysisResponse
}>()

const RISK_LABEL: Record<AiSuggestionRiskLevel, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

const RISK_TONE: Record<AiSuggestionRiskLevel, 'success' | 'warning' | 'danger'> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
}

function formatRange(from: string | null, to: string | null): string {
  if (!from && !to) {
    return '未提供'
  }
  return `${from ?? '—'} ~ ${to ?? '—'}`
}
</script>

<template>
  <div class="analysis-result">
    <div class="meta">
      <span class="meta-item">分析时间：{{ result.queryTime }}</span>
      <span v-if="result.evidence" class="meta-item">
        数据时间范围：{{ formatRange(result.evidence.dataFrom, result.evidence.dataTo) }}（近 {{ result.evidence.days }} 天销量）
      </span>
    </div>

    <p class="summary">{{ result.summary }}</p>

    <template v-if="result.items.length > 0">
      <h4 class="section-title">分析明细</h4>
      <el-table :data="result.items" size="small" empty-text="无明细">
        <el-table-column prop="productId" label="商品 ID" min-width="140" show-overflow-tooltip />
        <el-table-column label="可用" width="70" align="right">
          <template #default="{ row }">{{ row.availableStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="安全" width="70" align="right">
          <template #default="{ row }">{{ row.safetyStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="近 7 天销量" width="100" align="right">
          <template #default="{ row }">{{ row.soldQuantityLast7Days ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="风险" width="80">
          <template #default="{ row }">
            <StatusTag
              v-if="row.riskLevel"
              :value="row.riskLevel"
              :label="RISK_LABEL[row.riskLevel as AiSuggestionRiskLevel]"
              :tone="RISK_TONE[row.riskLevel as AiSuggestionRiskLevel]"
            />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason ?? '—' }}</template>
        </el-table-column>
      </el-table>
    </template>

    <template v-if="result.evidence && result.evidence.products.length > 0">
      <h4 class="section-title">结构化证据（后端只读数据）</h4>
      <el-table :data="result.evidence.products" size="small" empty-text="无证据数据">
        <el-table-column label="#" width="50" align="right">
          <template #default="{ row }">{{ row.rank }}</template>
        </el-table-column>
        <el-table-column prop="productId" label="商品 ID" min-width="140" show-overflow-tooltip />
        <el-table-column label="销量" width="80" align="right">
          <template #default="{ row }">{{ row.sales?.soldQuantity ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="订单数" width="80" align="right">
          <template #default="{ row }">{{ row.sales?.orderCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="可用库存" width="90" align="right">
          <template #default="{ row }">{{ row.inventory?.availableStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="安全库存" width="90" align="right">
          <template #default="{ row }">{{ row.inventory?.safetyStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="低库存" width="80">
          <template #default="{ row }">
            <StatusTag v-if="row.inventory?.lowStock" value="LOW_STOCK" label="低库存" tone="warning" />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <div v-if="result.limitations.length > 0" class="limitations">
      <h4 class="section-title">数据局限</h4>
      <ul>
        <li v-for="(item, index) in result.limitations" :key="index">{{ item }}</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.analysis-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-12);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-16);
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.summary {
  margin: 0;
  padding: var(--space-12) var(--space-16);
  background: var(--surface-muted, #f5f7fa);
  border-radius: var(--radius);
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
}

.section-title {
  margin: var(--space-4) 0 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-strong);
}

.muted {
  color: var(--text-faint);
}

.limitations ul {
  margin: var(--space-4) 0 0;
  padding-left: var(--space-20);
  color: var(--text-muted);
  font-size: var(--text-sm);
}
</style>
