<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getInventory, listInventories } from '@/api/inventories'
import type { AdminInventory, InventoryStatus, StockState } from '@/types/inventory'

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

onMounted(fetchInventories)
</script>

<template>
  <div>
    <PageHeader title="库存管理" description="库存列表、搜索、库存状态与低库存筛选、库存详情。" />

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
      <el-table-column label="操作" width="120" fixed="right">
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
