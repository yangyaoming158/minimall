<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import InventoryInitDialog from '@/components/InventoryInitDialog.vue'
import InventoryAdjustDialog, {
  type AdjustFormPayload,
} from '@/components/InventoryAdjustDialog.vue'
import {
  adjustInventory,
  getInventory,
  getInventoryRecords,
  initializeInventory,
  listInventories,
} from '@/api/inventories'
import { ApiError } from '@/types/api'
import type {
  AdminInventory,
  InitializeInventoryRequest,
  InventoryChangeType,
  InventoryRecord,
  InventoryRecordSourceType,
  InventoryStatus,
  StockState,
} from '@/types/inventory'

const PAGE_SIZE = 10

const inventories = ref<AdminInventory[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const filters = reactive<{ keyword: string; stockState: '' | StockState; lowStock: boolean }>({
  keyword: '',
  stockState: '',
  lowStock: false,
})

const STOCK_STATE_OPTIONS: { value: '' | StockState; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'IN_STOCK', label: '有货' },
  { value: 'OUT_OF_STOCK', label: '缺货' },
  { value: 'INACTIVE', label: '停用' },
]

const STOCK_STATE_LABEL: Record<StockState, string> = {
  IN_STOCK: '有货',
  OUT_OF_STOCK: '缺货',
  INACTIVE: '停用',
}

const STATUS_LABEL: Record<InventoryStatus, string> = {
  ACTIVE: '启用',
  INACTIVE: '停用',
}

const CHANGE_TYPE_LABEL: Record<InventoryChangeType, string> = {
  DEDUCT: '扣减',
  RELEASE: '释放',
  ADJUST_INCREASE: '调增',
  ADJUST_DECREASE: '调减',
}

const SOURCE_TYPE_LABEL: Record<InventoryRecordSourceType, string> = {
  ORDER_DEDUCT: '订单扣减',
  ORDER_RELEASE: '订单释放',
  ADMIN_INITIALIZE: '管理员初始化',
  ADMIN_ADJUSTMENT: '管理员调整',
  INBOUND_ORDER: '入库单',
  AI_SUGGESTION: 'AI 建议',
}

// quantity is a positive magnitude; direction comes from changeType.
function isIncrease(record: InventoryRecord): boolean {
  return record.changeType === 'ADJUST_INCREASE' || record.changeType === 'RELEASE'
}

function signedQuantity(record: InventoryRecord): string {
  return `${isIncrease(record) ? '+' : '-'}${record.quantity}`
}

// Detail drawer state.
const detailVisible = ref(false)
const detail = ref<AdminInventory | null>(null)
const detailLoading = ref(false)

// Records drawer state.
const recordsVisible = ref(false)
const recordsTarget = ref<string | null>(null)
const records = ref<InventoryRecord[]>([])
const recordsLoading = ref(false)
const recordsErrored = ref(false)

// Initialize / adjust dialog state.
const initVisible = ref(false)
const initSubmitting = ref(false)
const adjustVisible = ref(false)
const adjustSubmitting = ref(false)
const adjustTarget = ref<string | null>(null)

function genRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

// The http interceptor only toasts 401/429/500. Surface other business errors
// (e.g. 40900 conflict / insufficient stock, 40000/40400) explicitly; skip
// transport-less errors that the interceptor already toasted.
function surfaceBusinessError(err: unknown): void {
  if (err instanceof ApiError && err.httpStatus != null && ![401, 429, 500].includes(err.httpStatus)) {
    ElMessage.error(err.message)
  }
}

async function fetchInventories(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listInventories({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(filters.keyword.trim() ? { keyword: filters.keyword.trim() } : {}),
      ...(filters.stockState ? { stockState: filters.stockState } : {}),
      ...(filters.lowStock ? { lowStock: true } : {}),
    })
    inventories.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    inventories.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchInventories()
}

function onPageChange(next: number): void {
  page.value = next
  fetchInventories()
}

