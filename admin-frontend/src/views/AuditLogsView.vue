<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listAuditLogs } from '@/api/auditLogs'
import type {
  AdminAuditAction,
  AdminAuditLog,
  AdminAuditResourceType,
} from '@/types/audit'

const PAGE_SIZE = 10

const logs = ref<AdminAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const filters = reactive<{
  adminUserId: string
  action: '' | AdminAuditAction
  resourceType: '' | AdminAuditResourceType
  resourceId: string
  requestId: string
  createdRange: [string, string] | null
}>({
  adminUserId: '',
  action: '',
  resourceType: '',
  resourceId: '',
  requestId: '',
  createdRange: null,
})

const ACTION_LABEL: Record<AdminAuditAction, string> = {
  PRODUCT_CREATE: '创建商品',
  PRODUCT_UPDATE: '更新商品',
  PRODUCT_ON_SHELF: '商品上架',
  PRODUCT_OFF_SHELF: '商品下架',
  INVENTORY_INITIALIZE: '初始化库存',
  INVENTORY_ADJUST: '调整库存',
  INBOUND_ORDER_CREATE: '创建入库单',
  INBOUND_ORDER_CONFIRM: '确认入库单',
  AI_SUGGESTION_CREATE: '生成 AI 建议',
  AI_SUGGESTION_APPLY: '采纳 AI 建议',
  AI_SUGGESTION_REJECT: '驳回 AI 建议',
}

const RESOURCE_LABEL: Record<AdminAuditResourceType, string> = {
  PRODUCT: '商品',
  INVENTORY: '库存',
  INVENTORY_RECORD: '库存流水',
  ORDER: '订单',
  PAYMENT: '支付',
  NOTIFICATION: '通知',
  ADMIN_USER: '管理员',
  INBOUND_ORDER: '入库单',
  AI_SUGGESTION: 'AI 建议',
}

const ACTION_OPTIONS: { value: '' | AdminAuditAction; label: string }[] = [
  { value: '', label: '全部操作' },
  ...(Object.keys(ACTION_LABEL) as AdminAuditAction[]).map((v) => ({ value: v, label: ACTION_LABEL[v] })),
]

const RESOURCE_OPTIONS: { value: '' | AdminAuditResourceType; label: string }[] = [
  { value: '', label: '全部资源' },
  ...(Object.keys(RESOURCE_LABEL) as AdminAuditResourceType[]).map((v) => ({ value: v, label: RESOURCE_LABEL[v] })),
]

// Detail drawer renders the row (audit logs are list-only, no detail endpoint).
const detailVisible = ref(false)
const detail = ref<AdminAuditLog | null>(null)

