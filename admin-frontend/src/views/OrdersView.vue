<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getOrder, getOrderEvents, listOrders } from '@/api/orders'
import type { AdminOrder, OrderEvent, OrderStatus } from '@/types/order'

const PAGE_SIZE = 10

const orders = ref<AdminOrder[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const filters = reactive<{
  orderNo: string
  username: string
  productId: string
  status: '' | OrderStatus
  createdRange: [string, string] | null
}>({
  orderNo: '',
  username: '',
  productId: '',
  status: '',
  createdRange: null,
})

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
}

const STATUS_OPTIONS: { value: '' | OrderStatus; label: string }[] = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'CLOSED', label: '已关闭' },
]

// Detail drawer.
const detailVisible = ref(false)
const detail = ref<AdminOrder | null>(null)
const events = ref<OrderEvent[]>([])
const detailLoading = ref(false)

function money(value: number | string): string {
  return `¥${Number(value).toFixed(2)}`
}

function eventLabel(e: OrderEvent): string {
  if (e.eventType) {
    return e.eventType
  }
  const from = e.fromStatus ? STATUS_LABEL[e.fromStatus] : '—'
  const to = e.toStatus ? STATUS_LABEL[e.toStatus] : '—'
  return `${from} → ${to}`
}

async function fetchOrders(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listOrders({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(filters.orderNo.trim() ? { orderNo: filters.orderNo.trim() } : {}),
      ...(filters.username.trim() ? { username: filters.username.trim() } : {}),
      ...(filters.productId.trim() ? { productId: filters.productId.trim() } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.createdRange
        ? { createdFrom: filters.createdRange[0], createdTo: filters.createdRange[1] }
        : {}),
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

function onReset(): void {
  filters.orderNo = ''
  filters.username = ''
  filters.productId = ''
  filters.status = ''
  filters.createdRange = null
  onSearch()
}

function onPageChange(next: number): void {
  page.value = next
  fetchOrders()
}

async function openDetail(row: AdminOrder): Promise<void> {
  detail.value = row
  events.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [full, ev] = await Promise.all([getOrder(row.orderNo), getOrderEvents(row.orderNo)])
    detail.value = full
    events.value = ev
  } catch {
    /* surfaced by the http interceptor; keep the row snapshot */
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchOrders)
</script>

<template>
  <div>
    <PageHeader title="订单管理" description="订单列表、状态 / 用户 / 商品 / 时间筛选、订单详情与事件时间线（只读）。" />

    <div class="filter-bar">
      <el-input v-model="filters.orderNo" placeholder="订单号" clearable style="width: 180px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-input v-model="filters.username" placeholder="用户名" clearable style="width: 150px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-input v-model="filters.productId" placeholder="商品 ID" clearable style="width: 150px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="filters.status" placeholder="状态" style="width: 130px" @change="onSearch">
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-date-picker
        v-model="filters.createdRange"
        type="datetimerange"
        value-format="YYYY-MM-DD[T]HH:mm:ss"
        range-separator="至"
        start-placeholder="创建起"
        end-placeholder="创建止"
        style="width: 360px"
        @change="onSearch"
      />
      <el-button type="primary" plain @click="onSearch">搜索</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="orders" class="table" empty-text="暂无订单">
      <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
      <el-table-column label="用户" min-width="140">
        <template #default="{ row }">{{ row.username }}<span class="muted"> #{{ row.userId }}</span></template>
      </el-table-column>
      <el-table-column label="商品" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.items && row.items.length">{{ row.items[0].productName }} ×{{ row.items[0].quantity }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">{{ money(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="STATUS_LABEL[row.status as OrderStatus]" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="170" />
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

    <el-drawer v-model="detailVisible" title="订单详情" size="520px">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.username }} #{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="STATUS_LABEL[detail.status]" />
          </el-descriptions-item>
          <el-descriptions-item label="金额">{{ money(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ detail.paidAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ detail.expireAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关闭时间">{{ detail.closedAt || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">商品明细</h4>
        <el-table :data="detail.items" size="small" border>
          <el-table-column prop="productId" label="商品 ID" min-width="120" show-overflow-tooltip />
          <el-table-column prop="productName" label="名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="单价" width="90" align="right">
            <template #default="{ row }">{{ money(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="70" align="right" />
        </el-table>

        <h4 class="section-title">事件时间线</h4>
        <el-empty v-if="!detailLoading && events.length === 0" description="暂无事件" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item v-for="(e, i) in events" :key="i" :timestamp="e.occurredAt" placement="top">
            <div class="evt-head">{{ eventLabel(e) }}</div>
            <div v-if="e.eventId" class="evt-meta mono">eventId: {{ e.eventId }}</div>
            <div v-if="e.payload" class="evt-meta mono">{{ e.payload }}</div>
          </el-timeline-item>
        </el-timeline>
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

.evt-head {
  font-weight: 600;
}

.evt-meta {
  font-size: var(--text-xs);
  color: var(--text-faint);
  word-break: break-all;
}

.mono {
  font-family: var(--font-mono, monospace);
}
</style>
