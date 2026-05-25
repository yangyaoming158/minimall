<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder, cancelOrder } from '@/api/order'
import { useConfirm } from '@/composables/useConfirm'
import { ApiError, ErrorCode } from '@/types/api'
import type { Order } from '@/types/order'
import Button from '@/components/atoms/Button.vue'
import Hairline from '@/components/atoms/Hairline.vue'
import PriceText from '@/components/atoms/PriceText.vue'
import Skeleton from '@/components/atoms/Skeleton.vue'
import SkeletonText from '@/components/atoms/SkeletonText.vue'
import ProductCover from '@/components/ProductCover.vue'
import ErrorState from '@/components/ErrorState.vue'
import OrderStepper from '@/components/OrderStepper.vue'

const route = useRoute()
const router = useRouter()
const confirm = useConfirm()

const orderNo = computed(() => String(route.params.orderNo ?? ''))

const order = ref<Order | null>(null)
const loading = ref(false)
const loadError = ref<
  | { kind: 'not-found' | 'generic'; message: string }
  | null
>(null)

const cancelling = ref(false)

async function load(): Promise<void> {
  if (!orderNo.value) return
  loading.value = true
  loadError.value = null
  try {
    order.value = await getOrder(orderNo.value)
  } catch (err) {
    order.value = null
    // Backend returns NOT_FOUND for both "does not exist" and "exists but
    // belongs to another user" (authorization-aware 404, by design).
    if (
      err instanceof ApiError &&
      (err.httpStatus === 404 || err.code === ErrorCode.NOT_FOUND)
    ) {
      loadError.value = { kind: 'not-found', message: '订单不存在或已被移除' }
    } else {
      loadError.value = {
        kind: 'generic',
        message: err instanceof Error ? err.message : '加载订单失败',
      }
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(
  () => orderNo.value,
  (next, prev) => {
    if (next && next !== prev) {
      order.value = null
      load()
    }
  },
)

async function onCancel(): Promise<void> {
  if (!order.value) return
  const target = order.value.orderNo

  const ok = await confirm({
    title: '取消订单',
    body: `确定要取消订单 ${target} 吗？此操作无法恢复。`,
    confirmLabel: '确认取消',
    cancelLabel: '再想想',
    tone: 'danger',
  })
  if (!ok) return

  cancelling.value = true
  try {
    await cancelOrder(target)
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
      const msg = err instanceof Error ? err.message : '取消订单失败'
      ElMessage.error(msg)
    }
  } finally {
    cancelling.value = false
  }
}

function goPay(): void {
  if (!order.value) return
  router.push({ name: 'payment', params: { orderNo: order.value.orderNo } })
}

function goList(): void {
  router.push({ name: 'orders' })
}

const isPendingPayment = computed(
  () => order.value?.status === 'PENDING_PAYMENT',
)

const isPaid = computed(() => order.value?.status === 'PAID')

const isClosedLike = computed(
  () =>
    order.value?.status === 'CANCELLED' ||
    order.value?.status === 'CLOSED' ||
    order.value?.status === 'REFUNDED',
)

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

const lastEight = computed(() => {
  const s = orderNo.value
  return s.length > 8 ? s.slice(-8) : s
})

const statusTimeRow = computed<{ label: string; value: string } | null>(() => {
  if (!order.value) return null
  if (isPendingPayment.value) {
    return { label: '支付截止', value: formatDateTime(order.value.expireAt) }
  }
  if (isPaid.value) {
    return { label: '支付时间', value: formatDateTime(order.value.paidAt) }
  }
  if (isClosedLike.value) {
    return { label: '关闭时间', value: formatDateTime(order.value.closedAt ?? order.value.updatedAt) }
  }
  return null
})

const isInitialLoad = computed(
  () => loading.value && !order.value && !loadError.value,
)
</script>

<template>
  <section class="page">
    <!-- Initial load skeleton -->
    <div v-if="isInitialLoad" class="detail" aria-busy="true">
      <Skeleton width="120px" height="14px" block />
      <div class="d-head">
        <Skeleton width="160px" height="28px" block />
        <Skeleton width="200px" height="14px" block />
      </div>
      <div class="d-stepper-skel">
        <Skeleton width="100%" height="24px" radius="full" block />
      </div>
      <Hairline />
      <div class="item-row">
        <Skeleton width="72px" height="72px" radius="md" block />
        <div class="item-mid">
          <SkeletonText :lines="2" line-height="14px" gap="10px" />
        </div>
        <div class="item-side">
          <Skeleton width="64px" height="14px" />
          <Skeleton width="80px" height="16px" />
        </div>
      </div>
      <Hairline />
      <div class="total-row">
        <Skeleton width="48px" height="18px" />
        <Skeleton width="120px" height="32px" />
      </div>
      <Hairline />
      <div class="meta-grid">
        <Skeleton width="100%" height="32px" block />
        <Skeleton width="100%" height="32px" block />
        <Skeleton width="100%" height="32px" block />
      </div>
    </div>

    <!-- 404 -->
    <ErrorState
      v-else-if="loadError && loadError.kind === 'not-found'"
      tone="notfound"
      title="订单不存在"
      :description="loadError.message"
      show-home
      home-label="返回订单列表"
      @home="goList"
    />

    <!-- Generic error -->
    <ErrorState
      v-else-if="loadError"
      tone="error"
      title="加载失败"
      :description="loadError.message"
      show-retry
      retry-label="重试"
      show-home
      home-label="返回订单列表"
      @retry="load"
      @home="goList"
    />

    <!-- Detail -->
    <div v-else-if="order" class="detail">
      <div class="crumb">
        <Button variant="text" size="sm" @click="goList">
          ← 我的订单 · …{{ lastEight }}
        </Button>
      </div>

      <header class="d-head">
        <h1 class="title">订单详情</h1>
        <span class="order-no-full" :title="order.orderNo">{{ order.orderNo }}</span>
      </header>

      <div class="d-stepper">
        <OrderStepper
          :status="order.status"
          :closed-at="order.closedAt"
          :updated-at="order.updatedAt"
        />
      </div>

      <section class="items" aria-label="商品明细">
        <div
          v-for="item in order.items"
          :key="item.productId"
          class="item-row"
        >
          <div class="item-cover">
            <ProductCover
              :product-id="item.productId"
              :name="item.productName"
              aspect="1:1"
              grade="list"
              size="full"
            />
          </div>
          <div class="item-mid">
            <span class="item-name">{{ item.productName }}</span>
            <span class="item-id">{{ item.productId }}</span>
            <span class="item-qty">× {{ item.quantity }}</span>
          </div>
          <div class="item-side">
            <PriceText :amount="item.unitPrice" size="sm" />
            <PriceText :amount="item.unitPrice * item.quantity" size="md" />
          </div>
        </div>
      </section>

      <Hairline />

      <div class="total-row">
        <span class="total-label">合计</span>
        <PriceText :amount="order.totalAmount" size="lg" />
      </div>
      <p class="total-hint">订单金额以后端结算为准</p>

      <Hairline />

      <dl class="meta-grid">
        <div class="meta-cell">
          <dt class="meta-label">订单号</dt>
          <dd class="meta-value meta-value--mono">{{ order.orderNo }}</dd>
        </div>
        <div class="meta-cell">
          <dt class="meta-label">下单时间</dt>
          <dd class="meta-value">{{ formatDateTime(order.createdAt) }}</dd>
        </div>
        <div v-if="statusTimeRow" class="meta-cell">
          <dt class="meta-label">{{ statusTimeRow.label }}</dt>
          <dd class="meta-value">{{ statusTimeRow.value }}</dd>
        </div>
      </dl>

      <div class="actions">
        <template v-if="isPendingPayment">
          <Button
            variant="ghost"
            size="lg"
            :loading="cancelling"
            @click="onCancel"
          >
            取消订单
          </Button>
          <Button variant="primary" size="lg" @click="goPay">
            去支付
          </Button>
        </template>
        <Button v-else-if="isPaid" variant="primary" size="lg" @click="goPay">
          查看支付凭证
        </Button>
        <Button v-else variant="ghost" size="lg" @click="goList">
          返回订单列表
        </Button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  min-height: 320px;
}

.detail {
  max-width: 720px;
  margin: 0 auto;
}

.crumb {
  margin-bottom: 12px;
  margin-left: -4px; /* compensate for text-button's inner padding */
}

.d-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 24px;
}

