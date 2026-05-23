<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder } from '@/api/order'
import { getPayment, payOrder } from '@/api/payment'
import { ApiError, ErrorCode } from '@/types/api'
import type { Order } from '@/types/order'
import type { Payment } from '@/types/payment'
import { getOrderStatusMeta } from '@/utils/order-status'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo ?? ''))

const POLL_ATTEMPTS = 5
const POLL_INTERVAL_MS = 800

type ViewState =
  | 'loading'
  | 'error-not-found'
  | 'error-generic'
  | 'ready'           // PENDING_PAYMENT, ready to pay
  | 'submitting'      // POST /pay in flight or polling
  | 'success'         // order reached PAID
  | 'success-pending' // /pay succeeded but order not yet PAID after polling
  | 'already-paid'    // landed on a PAID order
  | 'not-payable'     // landed on CANCELLED / CLOSED / REFUNDED

const state = ref<ViewState>('loading')
const errorMessage = ref('')
const submitError = ref<string | null>(null)

const order = ref<Order | null>(null)
const payment = ref<Payment | null>(null)

// Generated once on entry; reused across retries until the user navigates
// away and the component unmounts (consistent with checkout's contract).
const idempotencyKey = ref('')

// Component lifetime cancellation flag for the polling loop.
let cancelled = false

function generateKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function loadInitial(): Promise<void> {
  state.value = 'loading'
  errorMessage.value = ''
  try {
    const o = await getOrder(orderNo.value)
    order.value = o
    if (o.status === 'PAID') {
      // Backend may or may not have a payment row yet visible to GET; tolerate
      // a missing payment record so the page still renders.
      try {
        payment.value = await getPayment(orderNo.value)
      } catch {
        payment.value = null
      }
      state.value = 'already-paid'
    } else if (o.status === 'PENDING_PAYMENT') {
      state.value = 'ready'
    } else {
      state.value = 'not-payable'
    }
  } catch (err) {
    order.value = null
    if (
      err instanceof ApiError &&
      (err.httpStatus === 404 || err.code === ErrorCode.NOT_FOUND)
    ) {
      state.value = 'error-not-found'
      errorMessage.value = '订单不存在或已被移除'
    } else {
      state.value = 'error-generic'
      errorMessage.value = err instanceof Error ? err.message : '加载订单失败'
    }
  }
}

onMounted(() => {
  idempotencyKey.value = generateKey()
  loadInitial()
})

onBeforeUnmount(() => {
  cancelled = true
})

// After the /pay POST returns SUCCESS, the order-service still has to consume
// the RabbitMQ PaymentSuccessEvent before the order flips to PAID. Poll a few
// times so the user sees the final state in the same flow.
async function pollOrderUntilPaid(): Promise<boolean> {
  for (let i = 0; i < POLL_ATTEMPTS; i += 1) {
    await sleep(POLL_INTERVAL_MS)
    if (cancelled) return false
    try {
      const o = await getOrder(orderNo.value)
      order.value = o
      if (o.status === 'PAID') {
        return true
      }
    } catch {
      // Transient errors during polling shouldn't abort; the next iteration
      // will retry. Final state is decided by the loop exit.
    }
  }
  return false
}

async function refreshPaymentReceipt(): Promise<void> {
  try {
    payment.value = await getPayment(orderNo.value)
  } catch {
    payment.value = null
  }
}

async function onSubmit(): Promise<void> {
  if (state.value !== 'ready' || !order.value) return

  state.value = 'submitting'
  submitError.value = null

  try {
    await payOrder(orderNo.value, {
      channel: 'MOCK',
      idempotencyKey: idempotencyKey.value,
    })
  } catch (err) {
    // PAYMENT_ALREADY_SUCCESS: another tab/process paid this order while we
    // were on the page. Re-read order + payment and present the already-paid
    // view instead of leaving the user stuck with a stale error.
    if (err instanceof ApiError && err.code === ErrorCode.PAYMENT_ALREADY_SUCCESS) {
      ElMessage.warning('该订单已支付，已为你刷新最新状态')
      await loadInitial()
      return
    }
    submitError.value = err instanceof Error ? err.message : '支付失败'
    state.value = 'ready'
    return
  }

  // Pay accepted. Now wait for order-service to consume the async event.
  const paid = await pollOrderUntilPaid()
  if (cancelled) return

  if (paid) {
    await refreshPaymentReceipt()
    state.value = 'success'
  } else {
    state.value = 'success-pending'
  }
}

