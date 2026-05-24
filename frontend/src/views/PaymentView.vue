<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder } from '@/api/order'
import { getPayment, payOrder } from '@/api/payment'
import { ApiError, ErrorCode } from '@/types/api'
import type { Order, OrderStatus } from '@/types/order'
import type { Payment } from '@/types/payment'
import Button from '@/components/atoms/Button.vue'
import Hairline from '@/components/atoms/Hairline.vue'
import Notice from '@/components/atoms/Notice.vue'
import Skeleton from '@/components/atoms/Skeleton.vue'
import ErrorState from '@/components/ErrorState.vue'
import OrderStepper from '@/components/OrderStepper.vue'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo ?? ''))

const POLL_ATTEMPTS = 5
const POLL_INTERVAL_MS = 800
const TWEEN_MS = 420

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

// Reused for every retry of the same in-page attempt; reset only when the
// component remounts OR when route param orderNo changes (see watch below).
const idempotencyKey = ref('')

// Monotonic counter that "owns" the currently-active load attempt. Every
// async function captures the value at entry and re-checks it after each
// await; a mismatch means the user navigated to a new orderNo (or the
// component unmounted) and any pending writes must be dropped to avoid
// clobbering the new run's state.
let activeRunId = 0

function generateKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function resetForNewRun(): void {
  order.value = null
  payment.value = null
  submitError.value = null
  errorMessage.value = ''
  state.value = 'loading'
}

