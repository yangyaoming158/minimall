<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrder, cancelOrder } from '@/api/order'
import { ApiError, ErrorCode } from '@/types/api'
import type { Order } from '@/types/order'
import { getOrderStatusMeta } from '@/utils/order-status'

const route = useRoute()
const router = useRouter()

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

  try {
    await ElMessageBox.confirm(
      `确定要取消订单 ${target} 吗？此操作无法恢复。`,
      '取消订单',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
      },
    )
  } catch {
    return
  }

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

const statusMeta = computed(() =>
  order.value ? getOrderStatusMeta(order.value.status) : null,
)

const isPendingPayment = computed(
  () => order.value?.status === 'PENDING_PAYMENT',
)

const isPaid = computed(() => order.value?.status === 'PAID')

const isClosedLike = computed(
  () =>
    order.value?.status === 'CANCELLED' || order.value?.status === 'CLOSED',
)

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function formatAmount(value: number): string {
  return `¥${value.toFixed(2)}`
}
</script>

<template>
  <section v-loading="loading" class="page">
    <!-- 404 -->
    <el-result
      v-if="loadError && loadError.kind === 'not-found'"
      icon="warning"
      title="订单不存在"
      :sub-title="loadError.message"
    >
      <template #extra>
        <el-button type="primary" @click="goList">返回订单列表</el-button>
      </template>
    </el-result>

    <!-- Generic error -->
    <el-result
      v-else-if="loadError && loadError.kind === 'generic'"
      icon="error"
      title="加载失败"
      :sub-title="loadError.message"
    >
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
        <el-button @click="goList">返回订单列表</el-button>
      </template>
    </el-result>

    <!-- Detail -->
    <div v-else-if="order" class="detail">
      <header class="detail-head">
        <div class="head-left">
          <h1 class="title">订单详情</h1>
          <span class="order-no">{{ order.orderNo }}</span>
        </div>
        <el-tag
          v-if="statusMeta"
          :type="statusMeta.type"
          size="large"
          effect="light"
        >
          {{ statusMeta.label }}
        </el-tag>
      </header>

      <section class="card">
        <h2 class="section-title">基本信息</h2>
        <div class="grid">
          <div class="cell">
            <span class="cell-label">下单时间</span>
            <span class="cell-value">{{ formatDateTime(order.createdAt) }}</span>
          </div>
          <div class="cell">
            <span class="cell-label">更新时间</span>
            <span class="cell-value">{{ formatDateTime(order.updatedAt) }}</span>
          </div>
          <div v-if="isPendingPayment" class="cell">
            <span class="cell-label">支付截止</span>
            <span class="cell-value">{{ formatDateTime(order.expireAt) }}</span>
          </div>
          <div v-if="isPaid" class="cell">
            <span class="cell-label">支付时间</span>
            <span class="cell-value">{{ formatDateTime(order.paidAt) }}</span>
          </div>
          <div v-if="isClosedLike" class="cell">
            <span class="cell-label">关闭时间</span>
            <span class="cell-value">{{ formatDateTime(order.closedAt) }}</span>
          </div>
        </div>
      </section>

      <section class="card">
        <h2 class="section-title">商品明细</h2>
        <div class="items">
          <div
            v-for="item in order.items"
            :key="item.productId"
            class="item-row"
          >
            <div class="item-main">
              <span class="item-name">{{ item.productName }}</span>
              <span class="item-id">{{ item.productId }}</span>
            </div>
            <div class="item-side">
              <span class="item-qty">× {{ item.quantity }}</span>
              <span class="item-unit">{{ formatAmount(item.unitPrice) }}</span>
              <span class="item-sub">{{ formatAmount(item.unitPrice * item.quantity) }}</span>
            </div>
          </div>
        </div>

        <el-divider />

        <div class="total-row">
          <span class="total-label">合计</span>
          <span class="total-amount">{{ formatAmount(order.totalAmount) }}</span>
        </div>
        <p class="total-hint">订单金额以后端结算为准</p>
      </section>

      <div class="actions">
        <template v-if="isPendingPayment">
          <el-button size="large" :loading="cancelling" @click="onCancel">
            取消订单
          </el-button>
          <el-button type="primary" size="large" @click="goPay">
            去支付
          </el-button>
        </template>
        <el-button v-else size="large" @click="goList">
          返回订单列表
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  min-height: 320px;
}

.detail {
  max-width: 820px;
  margin: 0 auto;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.head-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.order-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: #606266;
}

.card {
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 24px 28px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 24px;
}

.cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 14px;
}

.cell-label {
  color: #909399;
  font-size: 12px;
}

.cell-value {
  color: #1f2329;
}

.items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
}

.item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 15px;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #909399;
}

.item-side {
  display: flex;
  gap: 24px;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.item-qty {
  min-width: 48px;
  text-align: right;
}

.item-unit {
  min-width: 80px;
  text-align: right;
}

.item-sub {
  min-width: 96px;
  text-align: right;
  color: #1f2329;
  font-weight: 600;
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

.total-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
  text-align: right;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .item-side {
    gap: 12px;
  }
}
</style>
