<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyOrders, cancelOrder } from '@/api/order'
import { ApiError, ErrorCode } from '@/types/api'
import type { Order } from '@/types/order'
import { getOrderStatusMeta } from '@/utils/order-status'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 10

const orders = ref<Order[]>([])
const totalElements = ref(0)
const currentPage = ref(1)

const loading = ref(false)
const errored = ref(false)
const errorMessage = ref('')
const cancellingOrderNo = ref<string | null>(null)

function readPageFromQuery(): number {
  const raw = route.query.page
  if (typeof raw !== 'string') return 1
  if (!/^\d+$/.test(raw)) return 1
  const n = parseInt(raw, 10)
  return n >= 1 ? n : 1
}

async function load(): Promise<void> {
  loading.value = true
  errored.value = false
  errorMessage.value = ''
  try {
    const res = await listMyOrders({
      page: currentPage.value - 1,
      size: PAGE_SIZE,
    })
    orders.value = res.content
    totalElements.value = res.totalElements
    // If the page param is beyond the last page (e.g. after cancelling the last
    // remaining order on a page), back off to the last available page.
    if (
      currentPage.value > 1 &&
      orders.value.length === 0 &&
      totalElements.value > 0
    ) {
      const lastPage = Math.max(1, Math.ceil(totalElements.value / PAGE_SIZE))
      if (lastPage !== currentPage.value) {
        currentPage.value = lastPage
        syncQuery()
        await load()
      }
    }
  } catch (err) {
    orders.value = []
    errored.value = true
    errorMessage.value = err instanceof Error ? err.message : '加载订单失败'
  } finally {
    loading.value = false
  }
}

function syncQuery(): void {
  const q = currentPage.value > 1 ? { page: String(currentPage.value) } : {}
  router.replace({ query: q })
}

function onPageChange(page: number): void {
  currentPage.value = page
  syncQuery()
  load()
}

onMounted(() => {
  currentPage.value = readPageFromQuery()
  load()
})

// Browser back/forward updates the query - keep state in sync without
// pushing more history entries.
watch(
  () => route.query.page,
  () => {
    const next = readPageFromQuery()
    if (next !== currentPage.value) {
      currentPage.value = next
      load()
    }
  },
)

function goDetail(orderNo: string): void {
  router.push({ name: 'order-detail', params: { orderNo } })
}

function goPay(orderNo: string): void {
  router.push({ name: 'payment', params: { orderNo } })
}

function goShopping(): void {
  router.push({ name: 'products' })
}

async function onCancel(order: Order): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定要取消订单 ${order.orderNo} 吗？此操作无法恢复。`,
      '取消订单',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
      },
    )
  } catch {
    // user dismissed the dialog
    return
  }

  cancellingOrderNo.value = order.orderNo
  try {
    await cancelOrder(order.orderNo)
    ElMessage.success('订单已取消')
    await load()
  } catch (err) {
    if (
      err instanceof ApiError &&
      (err.code === ErrorCode.ORDER_INVALID_STATE ||
        err.code === ErrorCode.ORDER_CANCELLED)
    ) {
      ElMessage.warning('订单状态已变化，已为你刷新最新数据')
      await load()
    } else {
      // network / 401 / 429 / 500 are already toasted by the http interceptor;
      // other business codes surface their message here.
      const msg = err instanceof Error ? err.message : '取消订单失败'
      ElMessage.error(msg)
    }
  } finally {
    cancellingOrderNo.value = null
  }
}

function productSummary(order: Order): string {
  if (order.items.length === 0) return '—'
  const first = order.items[0]
  const head = `${first.productName} × ${first.quantity}`
  if (order.items.length > 1) {
    return `${head} 等 ${order.items.length} 件`
  }
  return head
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  // Backend serializes LocalDateTime as ISO without timezone.
  // We render it as-is in a readable form; locale formatting is intentionally
  // simple to match the customer-storefront brief.
  return value.replace('T', ' ').slice(0, 19)
}

function formatAmount(value: number): string {
  return `¥${value.toFixed(2)}`
}

const isEmpty = computed(
  () =>
    !loading.value && !errored.value && orders.value.length === 0,
)

function isPendingPayment(order: Order): boolean {
  return order.status === 'PENDING_PAYMENT'
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1 class="title">我的订单</h1>
      <span v-if="totalElements > 0" class="count">共 {{ totalElements }} 笔订单</span>
    </header>

    <!-- Error -->
    <el-result
      v-if="errored"
      icon="error"
      title="加载失败"
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <!-- Empty -->
    <el-empty
      v-else-if="isEmpty"
      description="暂无订单"
    >
      <el-button type="primary" @click="goShopping">去逛逛</el-button>
    </el-empty>

    <!-- List -->
    <div v-else v-loading="loading" class="orders">
      <article
        v-for="order in orders"
        :key="order.orderNo"
        class="order-card"
        @click="goDetail(order.orderNo)"
      >
        <div class="card-head">
          <span class="order-no" :title="order.orderNo">{{ order.orderNo }}</span>
          <el-tag
            :type="getOrderStatusMeta(order.status).type"
            size="small"
            effect="light"
          >
            {{ getOrderStatusMeta(order.status).label }}
          </el-tag>
        </div>

        <div class="card-body">
          <span class="product-summary">{{ productSummary(order) }}</span>
          <span class="amount">{{ formatAmount(order.totalAmount) }}</span>
        </div>

        <div class="card-meta">
          <span>下单时间 {{ formatDateTime(order.createdAt) }}</span>
          <span v-if="isPendingPayment(order)">
            支付截止 {{ formatDateTime(order.expireAt) }}
          </span>
        </div>

        <div class="card-actions">
          <template v-if="isPendingPayment(order)">
            <el-button
              size="small"
              :loading="cancellingOrderNo === order.orderNo"
              @click.stop="onCancel(order)"
            >
              取消订单
            </el-button>
            <el-button
              type="primary"
              size="small"
              @click.stop="goPay(order.orderNo)"
            >
              去支付
            </el-button>
          </template>
          <el-button
            v-else
            size="small"
            @click.stop="goDetail(order.orderNo)"
          >
            查看详情
          </el-button>
        </div>
      </article>

      <div v-if="totalElements > PAGE_SIZE" class="pager">
        <el-pagination
          background
          layout="prev, pager, next, jumper, total"
          :total="totalElements"
          :page-size="PAGE_SIZE"
          :current-page="currentPage"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  min-height: 320px;
}

.page-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.count {
  font-size: 13px;
  color: #909399;
}

.orders {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card {
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 18px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.order-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.order-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.product-summary {
  flex: 1;
  font-size: 15px;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amount {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}

.card-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
