<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import InventoryInitDialog from '@/components/InventoryInitDialog.vue'
import InventoryAdjustDialog, {
  type AdjustFormPayload,
} from '@/components/InventoryAdjustDialog.vue'
import { adjustInventory, getInventory, initializeInventory, listInventories } from '@/api/inventories'
import { ApiError } from '@/types/api'
import type {
  AdminInventory,
  InitializeInventoryRequest,
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

// Detail drawer state.
const detailVisible = ref(false)
const detail = ref<AdminInventory | null>(null)
const detailLoading = ref(false)

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
    <PageHeader title="库存管理" description="库存列表、搜索、库存状态与低库存筛选、初始化 / 调整库存、库存详情。">
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
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openAdjust(row)">调整</el-button>
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
</style>
