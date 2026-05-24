<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyOrders, cancelOrder } from '@/api/order'
import { ApiError, ErrorCode } from '@/types/api'
import type { Order, OrderStatus } from '@/types/order'
import Button from '@/components/atoms/Button.vue'
import DotStatus from '@/components/atoms/DotStatus.vue'
import Hairline from '@/components/atoms/Hairline.vue'
import Pager from '@/components/atoms/Pager.vue'
import PriceText from '@/components/atoms/PriceText.vue'
import Skeleton from '@/components/atoms/Skeleton.vue'
import SkeletonText from '@/components/atoms/SkeletonText.vue'
import ProductCover from '@/components/ProductCover.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'

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
      sort: 'createdAt,desc',
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

function firstItemProductId(order: Order): string | null {
  return order.items[0]?.productId ?? null
}

function firstItemName(order: Order): string | null {
  return order.items[0]?.productName ?? null
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  // Backend serializes LocalDateTime as ISO without timezone.
  // We render it as-is in a readable form; locale formatting is intentionally
  // simple to match the customer-storefront brief.
  return value.replace('T', ' ').slice(0, 19)
}

type DotTone = 'neutral' | 'terracotta' | 'success' | 'warn' | 'danger'

const STATUS_DOT: Record<OrderStatus, { label: string; tone: DotTone }> = {
  PENDING_PAYMENT: { label: '待支付', tone: 'warn' },
  PAID: { label: '已支付', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  CLOSED: { label: '已关闭', tone: 'neutral' },
  REFUNDED: { label: '已退款', tone: 'danger' },
}

function statusDot(status: string): { label: string; tone: DotTone } {
  return STATUS_DOT[status as OrderStatus] ?? { label: status, tone: 'neutral' }
}

const isEmpty = computed(
  () =>
    !loading.value && !errored.value && orders.value.length === 0,
)

const isInitialLoad = computed(
  () => loading.value && orders.value.length === 0 && !errored.value,
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

    <!-- Initial load: skeleton cards -->
    <div v-if="isInitialLoad" class="orders" aria-busy="true">
      <div v-for="i in 3" :key="i" class="order-card order-card--skeleton">
        <div class="card-main">
          <Skeleton width="96px" height="96px" radius="md" block />
          <div class="card-mid">
            <SkeletonText :lines="3" line-height="14px" gap="10px" />
          </div>
          <div class="card-right">
            <Skeleton width="72px" height="14px" />
            <Skeleton width="160px" height="12px" />
          </div>
        </div>
      </div>
    </div>

    <!-- Error -->
    <ErrorState
      v-else-if="errored"
      tone="error"
      title="加载失败"
      :description="errorMessage"
      show-retry
      retry-label="重试"
      @retry="load"
    />

    <!-- Empty -->
    <EmptyState
      v-else-if="isEmpty"
      title="暂无订单"
      description="去挑几件商品下单吧"
      show-action
      action-label="去逛逛"
      @action="goShopping"
    />

    <!-- List -->
    <div
      v-else
      class="orders"
      :aria-busy="loading ? 'true' : 'false'"
      :class="{ 'orders--dim': loading }"
    >
      <article
        v-for="order in orders"
        :key="order.orderNo"
        class="order-card"
        @click="goDetail(order.orderNo)"
      >
        <div class="card-main">
          <div class="card-cover">
            <ProductCover
              :product-id="firstItemProductId(order)"
              :name="firstItemName(order)"
              aspect="1:1"
              grade="list"
              size="full"
            />
          </div>

          <div class="card-mid">
            <span class="order-no" :title="order.orderNo">{{ order.orderNo }}</span>
            <span class="product-summary">{{ productSummary(order) }}</span>
            <PriceText :amount="order.totalAmount" size="lg" />
          </div>

          <div class="card-right">
            <DotStatus :tone="statusDot(order.status).tone">
              {{ statusDot(order.status).label }}
            </DotStatus>
            <span class="meta">下单 {{ formatDateTime(order.createdAt) }}</span>
            <span v-if="isPendingPayment(order)" class="meta meta--warn">
              支付截止 {{ formatDateTime(order.expireAt) }}
            </span>
          </div>
        </div>

        <Hairline />

        <div class="card-actions">
          <template v-if="isPendingPayment(order)">
            <Button
              variant="ghost"
              size="sm"
              :loading="cancellingOrderNo === order.orderNo"
              @click.stop="onCancel(order)"
            >
              取消订单
            </Button>
            <Button
              variant="primary"
              size="sm"
              @click.stop="goPay(order.orderNo)"
            >
              去支付
            </Button>
          </template>
          <Button
            v-else
            variant="text"
            size="sm"
            @click.stop="goDetail(order.orderNo)"
          >
            查看详情 →
          </Button>
        </div>
      </article>

      <div v-if="totalElements > PAGE_SIZE" class="pager-wrap">
        <Pager
          :total="totalElements"
          :page-size="PAGE_SIZE"
          :current="currentPage"
          @change="onPageChange"
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
  margin-bottom: 24px;
  gap: 12px;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  font-weight: var(--t-h1-weight);
  line-height: var(--t-h1-lh);
  color: var(--ink-900);
}

.count {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
}

.orders {
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: opacity var(--dur-2) var(--ease);
}

.orders--dim {
  opacity: 0.6;
  pointer-events: none;
}

.order-card {
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius);
  padding: 20px 20px 16px;
  cursor: pointer;
  transition: border-color var(--dur-2) var(--ease),
    box-shadow var(--dur-2) var(--ease),
    transform var(--dur-2) var(--ease);
}

.order-card:hover {
  border-color: var(--ink-300);
  box-shadow: 0 8px 24px rgba(31, 35, 41, 0.06);
  transform: translateY(-1px);
}

.order-card--skeleton {
  cursor: default;
}

.order-card--skeleton:hover {
  border-color: var(--ink-100);
  box-shadow: none;
  transform: none;
}

.card-main {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 20px;
  align-items: start;
  margin-bottom: 14px;
}

.card-cover {
  width: 96px;
}

.card-mid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.order-no {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-summary {
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 500;
  color: var(--ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  text-align: right;
  white-space: nowrap;
}

.meta {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}

.meta--warn {
  color: var(--warn);
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.pager-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

@media (max-width: 640px) {
  .card-main {
    grid-template-columns: 64px minmax(0, 1fr);
    grid-template-rows: auto auto;
    gap: 14px;
  }
  .card-cover {
    width: 64px;
  }
  .card-right {
    grid-column: 1 / -1;
    align-items: flex-start;
    text-align: left;
  }
}
</style>