async function loadInitial(): Promise<void> {
  activeRunId += 1
  const myRun = activeRunId
  resetForNewRun()
  try {
    const o = await getOrder(orderNo.value)
    if (myRun !== activeRunId) return
    order.value = o
    if (o.status === 'PAID') {
      // Backend may or may not have a payment row yet visible to GET; tolerate
      // a missing payment record so the page still renders.
      try {
        const p = await getPayment(orderNo.value)
        if (myRun !== activeRunId) return
        payment.value = p
      } catch {
        if (myRun !== activeRunId) return
        payment.value = null
      }
      state.value = 'already-paid'
    } else if (o.status === 'PENDING_PAYMENT') {
      state.value = 'ready'
    } else {
      state.value = 'not-payable'
    }
  } catch (err) {
    if (myRun !== activeRunId) return
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

// Bumping activeRunId in onBeforeUnmount guarantees that any in-flight await
// resolving after teardown finds (myRun !== activeRunId) and bails before
// writing to a torn-down component.
onBeforeUnmount(() => {
  activeRunId += 1
  cancelTween()
})

// Same-component navigation: /payments/A -> /payments/B reuses the instance.
// Reset everything (order, payment, errors, state, idempotencyKey) and start
// a fresh load for the new orderNo. Bumping activeRunId implicitly happens
// inside loadInitial, which invalidates any A-run that's still awaiting.
watch(
  () => orderNo.value,
  (next, prev) => {
    if (next && next !== prev) {
      idempotencyKey.value = generateKey()
      loadInitial()
    }
  },
)

// After the /pay POST returns SUCCESS, the order-service still has to consume
// the RabbitMQ PaymentSuccessEvent before the order flips to PAID. Poll a few
// times so the user sees the final state in the same flow.
async function pollOrderUntilPaid(myRun: number): Promise<boolean> {
  for (let i = 0; i < POLL_ATTEMPTS; i += 1) {
    await sleep(POLL_INTERVAL_MS)
    if (myRun !== activeRunId) return false
    try {
      const o = await getOrder(orderNo.value)
      if (myRun !== activeRunId) return false
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

async function refreshPaymentReceipt(myRun: number): Promise<void> {
  try {
    const p = await getPayment(orderNo.value)
    if (myRun !== activeRunId) return
    payment.value = p
  } catch {
    if (myRun !== activeRunId) return
    payment.value = null
  }
}

async function onSubmit(): Promise<void> {
  if (state.value !== 'ready' || !order.value) return

  const myRun = activeRunId
  state.value = 'submitting'
  submitError.value = null

  try {
    await payOrder(orderNo.value, {
      channel: 'MOCK',
      idempotencyKey: idempotencyKey.value,
    })
  } catch (err) {
    if (myRun !== activeRunId) return
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

  if (myRun !== activeRunId) return

  // Pay accepted. Now wait for order-service to consume the async event.
  const paid = await pollOrderUntilPaid(myRun)
  if (myRun !== activeRunId) return

  if (paid) {
    await refreshPaymentReceipt(myRun)
    if (myRun !== activeRunId) return
    state.value = 'success'
  } else {
    state.value = 'success-pending'
  }
}

async function onManualRefresh(): Promise<void> {
  if (state.value !== 'success-pending') return
  const myRun = activeRunId
  state.value = 'submitting'
  const paid = await pollOrderUntilPaid(myRun)
  if (myRun !== activeRunId) return
  if (paid) {
    await refreshPaymentReceipt(myRun)
    if (myRun !== activeRunId) return
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

const submitting = computed(() => state.value === 'submitting')

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
  REFUNDED: '已退款',
}

const notPayableLabel = computed(() =>
  order.value ? STATUS_LABEL[order.value.status] ?? order.value.status : '',
)

// Amount tween: animate from 0 to the order's totalAmount over TWEEN_MS.
// Honors prefers-reduced-motion by snapping straight to the final value.
const displayAmount = ref(0)
let tweenRaf: number | null = null

function cancelTween(): void {
  if (tweenRaf !== null) {
    cancelAnimationFrame(tweenRaf)
    tweenRaf = null
  }
}

function startAmountTween(): void {
  cancelTween()
  if (!order.value) return
  const target = order.value.totalAmount
  const reduced =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reduced) {
    displayAmount.value = target
    return
  }
  displayAmount.value = 0
  const start = performance.now()
  const tick = (now: number): void => {
    const t = Math.min(1, (now - start) / TWEEN_MS)
    // ease-out cubic
    const eased = 1 - Math.pow(1 - t, 3)
    displayAmount.value = target * eased
    if (t < 1) {
      tweenRaf = requestAnimationFrame(tick)
    } else {
      tweenRaf = null
      displayAmount.value = target
    }
  }
  tweenRaf = requestAnimationFrame(tick)
}

// Trigger the tween when we first land on `ready` for a given order. Don't
// re-tween when state flips to `submitting` and back to `ready` after a retry
// — the final amount is already in view by then.
watch(
  () => state.value,
  (s, prev) => {
    if (s === 'ready' && prev !== 'submitting') {
      startAmountTween()
    } else if (s === 'already-paid' && order.value) {
      // Skip the tween for already-paid: per spec, the success circle is
      // shown without animation since it's not a fresh transaction.
      displayAmount.value = order.value.totalAmount
    }
  },
)

const displayAmountText = computed(() => `¥${displayAmount.value.toFixed(2)}`)

const ctaLabel = computed(() => `确认支付 ${amountText.value}`)
</script>

<template>
  <section class="page">
    <!-- Initial load skeleton -->
    <div v-if="state === 'loading'" class="payment" aria-busy="true">
      <Skeleton width="160px" height="14px" block />
      <div class="pay__skel-stepper">
        <Skeleton width="100%" height="24px" radius="full" block />
      </div>
      <Skeleton width="100%" height="44px" radius="md" block />
      <div class="pay__skel-amount">
        <Skeleton width="80px" height="12px" />
        <Skeleton width="220px" height="56px" />
      </div>
      <Skeleton width="100%" height="80px" radius="md" block />
      <Skeleton width="100%" height="48px" radius="md" block />
    </div>

    <!-- Errors -->
    <ErrorState
      v-else-if="state === 'error-not-found'"
      tone="notfound"
      title="订单不存在"
      :description="errorMessage"
      show-home
      home-label="返回订单列表"
      @home="goOrders"
    />
    <ErrorState
      v-else-if="state === 'error-generic'"
      tone="error"
      title="加载失败"
      :description="errorMessage"
      show-retry
      retry-label="重试"
      show-home
      home-label="返回订单列表"
      @retry="loadInitial"
      @home="goOrders"
    />

    <!-- Main -->
    <div v-else-if="order" class="payment">
      <div class="pay__crumb">
        <Button variant="text" size="sm" @click="goOrderDetail">
          ← 订单详情 · {{ order.orderNo }}
        </Button>
      </div>

      <div class="pay__stepper">
        <OrderStepper
          :status="order.status"
          :closed-at="order.closedAt"
          :updated-at="order.updatedAt"
        />
      </div>

      <Notice tone="neutral">
        Demo payment · 模拟链路，不会发生真实扣款
      </Notice>

      <!-- READY / SUBMITTING -->
      <template v-if="state === 'ready' || state === 'submitting'">
        <div class="amount">
          <span class="amount__dot" aria-hidden="true" />
          <span class="amount__caption">应付金额</span>
          <span class="amount__value">{{ displayAmountText }}</span>
        </div>

        <button
          type="button"
          class="method"
          aria-pressed="true"
          aria-label="模拟支付（演示渠道，立即标记订单为已支付）"
        >
          <span class="method__badge">Mock</span>
          <span class="method__body">
            <span class="method__title">模拟支付</span>
            <span class="method__sub">演示渠道 · 立即标记订单为已支付</span>
          </span>
          <svg class="method__check" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" fill="none" stroke="var(--accent-terracotta)" stroke-width="1.5" />
            <polyline points="7.5 12 11 15.5 16.5 9" fill="none" stroke="var(--accent-terracotta)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>

        <Notice
          v-if="submitError"
          tone="danger"
          title="支付失败"
        >
          {{ submitError }}
        </Notice>

        <Button
          variant="primary"
          size="lg"
          full
          :loading="submitting"
          @click="onSubmit"
        >
          {{ ctaLabel }}
        </Button>

        <p v-if="submitting" class="poll-hint">正在等待订单状态更新…</p>
      </template>

      <!-- SUCCESS -->
      <section v-else-if="state === 'success'" class="terminal">
        <div class="terminal__circle terminal__circle--success" aria-hidden="true">
          <svg viewBox="0 0 28 28" width="28" height="28">
            <polyline
              class="terminal__check"
              points="6 14.5 12 20 22 8.5"
              fill="none"
              stroke="var(--accent-terracotta)"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <h2 class="terminal__title">支付成功</h2>
        <p class="terminal__sub">模拟支付链路 · 订单已标记为已支付</p>

        <template v-if="payment">
          <Hairline />
          <dl class="receipt">
            <div class="receipt__row">
              <dt class="receipt__label">支付单号</dt>
              <dd class="receipt__value receipt__value--mono">{{ payment.paymentNo }}</dd>
            </div>
            <div class="receipt__row">
              <dt class="receipt__label">支付渠道</dt>
              <dd class="receipt__value">{{ payment.channel }}</dd>
            </div>
            <div class="receipt__row">
              <dt class="receipt__label">支付金额</dt>
              <dd class="receipt__value">¥{{ payment.amount.toFixed(2) }}</dd>
            </div>
            <div class="receipt__row">
              <dt class="receipt__label">支付时间</dt>
              <dd class="receipt__value">{{ formatDateTime(payment.paidAt) }}</dd>
            </div>
          </dl>
        </template>

        <div class="terminal__actions terminal__actions--split">
          <Button variant="ghost" size="lg" @click="goShopping">继续逛逛</Button>
          <Button variant="primary" size="lg" @click="goOrderDetail">查看订单详情</Button>
        </div>
      </section>

      <!-- SUCCESS-PENDING -->
      <section v-else-if="state === 'success-pending'" class="terminal">
        <div class="terminal__circle terminal__circle--neutral" aria-hidden="true">
          <svg viewBox="0 0 28 28" width="28" height="28">
            <path
              d="M 8 5 L 20 5 M 8 23 L 20 23 M 9 5 L 9 9 Q 9 11 14 14 Q 19 11 19 9 L 19 5 M 9 23 L 9 19 Q 9 17 14 14 Q 19 17 19 19 L 19 23"
              fill="none"
              stroke="var(--ink-700)"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <h2 class="terminal__title">支付已提交</h2>
        <p class="terminal__sub">支付请求已被受理，订单状态稍后更新。</p>

        <div class="terminal__actions terminal__actions--stack">
          <Button variant="primary" size="lg" full @click="onManualRefresh">
            再次刷新订单
          </Button>
          <Button variant="text" size="sm" @click="goOrderDetail">
            返回订单详情
          </Button>
        </div>
      </section>

      <!-- ALREADY-PAID -->
      <section v-else-if="state === 'already-paid'" class="terminal">
        <div class="terminal__circle terminal__circle--success terminal__circle--static" aria-hidden="true">
          <svg viewBox="0 0 28 28" width="28" height="28">
            <polyline
              points="6 14.5 12 20 22 8.5"
              fill="none"
              stroke="var(--accent-terracotta)"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <h2 class="terminal__title">该订单已完成支付</h2>
        <p class="terminal__sub">无需重复支付。下方为支付凭证。</p>

        <template v-if="payment">
          <Hairline />
          <dl class="receipt">
            <div class="receipt__row">
              <dt class="receipt__label">支付单号</dt>
              <dd class="receipt__value receipt__value--mono">{{ payment.paymentNo }}</dd>
            </div>
            <div class="receipt__row">
              <dt class="receipt__label">支付渠道</dt>
              <dd class="receipt__value">{{ payment.channel }}</dd>
            </div>
            <div class="receipt__row">
              <dt class="receipt__label">支付金额</dt>
              <dd class="receipt__value">¥{{ payment.amount.toFixed(2) }}</dd>
            </div>
            <div class="receipt__row">
              <dt class="receipt__label">支付时间</dt>
              <dd class="receipt__value">{{ formatDateTime(payment.paidAt) }}</dd>
            </div>
          </dl>
        </template>

        <div class="terminal__actions terminal__actions--single">
          <Button variant="primary" size="lg" full @click="goOrderDetail">
            查看订单详情
          </Button>
        </div>
      </section>

      <!-- NOT-PAYABLE -->
      <section v-else-if="state === 'not-payable'" class="terminal">
        <div class="terminal__circle terminal__circle--neutral" aria-hidden="true">
          <svg viewBox="0 0 28 28" width="28" height="28">
            <circle cx="14" cy="14" r="10" fill="none" stroke="var(--ink-700)" stroke-width="1.8" />
            <line x1="14" y1="9" x2="14" y2="15" stroke="var(--ink-700)" stroke-width="2" stroke-linecap="round" />
            <circle cx="14" cy="19" r="1.2" fill="var(--ink-700)" />
          </svg>
        </div>
        <h2 class="terminal__title">无法发起支付</h2>
        <p class="terminal__sub">
          当前订单状态为「{{ notPayableLabel }}」，无法发起支付。
        </p>

        <div class="terminal__actions terminal__actions--split">
          <Button variant="ghost" size="lg" @click="goOrders">返回订单列表</Button>
          <Button variant="primary" size="lg" @click="goOrderDetail">查看订单详情</Button>
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
  max-width: 480px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pay__crumb {
  margin-left: -4px;
}

.pay__stepper {
  margin-top: 8px;
}

.pay__skel-stepper {
  margin-top: 4px;
}

.pay__skel-amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin: 24px 0;
}

.amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  margin-top: 24px;
  margin-bottom: 16px;
}

.amount__dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-terracotta);
  margin-bottom: 4px;
}

.amount__caption {
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-500);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.amount__value {
  font-family: var(--font-display);
  font-size: var(--t-amount-display-size);
  font-weight: var(--t-amount-display-weight);
  line-height: 1;
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.method {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 16px;
  border: 2px solid var(--ink-900);
  border-radius: var(--radius-md);
  background: var(--surface);
  text-align: left;
  cursor: default;
  margin-top: 16px;
}

.method__badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--ink-300);
  letter-spacing: 0.04em;
  padding: 2px 8px;
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-sm);
}