.title {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  font-weight: var(--t-h1-weight);
  line-height: var(--t-h1-lh);
  color: var(--ink-900);
}

.order-no-full {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--ink-500);
  word-break: break-all;
}

.d-stepper {
  margin-bottom: 32px;
}

.d-stepper-skel {
  margin-bottom: 32px;
}

.items {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.item-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 8px 0;
}

.item-cover {
  width: 72px;
}

.item-mid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.item-name {
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 500;
  color: var(--ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-id {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-qty {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}

.item-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  white-space: nowrap;
}

.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 16px 0 4px;
}

.total-label {
  font-family: var(--font-sans);
  font-size: 16px;
  font-weight: 500;
  color: var(--ink-900);
}

.total-hint {
  margin: 0 0 16px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  text-align: right;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px 24px;
  margin: 16px 0 32px;
}

.meta-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.meta-label {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  margin: 0;
}

.meta-value {
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--ink-900);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-value--mono {
  font-family: var(--font-mono);
  font-size: 13px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 640px) {
  .item-row {
    grid-template-columns: 64px minmax(0, 1fr);
    grid-template-rows: auto auto;
    gap: 12px 14px;
  }
  .item-cover {
    width: 64px;
  }
  .item-side {
    grid-column: 1 / -1;
    flex-direction: row;
    align-items: baseline;
    justify-content: space-between;
  }
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