function fmtJson(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

async function fetchLogs(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const adminUserId = filters.adminUserId.trim()
    const res = await listAuditLogs({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(adminUserId && !Number.isNaN(Number(adminUserId)) ? { adminUserId: Number(adminUserId) } : {}),
      ...(filters.action ? { action: filters.action } : {}),
      ...(filters.resourceType ? { resourceType: filters.resourceType } : {}),
      ...(filters.resourceId.trim() ? { resourceId: filters.resourceId.trim() } : {}),
      ...(filters.requestId.trim() ? { requestId: filters.requestId.trim() } : {}),
      ...(filters.createdRange
        ? { createdFrom: filters.createdRange[0], createdTo: filters.createdRange[1] }
        : {}),
    })
    logs.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchLogs()
}

function onReset(): void {
  filters.adminUserId = ''
  filters.action = ''
  filters.resourceType = ''
  filters.resourceId = ''
  filters.requestId = ''
  filters.createdRange = null
  onSearch()
}

function onPageChange(next: number): void {
  page.value = next
  fetchLogs()
}

function openDetail(row: AdminAuditLog): void {
  detail.value = row
  detailVisible.value = true
}

onMounted(fetchLogs)
</script>

<template>
  <div>
    <PageHeader title="操作日志" description="管理员操作审计日志、操作类型 / 资源 / 管理员 / 时间筛选、可追溯字段与前后快照详情。" />

    <div class="filter-bar">
      <el-input v-model="filters.adminUserId" placeholder="管理员 ID" clearable style="width: 140px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="filters.action" placeholder="操作类型" style="width: 150px" @change="onSearch">
        <el-option v-for="opt in ACTION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filters.resourceType" placeholder="资源类型" style="width: 140px" @change="onSearch">
        <el-option v-for="opt in RESOURCE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input v-model="filters.resourceId" placeholder="资源 ID" clearable style="width: 150px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-input v-model="filters.requestId" placeholder="requestId" clearable style="width: 180px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-date-picker
        v-model="filters.createdRange"
        type="datetimerange"
        value-format="YYYY-MM-DD[T]HH:mm:ss"
        range-separator="至"
        start-placeholder="时间起"
        end-placeholder="时间止"
        style="width: 360px"
        @change="onSearch"
      />
      <el-button type="primary" plain @click="onSearch">搜索</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="logs" class="table" empty-text="暂无操作日志">
      <el-table-column prop="createdAt" label="时间" min-width="180" />
      <el-table-column label="管理员" min-width="140">
        <template #default="{ row }">{{ row.adminUsername }}<span class="muted"> #{{ row.adminUserId }}</span></template>
      </el-table-column>
      <el-table-column label="操作类型" width="130">
        <template #default="{ row }">
          <StatusTag :value="row.action" :label="ACTION_LABEL[row.action as AdminAuditAction]" tone="info" />
        </template>
      </el-table-column>
      <el-table-column label="资源" min-width="160">
        <template #default="{ row }">
          {{ RESOURCE_LABEL[row.resourceType as AdminAuditResourceType] }}
          <span v-if="row.resourceId" class="muted">/ {{ row.resourceId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="160" />
      <el-table-column label="参考单号" min-width="120">
        <template #default="{ row }">{{ row.referenceNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="requestId" min-width="160" show-overflow-tooltip>
        <template #default="{ row }"><span class="mono">{{ row.requestId || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="errored" class="error-hint">加载失败，请稍后重试。</div>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <el-drawer v-model="detailVisible" title="操作日志详情" size="520px">
      <div v-if="detail" class="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="摘要">{{ detail.summary }}</el-descriptions-item>
          <el-descriptions-item label="管理员">{{ detail.adminUsername }} #{{ detail.adminUserId }}</el-descriptions-item>
          <el-descriptions-item label="操作">
            <StatusTag :value="detail.action" :label="ACTION_LABEL[detail.action]" tone="info" />
          </el-descriptions-item>
          <el-descriptions-item label="资源">
            {{ RESOURCE_LABEL[detail.resourceType] }}<span v-if="detail.resourceId"> / {{ detail.resourceId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="参考单号">{{ detail.referenceNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="requestId"><span class="mono">{{ detail.requestId || '—' }}</span></el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.ip || '—' }}</el-descriptions-item>
          <el-descriptions-item label="User-Agent">{{ detail.userAgent || '—' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ detail.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">变更前快照</h4>
        <pre class="snapshot">{{ fmtJson(detail.beforeSnapshot) }}</pre>
        <h4 class="section-title">变更后快照</h4>
        <pre class="snapshot">{{ fmtJson(detail.afterSnapshot) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-12);
  margin-bottom: var(--space-16);
}

.table {
  width: 100%;
}

.muted {
  color: var(--text-faint);
}

.mono {
  font-family: var(--font-mono, monospace);
}

.error-hint {
  margin-top: var(--space-12);
  color: var(--danger);
  font-size: var(--text-sm);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-16);
}

.detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-12);
}

.section-title {
  margin: var(--space-8) 0 0;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.snapshot {
  margin: 0;
  padding: var(--space-12);
  background: var(--neutral-soft, #f5f5f5);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono, monospace);
  font-size: var(--text-xs);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
