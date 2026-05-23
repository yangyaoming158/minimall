<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProduct } from '@/api/product'
import { getInventory } from '@/api/inventory'
import { createOrder } from '@/api/order'
import { ApiError } from '@/types/api'
import type { Product } from '@/types/product'
import type { Inventory } from '@/types/inventory'

const route = useRoute()
const router = useRouter()

const product = ref<Product | null>(null)
const inventory = ref<Inventory | null>(null)

const loading = ref(false)
const productError = ref<{ kind: 'not-found' | 'generic'; message: string } | null>(null)
const inventoryError = ref<string | null>(null)
const inventoryLoading = ref(false)

const submitting = ref(false)
const submitError = ref<string | null>(null)

// Generated once when entering the page and reused for every retry of the
// same checkout attempt. Re-entering /checkout creates a new component
// instance and therefore a new key (per Task 7 contract).
const idempotencyKey = ref<string>('')

const productIdParam = computed(() => {
  const v = route.query.productId
  return typeof v === 'string' && v.length > 0 ? v : null
})

const quantityParam = computed(() => {
  const raw = route.query.quantity
  if (typeof raw !== 'string') return null
  if (!/^\d+$/.test(raw)) return null
  const n = parseInt(raw, 10)
  return Number.isInteger(n) && n >= 1 ? n : null
})

const paramsValid = computed(() => productIdParam.value !== null && quantityParam.value !== null)

function generateKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // Fallback: timestamp + random bits. Sufficient as an idempotency token
  // for the few browsers that still lack crypto.randomUUID.
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`
}

async function loadProduct(): Promise<void> {
  if (!productIdParam.value) return
  productError.value = null
  try {
    product.value = await getProduct(productIdParam.value)
  } catch (err) {
    product.value = null
    if (err instanceof ApiError && (err.httpStatus === 404 || err.code === '40400')) {
      productError.value = { kind: 'not-found', message: '商品不存在或已被移除' }
    } else {
      productError.value = {
        kind: 'generic',
        message: err instanceof Error ? err.message : '加载商品失败',
      }
    }
  }
}

async function loadInventory(): Promise<void> {
  if (!productIdParam.value) return
  inventoryError.value = null
  inventoryLoading.value = true
  try {
    inventory.value = await getInventory(productIdParam.value)
  } catch (err) {
    inventory.value = null
    inventoryError.value = err instanceof Error ? err.message : '加载库存失败'
  } finally {
    inventoryLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  if (!paramsValid.value) return
  loading.value = true
  await Promise.allSettled([loadProduct(), loadInventory()])
  loading.value = false
}

onMounted(() => {
  idempotencyKey.value = generateKey()
  loadAll()
})

const isOffShelf = computed(() => product.value?.status === 'OFF_SHELF')

const unitPriceText = computed(() =>
  product.value ? `¥${product.value.price.toFixed(2)}` : '',
)

const totalAmount = computed(() => {
  if (!product.value || quantityParam.value === null) return 0
  // Display only - the backend recomputes the authoritative total.
  return product.value.price * quantityParam.value
})

const totalText = computed(() => `¥${totalAmount.value.toFixed(2)}`)

const quantityWithinStock = computed(() => {
  if (!inventory.value || quantityParam.value === null) return false
  return quantityParam.value >= 1 && quantityParam.value <= inventory.value.availableStock
})

const canSubmit = computed(() => {
  if (submitting.value) return false
  if (!product.value || !inventory.value) return false
  if (isOffShelf.value) return false
  if (inventory.value.stockState !== 'IN_STOCK') return false
  return quantityWithinStock.value
})

const disabledHint = computed(() => {
  if (!product.value) return ''
  if (isOffShelf.value) return '该商品已下架，无法提交订单'
  if (!inventory.value) return '库存信息暂不可用，无法提交订单'
  if (inventory.value.stockState === 'INACTIVE') return '该商品未激活库存，无法提交订单'
  if (
    inventory.value.stockState === 'OUT_OF_STOCK' ||
    inventory.value.availableStock <= 0
  ) {
    return '库存不足，无法提交订单'
  }
  if (!quantityWithinStock.value && quantityParam.value !== null) {
    return `购买数量需为 1 ~ ${inventory.value.availableStock} 之间的整数（当前：${quantityParam.value}）`
  }
  return ''
})

async function onSubmit(): Promise<void> {
  if (!canSubmit.value || !product.value || quantityParam.value === null) return

  submitting.value = true
  submitError.value = null

  try {
    const res = await createOrder({
      productId: product.value.productId,
      quantity: quantityParam.value,
      idempotencyKey: idempotencyKey.value,
    })
    // replace (not push) so the back button does not return to this page
    // and risk a stale double-submit.
    router.replace({ name: 'order-detail', params: { orderNo: res.orderNo } })
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : '订单创建失败'
    submitting.value = false
    // idempotencyKey is intentionally NOT regenerated - retry must reuse it.
  }
}

function goBack(): void {
  if (productIdParam.value) {
    router.push({ name: 'product-detail', params: { productId: productIdParam.value } })
  } else {
    router.push({ name: 'products' })
  }
}

function goProductList(): void {
  router.push({ name: 'products' })
}
</script>

<template>
  <section v-loading="loading" class="page">
    <!-- Bad / missing query params -->
    <el-result
      v-if="!paramsValid"
      icon="warning"
      title="参数无效"
      sub-title="请从商品详情页发起结算"
    >
      <template #extra>
        <el-button type="primary" @click="goProductList">返回商品列表</el-button>
      </template>
    </el-result>

    <!-- Product 404 -->
    <el-result
      v-else-if="productError && productError.kind === 'not-found'"
      icon="warning"
      title="商品不存在"
      :sub-title="productError.message"
    >
      <template #extra>
        <el-button type="primary" @click="goProductList">返回商品列表</el-button>
      </template>
    </el-result>

    <!-- Product generic load error -->
    <el-result
      v-else-if="productError && productError.kind === 'generic'"
      icon="error"
      title="加载失败"
      :sub-title="productError.message"
    >
      <template #extra>
        <el-button type="primary" @click="loadAll">重试</el-button>
        <el-button @click="goProductList">返回商品列表</el-button>
      </template>
    </el-result>

    <!-- Success layout -->
    <div v-else-if="product" class="checkout">
      <header class="checkout-head">
        <h1 class="title">订单确认</h1>
        <el-button link @click="goBack">← 返回商品详情</el-button>
      </header>

      <div class="summary-card">
        <h2 class="section-title">商品信息</h2>

        <div class="summary-row">
          <span class="row-label">商品名称</span>
          <span class="row-value">{{ product.name }}</span>
        </div>
        <div class="summary-row">
          <span class="row-label">商品编号</span>
          <span class="row-value mono">{{ product.productId }}</span>
        </div>
        <div class="summary-row">
          <span class="row-label">单价</span>
          <span class="row-value">{{ unitPriceText }}</span>
        </div>
        <div class="summary-row">
          <span class="row-label">数量</span>
          <span class="row-value">× {{ quantityParam }}</span>
        </div>

        <el-divider />

        <h2 class="section-title">库存确认</h2>

        <div v-if="inventoryError" class="inventory-error">
          <el-alert
            type="warning"
            :title="inventoryError"
            :closable="false"
            show-icon
          />
          <el-button
            class="retry-btn"
            size="small"
            :loading="inventoryLoading"
            @click="loadInventory"
          >
            重试加载库存
          </el-button>
        </div>

        <template v-else-if="inventory">
          <div class="summary-row">
            <span class="row-label">可售库存</span>
            <span class="row-value">{{ inventory.availableStock }}</span>
          </div>
        </template>

        <el-divider />

        <div class="total-row">
          <span class="total-label">合计</span>
          <span class="total-amount">{{ totalText }}</span>
        </div>
        <p class="total-hint">订单最终金额以后端结算为准</p>
      </div>

      <el-alert
        v-if="submitError"
        class="submit-error"
        type="error"
        :title="submitError"
        :closable="false"
        show-icon
      />

      <div class="actions">
        <el-button size="large" :disabled="submitting" @click="goBack">
          取消
        </el-button>
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : '确认下单' }}
        </el-button>
      </div>

      <p v-if="disabledHint && !submitting" class="disabled-hint">{{ disabledHint }}</p>
    </div>
  </section>
</template>

<style scoped>
.page {
  min-height: 320px;
}

.checkout {
  max-width: 720px;
  margin: 0 auto;
}

.checkout-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.summary-card {
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 28px;
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

.total-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
  text-align: right;
}

.inventory-error {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.retry-btn {
  align-self: flex-start;
}

.submit-error {
  margin-top: 16px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
}

.disabled-hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: #e6a23c;
  text-align: right;
}
</style>