async function onManualRefresh(): Promise<void> {
  if (state.value !== 'success-pending') return
  state.value = 'submitting'
  const paid = await pollOrderUntilPaid()
  if (cancelled) return
  if (paid) {
    await refreshPaymentReceipt()
    state.value = 'success'
  } else {
    state.value = 'success-pending'
  }
}

function goOrderDetail(): void {
  router.push({ name: 'order-detail', params: { orderNo: orderNo.value } })
}

function goOrders(): void {
  router.push({ name: 'orders' })
}

function goShopping(): void {
  router.push({ name: 'products' })
}

const amountText = computed(() =>
  order.value ? `¥${order.value.totalAmount.toFixed(2)}` : '',
)

const orderStatusMeta = computed(() =>
  order.value ? getOrderStatusMeta(order.value.status) : null,
)

const submitting = computed(() => state.value === 'submitting')

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <section v-loading="state === 'loading'" class="page">
    <!-- Not found / cross-user (authorization-aware 404) -->
    <el-result
      v-if="state === 'error-not-found'"
      icon="warning"
      title="订单不存在"
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button type="primary" @click="goOrders">返回订单列表</el-button>
      </template>
    </el-result>

    <!-- Generic load error -->
    <el-result
      v-else-if="state === 'error-generic'"
      icon="error"
      title="加载失败"
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button type="primary" @click="loadInitial">重试</el-button>
        <el-button @click="goOrders">返回订单列表</el-button>
      </template>
    </el-result>

    <!-- Main layout -->
    <div v-else-if="order" class="payment">
      <el-alert
        class="mock-banner"
        type="warning"
        :closable="false"
        show-icon
        title="模拟支付"
        description="本页面为 demo 演示链路，不会发生真实扣款。"
      />

      <!-- Order summary card (shared across states) -->
      <section class="card">
        <h2 class="section-title">订单信息</h2>
        <div class="summary-row">
          <span class="row-label">订单号</span>
          <span class="row-value mono">{{ order.orderNo }}</span>
        </div>
        <div class="summary-row">
          <span class="row-label">订单状态</span>
          <el-tag
            v-if="orderStatusMeta"
            :type="orderStatusMeta.type"
            size="small"
            effect="light"
          >
            {{ orderStatusMeta.label }}
          </el-tag>
        </div>
        <div class="summary-row" v-if="state === 'ready' || state === 'submitting'">
          <span class="row-label">支付截止</span>
          <span class="row-value">{{ formatDateTime(order.expireAt) }}</span>
        </div>
        <el-divider />
        <div class="total-row">
          <span class="total-label">应付金额</span>
          <span class="total-amount">{{ amountText }}</span>
        </div>
      </section>

      <!-- READY -->
      <template v-if="state === 'ready' || state === 'submitting'">
        <el-alert
          v-if="submitError"
          class="submit-error"
          type="error"
          :title="submitError"
          :closable="false"
          show-icon
        />

        <div class="actions">
          <el-button
            size="large"
            :disabled="submitting"
            @click="goOrderDetail"
          >
            返回订单详情
          </el-button>
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            @click="onSubmit"
          >
            {{ submitting ? '处理中…' : `确认支付 ${amountText}` }}
          </el-button>
        </div>
        <p v-if="submitting" class="poll-hint">
          正在等待订单状态更新，请稍候…
        </p>
      </template>

      <!-- SUCCESS -->
      <section v-else-if="state === 'success'" class="card success-card">
        <div class="success-head">
          <el-icon class="success-icon" :size="48"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg></el-icon>
          <h2 class="success-title">支付成功</h2>
          <p class="success-sub">本次为模拟支付，订单已标记为已支付。</p>
        </div>

        <template v-if="payment">
          <el-divider />
          <h2 class="section-title">支付凭证</h2>
          <div class="summary-row">
            <span class="row-label">支付单号</span>
            <span class="row-value mono">{{ payment.paymentNo }}</span>
          </div>
          <div class="summary-row">
            <span class="row-label">支付渠道</span>
            <span class="row-value">{{ payment.channel }}</span>
          </div>
          <div class="summary-row">
            <span class="row-label">支付金额</span>
            <span class="row-value">¥{{ payment.amount.toFixed(2) }}</span>
          </div>
          <div class="summary-row">
            <span class="row-label">支付时间</span>
            <span class="row-value">{{ formatDateTime(payment.paidAt) }}</span>
          </div>
        </template>

        <div class="actions">
          <el-button size="large" @click="goShopping">继续逛逛</el-button>
          <el-button type="primary" size="large" @click="goOrderDetail">
            查看订单详情
          </el-button>
        </div>
      </section>

      <!-- SUCCESS-PENDING (paid but order not yet flipped after polling) -->
      <section v-else-if="state === 'success-pending'" class="card">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="支付已提交"
          description="支付请求已被受理，订单状态稍后更新。你可以稍候再刷新，或先去订单详情查看最新状态。"
        />
        <div class="actions">
          <el-button size="large" @click="goOrderDetail">返回订单详情</el-button>
          <el-button type="primary" size="large" @click="onManualRefresh">
            再次刷新订单
          </el-button>
        </div>
      </section>

      <!-- ALREADY-PAID (landed here for a PAID order) -->
      <section v-else-if="state === 'already-paid'" class="card">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="该订单已完成支付"
          description="无需重复支付。下方为支付凭证。"
        />

        <template v-if="payment">
          <el-divider />
          <h2 class="section-title">支付凭证</h2>
          <div class="summary-row">
            <span class="row-label">支付单号</span>
            <span class="row-value mono">{{ payment.paymentNo }}</span>
          </div>
          <div class="summary-row">
            <span class="row-label">支付渠道</span>
            <span class="row-value">{{ payment.channel }}</span>
          </div>
          <div class="summary-row">
            <span class="row-label">支付金额</span>
            <span class="row-value">¥{{ payment.amount.toFixed(2) }}</span>
          </div>
          <div class="summary-row">
            <span class="row-label">支付时间</span>
            <span class="row-value">{{ formatDateTime(payment.paidAt) }}</span>
          </div>
        </template>

        <div class="actions">
          <el-button type="primary" size="large" @click="goOrderDetail">
            查看订单详情
          </el-button>
        </div>
      </section>

      <!-- NOT-PAYABLE (CANCELLED / CLOSED / REFUNDED) -->
      <section v-else-if="state === 'not-payable'" class="card">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="订单状态不可支付"
          :description="`当前订单状态为 ${orderStatusMeta?.label ?? order.status}，无法发起支付。`"
        />
        <div class="actions">
          <el-button @click="goOrders">返回订单列表</el-button>
          <el-button type="primary" @click="goOrderDetail">查看订单详情</el-button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.page {
  min-height: 320px;
}

.payment {
  max-width: 720px;
  margin: 0 auto;
}

.mock-banner {
  margin-bottom: 16px;
}

.card {
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 24px 28px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}

.row-label {
  color: #606266;
}

.row-value {
  color: #1f2329;
  font-weight: 500;
  text-align: right;
  word-break: break-word;
}

.row-value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 400;
  color: #606266;
}

.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.total-label {
  font-size: 16px;
  color: #1f2329;
}

.total-amount {
  font-size: 28px;
  font-weight: 700;
  color: #f56c6c;
}

.submit-error {
  margin-bottom: 12px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.poll-hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: #909399;
  text-align: right;
}

.success-card {
  text-align: left;
}

.success-head {
  text-align: center;
  padding: 12px 0 6px;
}

.success-icon {
  color: #67c23a;
  background: #f0f9eb;
  border-radius: 50%;
  padding: 12px;
  margin-bottom: 8px;
}

.success-title {
  margin: 6px 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2329;
}

.success-sub {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
</style>
