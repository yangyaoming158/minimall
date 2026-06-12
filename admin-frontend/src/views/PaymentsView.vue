<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getPayment, listPayments } from '@/api/payments'
import type { AdminPayment, PaymentStatus } from '@/types/payment'

const PAGE_SIZE = 10

const payments = ref<AdminPayment[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const filters = reactive<{
  paymentNo: string
  orderNo: string
  status: '' | PaymentStatus
  paidRange: [string, string] | null
}>({
  paymentNo: '',
  orderNo: '',
  status: '',
  paidRange: null,
})

const STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: '待支付',
  SUCCESS: '已支付',
  FAILED: '失败',
}

const STATUS_OPTIONS: { value: '' | PaymentStatus; label: string }[] = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待支付' },
  { value: 'SUCCESS', label: '已支付' },
  { value: 'FAILED', label: '失败' },
]

// Detail drawer.
const detailVisible = ref(false)
const detail = ref<AdminPayment | null>(null)
const detailLoading = ref(false)

function money(value: number | string): string {
  return `¥${Number(value).toFixed(2)}`
}

async function fetchPayments(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listPayments({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(filters.paymentNo.trim() ? { paymentNo: filters.paymentNo.trim() } : {}),
      ...(filters.orderNo.trim() ? { orderNo: filters.orderNo.trim() } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.paidRange ? { paidFrom: filters.paidRange[0], paidTo: filters.paidRange[1] } : {}),
    })
    payments.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    payments.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchPayments()
}

function onReset(): void {
  filters.paymentNo = ''
  filters.orderNo = ''
  filters.status = ''
  filters.paidRange = null
  onSearch()
}

function onPageChange(next: number): void {
  page.value = next
  fetchPayments()
}

async function openDetail(row: AdminPayment): Promise<void> {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getPayment(row.paymentNo)
  } catch {
    /* surfaced by the http interceptor; keep the row snapshot */
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchPayments)
</script>

<template>
  <div>
    <PageHeader title="支付管理" description="支付单列表、状态筛选、paymentNo / orderNo / 支付时间筛选、支付详情（只读）。" />

    <div class="filter-bar">
      <el-input v-model="filters.paymentNo" placeholder="支付单号" clearable style="width: 180px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-input v-model="filters.orderNo" placeholder="订单号" clearable style="width: 180px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="filters.status" placeholder="状态" style="width: 130px" @change="onSearch">
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-date-picker
        v-model="filters.paidRange"
        type="datetimerange"
        value-format="YYYY-MM-DD[T]HH:mm:ss"
        range-separator="至"
        start-placeholder="支付起"
        end-placeholder="支付止"
        style="width: 360px"
        @change="onSearch"
      />
      <el-button type="primary" plain @click="onSearch">搜索</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="payments" class="table" empty-text="暂无支付单">
      <el-table-column prop="paymentNo" label="支付单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
      <el-table-column label="用户" width="90" align="right">
        <template #default="{ row }">{{ row.userId ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="商品" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productId || '—' }}</template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">{{ money(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="STATUS_LABEL[row.status as PaymentStatus]" />
        </template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="90" />
      <el-table-column prop="paidAt" label="支付时间" min-width="170">
        <template #default="{ row }">{{ row.paidAt || '—' }}</template>
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

    <el-drawer v-model="detailVisible" title="支付详情" size="440px">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="支付单号">{{ detail.paymentNo }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ detail.userId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="商品 ID">{{ detail.productId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="STATUS_LABEL[detail.status]" />
          </el-descriptions-item>
          <el-descriptions-item label="金额">{{ money(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ detail.channel }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ detail.paidAt || '—' }}</el-descriptions-item>
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
  flex-wrap: wrap;
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
</style>