// Re-fetch the single row so the drawer always reflects the latest figures even
// if the list page is stale; fall back to row data if the detail call fails.
async function openDetail(row: AdminInventory): Promise<void> {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getInventory(row.productId)
  } catch {
    /* surfaced by the http interceptor; keep the row snapshot */
  } finally {
    detailLoading.value = false
  }
}

async function openRecords(row: AdminInventory): Promise<void> {
  recordsTarget.value = row.productId
  recordsVisible.value = true
  records.value = []
  recordsLoading.value = true
  recordsErrored.value = false
  try {
    records.value = await getInventoryRecords(row.productId)
  } catch {
    recordsErrored.value = true
  } finally {
    recordsLoading.value = false
  }
}

function openInit(): void {
  initVisible.value = true
}

async function onInitSubmit(payload: InitializeInventoryRequest): Promise<void> {
  if (initSubmitting.value) {
    return
  }
  initSubmitting.value = true
  try {
    await initializeInventory(payload)
    ElMessage.success('库存已初始化')
    initVisible.value = false
    await fetchInventories()
  } catch (err) {
    surfaceBusinessError(err) // keep the dialog open so the admin can adjust input
  } finally {
    initSubmitting.value = false
  }
}

function openAdjust(row: AdminInventory): void {
  adjustTarget.value = row.productId
  adjustVisible.value = true
}

async function onAdjustSubmit(payload: AdjustFormPayload): Promise<void> {
  if (adjustSubmitting.value || !adjustTarget.value) {
    return // in-flight guard: a re-fired submit must not double-apply
  }
  adjustSubmitting.value = true
  // Fresh idempotency key per submit attempt: a genuine retry is a new op, while
  // an accidental double-fire is blocked by the in-flight guard above.
  const requestId = genRequestId()
  try {
    await adjustInventory(adjustTarget.value, { ...payload, requestId })
    ElMessage.success('库存已调整')
    adjustVisible.value = false
    await fetchInventories()
  } catch (err) {
    surfaceBusinessError(err) // e.g. 40900 insufficient stock — keep dialog open
  } finally {
    adjustSubmitting.value = false
  }
}

onMounted(fetchInventories)
</script>

