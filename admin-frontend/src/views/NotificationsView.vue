<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getNotification, listNotifications } from '@/api/notifications'
import type { AdminNotification, NotificationLogStatus } from '@/types/notification'

const PAGE_SIZE = 10

const notifications = ref<AdminNotification[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const filters = reactive<{
  eventId: string
  orderNo: string
  status: '' | NotificationLogStatus
  createdRange: [string, string] | null
}>({
  eventId: '',
  orderNo: '',
  status: '',
  createdRange: null,
})

const STATUS_LABEL: Record<NotificationLogStatus, string> = {
  PENDING: '待发送',
  SENT: '已发送',
  FAILED: '发送失败',
}

const STATUS_OPTIONS: { value: '' | NotificationLogStatus; label: string }[] = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待发送' },
  { value: 'SENT', label: '已发送' },
  { value: 'FAILED', label: '发送失败' },
]

// Detail drawer.
const detailVisible = ref(false)
const detail = ref<AdminNotification | null>(null)
const detailLoading = ref(false)

async function fetchNotifications(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listNotifications({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(filters.eventId.trim() ? { eventId: filters.eventId.trim() } : {}),
      ...(filters.orderNo.trim() ? { orderNo: filters.orderNo.trim() } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.createdRange
        ? { createdFrom: filters.createdRange[0], createdTo: filters.createdRange[1] }
        : {}),
    })
    notifications.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    notifications.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchNotifications()
}

function onReset(): void {
  filters.eventId = ''
  filters.orderNo = ''
  filters.status = ''
  filters.createdRange = null
  onSearch()
}

function onPageChange(next: number): void {
  page.value = next
  fetchNotifications()
}

async function openDetail(row: AdminNotification): Promise<void> {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getNotification(row.id)
  } catch {
    /* surfaced by the http interceptor; keep the row snapshot */
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchNotifications)
</script>

<template>
  <div>
    <PageHeader title="通知管理" description="通知日志列表、状态筛选、eventId / orderNo / 时间筛选、通知详情（只读，无重发）。" />

    <div class="filter-bar">
      <el-input v-model="filters.eventId" placeholder="事件 ID" clearable style="width: 200px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-input v-model="filters.orderNo" placeholder="订单号" clearable style="width: 180px" @keyup.enter="onSearch" @clear="onSearch" />
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

    <el-table v-loading="loading" :data="notifications" class="table" empty-text="暂无通知">
      <el-table-column prop="eventId" label="事件 ID" min-width="200" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.orderNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="notificationType" label="类型" width="150" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="STATUS_LABEL[row.status as NotificationLogStatus]" />
        </template>
      </el-table-column>
      <el-table-column prop="sentAt" label="发送时间" min-width="170">
        <template #default="{ row }">{{ row.sentAt || '—' }}</template>
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

    <el-drawer v-model="detailVisible" title="通知详情" size="460px">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="事件 ID">{{ detail.eventId }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.notificationType }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="STATUS_LABEL[detail.status]" />
          </el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ detail.sentAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMessage" label="错误信息">
            <span class="error-text">{{ detail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <template v-if="detail.payload">
          <h4 class="section-title">Payload</h4>
          <pre class="snapshot">{{ detail.payload }}</pre>
        </template>
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

.error-text {
  color: var(--danger);
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