.method__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.method__title {
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-900);
}

.method__sub {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  line-height: 1.4;
}

.method__check {
  flex-shrink: 0;
}

.poll-hint {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  text-align: center;
}

/* Terminal screens (success / success-pending / already-paid / not-payable) */
.terminal {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-top: 16px;
}

.terminal__circle {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.terminal__circle--success {
  background: var(--accent-terracotta-soft);
}

.terminal__circle--neutral {
  background: var(--canvas-darker);
}

.terminal__check {
  stroke-dasharray: 36;
  stroke-dashoffset: 36;
  animation: terminal-draw 0.6s ease-out 0.1s forwards;
}

@keyframes terminal-draw {
  to {
    stroke-dashoffset: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .terminal__check {
    animation: none;
    stroke-dashoffset: 0;
  }
}

.terminal__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--t-display-size);
  font-weight: var(--t-display-weight);
  line-height: var(--t-display-lh);
  letter-spacing: var(--t-display-track);
  color: var(--ink-900);
}

.terminal__sub {
  margin: 4px 0 0;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
  line-height: 1.5;
}

.receipt {
  width: 100%;
  margin: 16px 0 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
}

.receipt__row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  margin: 0;
}

.receipt__label {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
  margin: 0;
}

.receipt__value {
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--ink-900);
  margin: 0;
  text-align: right;
  word-break: break-all;
}

.receipt__value--mono {
  font-family: var(--font-mono);
  font-size: 13px;
}

.terminal__actions {
  width: 100%;
  margin-top: 32px;
  display: grid;
  gap: 12px;
}

.terminal__actions--split {
  grid-template-columns: 1fr 1fr;
}

.terminal__actions--single {
  grid-template-columns: 1fr;
}

.terminal__actions--stack {
  grid-template-columns: 1fr;
  justify-items: center;
}

.terminal__actions--stack > :last-child {
  margin-top: -4px;
}

@media (max-width: 540px) {
  .terminal__actions--split {
    grid-template-columns: 1fr;
    grid-auto-flow: row;
  }
  .terminal__actions--split > :first-child {
    grid-row: 2;
  }
}
</style>
