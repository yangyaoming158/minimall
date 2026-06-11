<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  cancelInboundOrder,
  confirmInboundOrder,
  getInboundOrder,
  listInboundOrders,
} from '@/api/inboundOrders'
import { ApiError } from '@/types/api'
import type { InboundOrder, InboundOrderSource, InboundOrderStatus } from '@/types/inbound'

const PAGE_SIZE = 10

const route = useRoute()

const orders = ref<InboundOrder[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const statusFilter = ref<'' | InboundOrderStatus>('')

const STATUS_OPTIONS: { value: '' | InboundOrderStatus; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'APPLIED', label: '已入库' },
  { value: 'CANCELLED', label: '已取消' },
]

const STATUS_LABEL: Record<InboundOrderStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  APPLIED: '已入库',
  CANCELLED: '已取消',
}

const STATUS_TONE: Record<InboundOrderStatus, 'success' | 'warning' | 'danger' | 'info'> = {
  DRAFT: 'warning',
  CONFIRMED: 'info',
  APPLIED: 'success',
  CANCELLED: 'danger',
}

const SOURCE_LABEL: Record<InboundOrderSource, string> = {
  ADMIN_MANUAL: '管理员手动',
  AI_SUGGESTION: 'AI 建议',
}

// Detail drawer state.
const detailVisible = ref(false)
const detail = ref<InboundOrder | null>(null)
const detailLoading = ref(false)

const confirmSubmitting = ref(false)
const cancelSubmitting = ref(false)

function genRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

// Idempotency keys per inbound order: a retry after a failed confirm reuses
// the same X-Request-Id so the backend replays instead of double-applying;
// the key is dropped once the confirm succeeds.
const confirmRequestIds = new Map<string, string>()

function requestIdFor(inboundNo: string): string {
  let id = confirmRequestIds.get(inboundNo)
  if (!id) {
    id = genRequestId()
    confirmRequestIds.set(inboundNo, id)
  }
  return id
}

// The http interceptor only toasts 401/429/500. Surface other business errors
// (e.g. 40900 status conflict) explicitly; transport-less errors were toasted.
function surfaceBusinessError(err: unknown): void {
  if (err instanceof ApiError && err.httpStatus != null && ![401, 429, 500].includes(err.httpStatus)) {
    ElMessage.error(err.message)
  }
}

async function fetchOrders(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listInboundOrders({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(statusFilter.value ? { status: statusFilter.value } : {}),
    })
    orders.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchOrders()
}

function onPageChange(next: number): void {
  page.value = next
  fetchOrders()
}