<template>
  <div>
    <PageHeader title="库存管理" description="库存列表、搜索、库存状态与低库存筛选、初始化 / 调整库存、库存流水。">
      <template #actions>
        <el-button type="primary" @click="openInit">初始化库存</el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="按商品 ID 搜索"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select
        v-model="filters.stockState"
        placeholder="库存状态"
        style="width: 140px"
        @change="onSearch"
      >
        <el-option v-for="opt in STOCK_STATE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <span class="low-stock-toggle">
        <span class="low-stock-label">仅看低库存</span>
        <el-switch v-model="filters.lowStock" @change="onSearch" />
      </span>
      <el-button type="primary" plain @click="onSearch">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="inventories" class="table" empty-text="暂无库存记录">
      <el-table-column prop="productId" label="商品 ID" min-width="160" show-overflow-tooltip />
      <el-table-column label="可用库存" width="110" align="right">
        <template #default="{ row }">{{ row.availableStock }}</template>
      </el-table-column>
      <el-table-column label="锁定库存" width="110" align="right">
        <template #default="{ row }">{{ row.lockedStock }}</template>
      </el-table-column>
      <el-table-column label="安全库存" width="110" align="right">
        <template #default="{ row }">{{ row.safetyStock }}</template>
      </el-table-column>
      <el-table-column label="库存健康" width="110">
        <template #default="{ row }">
          <StatusTag v-if="row.lowStock" value="LOW_STOCK" label="低库存" tone="warning" />
          <span v-else class="muted">正常</span>
        </template>
      </el-table-column>
      <el-table-column label="库存状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.stockState" :label="STOCK_STATE_LABEL[row.stockState as StockState]" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="STATUS_LABEL[row.status as InventoryStatus]" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openAdjust(row)">调整</el-button>
          <el-button link type="primary" @click="openRecords(row)">流水</el-button>
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

    <el-drawer v-model="detailVisible" title="库存详情" size="420px">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="商品 ID">{{ detail.productId }}</el-descriptions-item>
          <el-descriptions-item label="可用库存">{{ detail.availableStock }}</el-descriptions-item>
          <el-descriptions-item label="锁定库存">{{ detail.lockedStock }}</el-descriptions-item>
          <el-descriptions-item label="安全库存">{{ detail.safetyStock }}</el-descriptions-item>
          <el-descriptions-item label="库存健康">
            <StatusTag v-if="detail.lowStock" value="LOW_STOCK" label="低库存" tone="warning" />
            <span v-else class="muted">正常</span>
          </el-descriptions-item>
          <el-descriptions-item label="库存状态">
            <StatusTag :value="detail.stockState" :label="STOCK_STATE_LABEL[detail.stockState]" />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="STATUS_LABEL[detail.status]" />
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <el-drawer v-model="recordsVisible" size="480px">
      <template #header>
        <div class="records-header">
          <span class="records-title">库存流水</span>
          <span v-if="recordsTarget" class="records-sub">{{ recordsTarget }}</span>
          <!-- 预留：后续 AI 补货建议入口（Phase 2 不实现 AI UI） -->
        </div>
      </template>
      <div v-loading="recordsLoading" class="records-body">
        <div v-if="recordsErrored" class="error-hint">加载流水失败，请稍后重试。</div>
        <el-empty v-else-if="!recordsLoading && records.length === 0" description="暂无库存流水" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="r in records"
            :key="r.id"
            :timestamp="r.createdAt"
            placement="top"
            :type="isIncrease(r) ? 'success' : 'danger'"
          >
            <div class="rec-head">
              <StatusTag
                :value="r.changeType"
                :label="CHANGE_TYPE_LABEL[r.changeType]"
                :tone="isIncrease(r) ? 'success' : 'danger'"
              />
              <span class="rec-qty" :class="isIncrease(r) ? 'up' : 'down'">{{ signedQuantity(r) }}</span>
              <StatusTag :value="r.sourceType" :label="SOURCE_TYPE_LABEL[r.sourceType]" tone="info" />
            </div>
            <dl class="rec-meta">
              <template v-if="r.reason">
                <dt>原因</dt>
                <dd>{{ r.reason }}</dd>
              </template>
              <dt>操作人</dt>
              <dd>{{ r.adminUsername || '系统' }}</dd>
              <template v-if="r.requestId">
                <dt>requestId</dt>
                <dd class="mono">{{ r.requestId }}</dd>
              </template>
              <template v-if="r.referenceNo">
                <dt>参考单号</dt>
                <dd class="mono">{{ r.referenceNo }}</dd>
              </template>
              <template v-if="r.orderNo">
                <dt>订单号</dt>
                <dd class="mono">{{ r.orderNo }}</dd>
              </template>
            </dl>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <InventoryInitDialog v-model="initVisible" :submitting="initSubmitting" @submit="onInitSubmit" />
    <InventoryAdjustDialog
      v-model="adjustVisible"
      :product-id="adjustTarget"
      :submitting="adjustSubmitting"
      @submit="onAdjustSubmit"
    />
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-12);
  margin-bottom: var(--space-16);
}

.low-stock-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
}

.low-stock-label {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.table {
  width: 100%;
}

.muted {
  color: var(--text-faint);
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
  gap: var(--space-16);
}

.records-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-8);
}

.records-title {
  font-weight: 600;
}

.records-sub {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.records-body {
  min-height: 120px;
}

.rec-head {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  margin-bottom: var(--space-8);
}

.rec-qty {
  font-weight: 600;
}

.rec-qty.up {
  color: var(--success);
}

.rec-qty.down {
  color: var(--danger);
}

.rec-meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px var(--space-12);
  margin: 0;
  font-size: var(--text-sm);
}

.rec-meta dt {
  color: var(--text-faint);
  white-space: nowrap;
}

.rec-meta dd {
  margin: 0;
  color: var(--text);
  word-break: break-all;
}

.mono {
  font-family: var(--font-mono, monospace);
}
</style>
