<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProduct } from '@/api/product'
import { getInventory } from '@/api/inventory'
import { createOrder } from '@/api/order'
import { ApiError } from '@/types/api'
import type { Product } from '@/types/product'
import type { Inventory } from '@/types/inventory'
import ProductCover from '@/components/ProductCover.vue'
import ErrorState from '@/components/ErrorState.vue'
import Pill from '@/components/atoms/Pill.vue'
import PriceText from '@/components/atoms/PriceText.vue'
import DotStatus from '@/components/atoms/DotStatus.vue'
import Button from '@/components/atoms/Button.vue'
import Hairline from '@/components/atoms/Hairline.vue'
import Notice from '@/components/atoms/Notice.vue'
import Skeleton from '@/components/atoms/Skeleton.vue'
import SkeletonText from '@/components/atoms/SkeletonText.vue'

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
// same checkout attempt. Re-entering /checkout OR changing the productId /
// quantity query creates a fresh attempt and therefore a fresh key. Retries
// of a failed submit, however, MUST reuse the same key.
const idempotencyKey = ref<string>('')

// Monotonic counter that "owns" the currently-active load attempt. Every
// async function captures the value at entry and re-checks it after each
// await; a mismatch means the query string changed (or the component
// unmounted) and any pending writes must be dropped.
let activeRunId = 0

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
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`
}

function resetForNewRun(): void {
  product.value = null
  inventory.value = null
  productError.value = null
  inventoryError.value = null
  submitError.value = null
  submitting.value = false
}

async function loadProduct(myRun: number): Promise<void> {
  if (!productIdParam.value) return
  productError.value = null
  try {
    const p = await getProduct(productIdParam.value)
    if (myRun !== activeRunId) return
    product.value = p
  } catch (err) {
    if (myRun !== activeRunId) return
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

async function loadInventory(myRun?: number): Promise<void> {
  if (!productIdParam.value) return
  const run = myRun ?? activeRunId
  inventoryError.value = null
  inventoryLoading.value = true
  try {
    const inv = await getInventory(productIdParam.value)
    if (run !== activeRunId) return
    inventory.value = inv
  } catch (err) {
    if (run !== activeRunId) return
    inventory.value = null
    inventoryError.value = err instanceof Error ? err.message : '加载库存失败'
  } finally {
    if (run === activeRunId) {
      inventoryLoading.value = false
    }
  }
}

async function loadAll(): Promise<void> {
  if (!paramsValid.value) return
  activeRunId += 1
  const myRun = activeRunId
  loading.value = true
  await Promise.allSettled([loadProduct(myRun), loadInventory(myRun)])
  if (myRun !== activeRunId) return
  loading.value = false
}

onMounted(() => {
  idempotencyKey.value = generateKey()
  loadAll()
})

// Bumping activeRunId on unmount makes every in-flight await bail before
// writing to a torn-down component.
onBeforeUnmount(() => {
  activeRunId += 1
})

watch(
  [productIdParam, quantityParam],
  ([nextPid, nextQty], [prevPid, prevQty]) => {
    if (nextPid === prevPid && nextQty === prevQty) return
    activeRunId += 1
    resetForNewRun()
    idempotencyKey.value = generateKey()
    if (paramsValid.value) {
      loadAll()
    } else {
      loading.value = false
    }
  },
)

const isOffShelf = computed(() => product.value?.status === 'OFF_SHELF')

const totalAmount = computed(() => {
  if (!product.value || quantityParam.value === null) return 0
  return product.value.price * quantityParam.value
})

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

type StockTone = 'success' | 'danger' | 'neutral'
const stockChip = computed<{ text: string; tone: StockTone } | null>(() => {
  if (!inventory.value) return null
  switch (inventory.value.stockState) {
    case 'IN_STOCK':
      return { text: '有货', tone: 'success' }
    case 'OUT_OF_STOCK':
      return { text: '无货', tone: 'danger' }
    case 'INACTIVE':
      return { text: '未上架', tone: 'neutral' }
    default:
      return null
  }
})

const disabledHint = computed(() => {
  if (!product.value) return ''
  if (isOffShelf.value) return '该商品已下架,无法提交订单'
  if (!inventory.value) return '库存信息暂不可用,无法提交订单'
  if (inventory.value.stockState === 'INACTIVE') return '该商品未激活库存,无法提交订单'
  if (
    inventory.value.stockState === 'OUT_OF_STOCK' ||
    inventory.value.availableStock <= 0
  ) {
    return '库存不足,无法提交订单'
  }
  if (!quantityWithinStock.value && quantityParam.value !== null) {
    return `购买数量需为 1 ~ ${inventory.value.availableStock} 之间的整数(当前:${quantityParam.value})`
  }
  return ''
})

async function onSubmit(): Promise<void> {
  if (!canSubmit.value || !product.value || quantityParam.value === null) return

  if (product.value.productId !== productIdParam.value) return

  const myRun = activeRunId
  submitting.value = true
  submitError.value = null

  try {
    const res = await createOrder({
      productId: product.value.productId,
      quantity: quantityParam.value,
      idempotencyKey: idempotencyKey.value,
    })
    if (myRun !== activeRunId) return
    router.replace({ name: 'order-detail', params: { orderNo: res.orderNo } })
  } catch (err) {
    if (myRun !== activeRunId) return
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
  <section class="ck">
    <ErrorState
      v-if="!paramsValid"
      tone="error"
      title="参数无效"
      description="请从商品详情页发起结算"
      :show-home="true"
      home-label="返回商品列表"
      @home="goProductList"
    />

    <ErrorState
      v-else-if="productError && productError.kind === 'not-found'"
      tone="notfound"
      title="商品不存在"
      :description="productError.message"
      :show-home="true"
      home-label="返回商品列表"
      @home="goProductList"
    />

    <ErrorState
      v-else-if="productError && productError.kind === 'generic'"
      tone="error"
      title="加载失败"
      :description="productError.message"
      :show-retry="true"
      :retry-loading="loading"
      :show-home="true"
      home-label="返回商品列表"
      @retry="loadAll"
      @home="goProductList"
    />

    <div v-else-if="loading && !product" class="ck__shell">
      <div class="ck__head">
        <Skeleton :height="'28px'" :width="'120px'" block />
      </div>
      <div class="ck__card">
        <div class="ck__product-row">
          <Skeleton :width="'88px'" :height="'88px'" radius="md" />
          <div class="ck__product-meta">
            <Skeleton :height="'18px'" :width="'70%'" block />
            <Skeleton :height="'14px'" :width="'40%'" block />
          </div>
        </div>
        <Hairline />
        <SkeletonText :lines="2" :line-height="'14px'" :gap="'10px'" />
        <Hairline />
        <Skeleton :height="'40px'" :width="'40%'" block />
      </div>
    </div>

    <div v-else-if="product" class="ck__shell">
      <div class="ck__head">
        <Button variant="text" size="sm" @click="goBack">
          ← 返回商品详情
        </Button>
        <h1 class="ck__title">订单确认</h1>
      </div>

      <div class="ck__card">
        <div class="ck__product-row">
          <div class="ck__cover">
            <ProductCover
              :product-id="product.productId"
              :name="product.name"
              aspect="1:1"
              grade="list"
              size="full"
            />
          </div>
          <div class="ck__product-meta">
            <div class="ck__name-row">
              <h2 class="ck__name" :title="product.name">{{ product.name }}</h2>
              <Pill :tone="isOffShelf ? 'neutral' : 'success'" soft>
                {{ isOffShelf ? '已下架' : '在售' }}
              </Pill>
            </div>
            <div class="ck__sku">
              <span class="ck__sku-label">商品编号</span>
              <span class="ck__sku-value">{{ product.productId }}</span>
            </div>
            <div class="ck__unit">
              <span class="ck__unit-label">单价</span>
              <PriceText :amount="product.price" size="md" />
              <span class="ck__qty">× {{ quantityParam }}</span>
            </div>
          </div>
        </div>

        <Hairline />

        <div class="ck__stock">
          <span class="ck__stock-label">库存确认</span>
          <Notice v-if="inventoryError" tone="warn" title="库存信息加载失败">
            <div class="ck__stock-error">
              <span>{{ inventoryError }}</span>
              <Button
                variant="ghost"
                size="sm"
                :loading="inventoryLoading"
                @click="loadInventory()"
              >
                重试
              </Button>
            </div>
          </Notice>
          <div v-else-if="inventory && stockChip" class="ck__stock-line">
            <DotStatus :tone="stockChip.tone">{{ stockChip.text }}</DotStatus>
            <span class="ck__stock-count">
              可售库存 <strong>{{ inventory.availableStock }}</strong>
            </span>
          </div>
          <Skeleton v-else :height="'14px'" :width="'120px'" />
        </div>

        <Hairline />

        <div class="ck__total">
          <span class="ck__total-label">合计</span>
          <PriceText :amount="totalAmount" size="xl" />
        </div>
        <p class="ck__total-hint">订单最终金额以后端结算为准</p>
      </div>

      <Notice
        v-if="submitError"
        tone="danger"
        title="订单提交失败"
        class="ck__submit-error"
      >
        {{ submitError }}
      </Notice>

      <div class="ck__actions">
        <Button
          variant="ghost"
          size="lg"
          :disabled="submitting"
          @click="goBack"
        >
          取消
        </Button>
        <Button
          variant="primary"
          size="lg"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="onSubmit"
        >
          确认下单
        </Button>
      </div>

      <p v-if="disabledHint && !submitting" class="ck__hint">{{ disabledHint }}</p>
    </div>
  </section>
</template>

<style scoped>
.ck {
  padding: 24px 0 40px;
}

.ck__shell {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.ck__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ck__title {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  font-weight: var(--t-h1-weight);
  line-height: var(--t-h1-lh);
  color: var(--ink-900);
}

.ck__card {
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-md);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.ck__product-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.ck__cover {
  flex-shrink: 0;
  width: 88px;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--canvas-darker);
  border: 1px solid var(--ink-100);
}

.ck__cover :deep(.cover) {
  width: 100%;
  border-radius: 0;
}

.ck__product-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ck__name-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ck__name {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-h2-size);
  font-weight: var(--t-h2-weight);
  line-height: 1.35;
  color: var(--ink-900);
  word-break: break-word;
  flex: 1;
  min-width: 0;
}

.ck__sku {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
}

.ck__sku-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--ink-700);
}

.ck__unit {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}

.ck__unit-label {
  color: var(--ink-500);
}

.ck__qty {
  color: var(--ink-700);
  font-weight: 500;
}

.ck__stock {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ck__stock-label {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-700);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.ck__stock-line {
  display: flex;
  align-items: center;
  gap: 16px;
}

.ck__stock-count {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}

.ck__stock-count strong {
  color: var(--ink-900);
  font-weight: 600;
}

.ck__stock-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.ck__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.ck__total-label {
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--ink-500);
}

.ck__total-hint {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  text-align: right;
}

.ck__submit-error {
  margin: 0;
}

.ck__actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.ck__actions .btn {
  min-width: 140px;
}

.ck__hint {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--warn, var(--ink-500));
  text-align: right;
}

@media (max-width: 640px) {
  .ck__product-row {
    flex-direction: column;
  }
  .ck__cover {
    width: 100%;
    height: auto;
    aspect-ratio: 4 / 3;
  }
  .ck__actions {
    flex-direction: column-reverse;
  }
  .ck__actions .btn {
    width: 100%;
  }
}
</style>