async function openDetail(inboundNo: string): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getInboundOrder(inboundNo)
  } catch (err) {
    surfaceBusinessError(err) // e.g. 40400 from a stale deep link
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function onConfirm(order: InboundOrder): Promise<void> {
  if (confirmSubmitting.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认入库单 ${order.inboundNo}（共 ${order.itemCount} 项 / ${order.totalQuantity} 件）。确认后将正式增加库存，此操作不可撤销。`,
      '确认入库',
      { confirmButtonText: '确认入库', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // admin cancelled
  }
  confirmSubmitting.value = true
  try {
    const confirmed = await confirmInboundOrder(order.inboundNo, requestIdFor(order.inboundNo))
    confirmRequestIds.delete(order.inboundNo)
    ElMessage.success(`入库单 ${confirmed.inboundNo} 已入库，库存已正式增加`)
    detailVisible.value = false
    await fetchOrders()
  } catch (err) {
    surfaceBusinessError(err) // requestId is kept so a retry replays idempotently
  } finally {
    confirmSubmitting.value = false
  }
}

async function onCancel(order: InboundOrder): Promise<void> {
  if (cancelSubmitting.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `取消入库草稿 ${order.inboundNo}。草稿取消不会变更库存。`,
      '取消草稿',
      { confirmButtonText: '取消草稿', cancelButtonText: '返回', type: 'info' },
    )
  } catch {
    return // admin cancelled
  }
  cancelSubmitting.value = true
  try {
    await cancelInboundOrder(order.inboundNo)
    ElMessage.success('入库草稿已取消')
    detailVisible.value = false
    await fetchOrders()
  } catch (err) {
    surfaceBusinessError(err)
  } finally {
    cancelSubmitting.value = false
  }
}

onMounted(async () => {
  await fetchOrders()
  // Deep link from the suggestion review page: /inbound-orders?inboundNo=IB-xxx
  const linked = route.query.inboundNo
  if (typeof linked === 'string' && linked) {
    await openDetail(linked)
  }
})
</script>

<template>
  <div>
    <PageHeader
      title="入库单"
      description="入库单确认是唯一正式增加库存的操作。AI 建议转换的草稿也在此确认；草稿与取消均不变更库存。"
    />

    <div class="filter-bar">
      <el-select v-model="statusFilter" placeholder="入库单状态" style="width: 140px" @change="onSearch">
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="orders" class="table" empty-text="暂无入库单">
      <el-table-column prop="inboundNo" label="入库单号" min-width="190" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            :label="STATUS_LABEL[row.status as InboundOrderStatus]"
            :tone="STATUS_TONE[row.status as InboundOrderStatus]"
          />
        </template>
      </el-table-column>
      <el-table-column label="来源" width="110">
        <template #default="{ row }">{{ SOURCE_LABEL[row.source as InboundOrderSource] }}</template>
      </el-table-column>
      <el-table-column label="商品数" width="90" align="right">
        <template #default="{ row }">{{ row.itemCount }}</template>
      </el-table-column>
      <el-table-column label="总数量" width="90" align="right">
        <template #default="{ row }">{{ row.totalQuantity }}</template>
      </el-table-column>
      <el-table-column label="创建人" width="110">
        <template #default="{ row }">{{ row.createdByAdminUsername ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="确认人" width="110">
        <template #default="{ row }">{{ row.confirmedByAdminUsername ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.inboundNo)">详情</el-button>
          <template v-if="row.status === 'DRAFT'">
            <el-button link type="primary" @click="onConfirm(row)">确认入库</el-button>
            <el-button link type="danger" @click="onCancel(row)">取消</el-button>
          </template>
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

    <el-drawer v-model="detailVisible" title="入库单详情" size="560px">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="入库单号" :span="2">
            <span class="mono">{{ detail.inboundNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="STATUS_LABEL[detail.status]" :tone="STATUS_TONE[detail.status]" />
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ SOURCE_LABEL[detail.source] }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdByAdminUsername ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.confirmedByAdminUsername" label="确认人">
            {{ detail.confirmedByAdminUsername }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.confirmedAt" label="确认时间">
            {{ detail.confirmedAt }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.confirmRequestId" label="requestId" :span="2">
            <span class="mono">{{ detail.confirmRequestId }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="items-title">入库明细（{{ detail.itemCount }} 项，合计 {{ detail.totalQuantity }} 件）</h3>
        <el-table :data="detail.items" size="small" empty-text="无明细">
          <el-table-column prop="productId" label="商品 ID" min-width="180" show-overflow-tooltip />
          <el-table-column label="入库数量" width="110" align="right">
            <template #default="{ row }">{{ row.quantity }}</template>
          </el-table-column>
        </el-table>

        <div v-if="detail.status === 'DRAFT'" class="detail-actions">
          <el-button type="primary" :loading="confirmSubmitting" @click="onConfirm(detail)">
            确认入库
          </el-button>
          <el-button type="danger" plain :loading="cancelSubmitting" @click="onCancel(detail)">
            取消草稿
          </el-button>
        </div>
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

.table {
  width: 100%;
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

.items-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-strong);
}

.detail-actions {
  display: flex;
  gap: var(--space-8);
  justify-content: flex-end;
}

.mono {
  font-family: var(--font-mono, monospace);
}
</style>
