<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProduct } from '@/api/product'
import { getInventory } from '@/api/inventory'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'
import type { Product } from '@/types/product'
import type { Inventory } from '@/types/inventory'
import ProductCover from '@/components/ProductCover.vue'
import ErrorState from '@/components/ErrorState.vue'
import Pill from '@/components/atoms/Pill.vue'
import PriceText from '@/components/atoms/PriceText.vue'
import DotStatus from '@/components/atoms/DotStatus.vue'
import QuantityStepper from '@/components/atoms/QuantityStepper.vue'
import Button from '@/components/atoms/Button.vue'
import Hairline from '@/components/atoms/Hairline.vue'
import Notice from '@/components/atoms/Notice.vue'
import Skeleton from '@/components/atoms/Skeleton.vue'
import SkeletonText from '@/components/atoms/SkeletonText.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const productId = computed(() => String(route.params.productId ?? ''))

const product = ref<Product | null>(null)
const inventory = ref<Inventory | null>(null)

const loading = ref(false)
const productError = ref<{ kind: 'not-found' | 'generic'; message: string } | null>(null)
const inventoryError = ref<string | null>(null)
const inventoryLoading = ref(false)

const quantity = ref(1)

async function loadProduct(): Promise<void> {
  productError.value = null
  try {
    product.value = await getProduct(productId.value)
  } catch (err) {
    product.value = null
    if (err instanceof ApiError && (err.httpStatus === 404 || err.code === '40400')) {
      productError.value = { kind: 'not-found', message: '商品不存在或已被移除' }
    } else {
      const message = err instanceof Error ? err.message : '加载商品失败'
      productError.value = { kind: 'generic', message }
    }
  }
}

async function loadInventory(): Promise<void> {
  inventoryError.value = null
  inventoryLoading.value = true
  try {
    inventory.value = await getInventory(productId.value)
  } catch (err) {
    inventory.value = null
    inventoryError.value = err instanceof Error ? err.message : '加载库存失败'
  } finally {
    inventoryLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  if (!productId.value) {
    return
  }
  loading.value = true
  await Promise.allSettled([loadProduct(), loadInventory()])
  loading.value = false
}

// Clamp quantity so the stepper stays consistent with whatever stock value loaded.
watch(
  () => inventory.value?.availableStock ?? 0,
  (available) => {
    if (available <= 0) {
      quantity.value = 1
      return
    }
    if (quantity.value > available) {
      quantity.value = available
    }
    if (quantity.value < 1) {
      quantity.value = 1
    }
  },
)

// Reload when navigating between detail pages without unmounting.
watch(
  () => productId.value,
  (next, prev) => {
    if (next && next !== prev) {
      product.value = null
      inventory.value = null
      quantity.value = 1
      loadAll()
    }
  },
)

onMounted(loadAll)

const isOffShelf = computed(() => product.value?.status === 'OFF_SHELF')

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

const maxQuantity = computed(() => {
  const available = inventory.value?.availableStock ?? 0
  return available > 0 ? available : 1
})

const quantityValid = computed(() => {
  const q = quantity.value
  const available = inventory.value?.availableStock ?? 0
  return Number.isInteger(q) && q >= 1 && q <= available
})

const canOrder = computed(() => {
  if (!product.value || !inventory.value) return false
  if (isOffShelf.value) return false
  if (inventory.value.stockState !== 'IN_STOCK') return false
  return quantityValid.value
})

const stepperDisabled = computed(
  () => !inventory.value || isOffShelf.value || inventory.value.stockState !== 'IN_STOCK',
)

const disabledHint = computed(() => {
  if (!product.value) return ''
  if (isOffShelf.value) return '该商品已下架,暂不可下单'
  if (!inventory.value) return '库存信息暂不可用,无法下单'
  if (inventory.value.stockState === 'INACTIVE') return '该商品未激活库存,暂不可下单'
  if (inventory.value.stockState === 'OUT_OF_STOCK' || inventory.value.availableStock <= 0) {
    return '库存不足,暂不可下单'
  }
  if (!quantityValid.value) return `购买数量需为 1 ~ ${inventory.value.availableStock} 之间的整数`
  return ''
})

function onOrder(): void {
  if (!canOrder.value || !product.value) return

  if (!auth.isLoggedIn) {
    router.push({
      name: 'login',
      query: { redirect: route.fullPath },
    })
    return
  }

  router.push({
    name: 'checkout',
    query: {
      productId: product.value.productId,
      quantity: String(quantity.value),
    },
  })
}

function goBackToList(): void {
  router.push({ name: 'products' })
}
</script>

<template>
  <section class="pdetail">
    <ErrorState
      v-if="productError && productError.kind === 'not-found'"
      tone="notfound"
      title="商品不存在"
      :description="productError.message"
      :show-home="true"
      home-label="返回商品列表"
      @home="goBackToList"
    />

    <ErrorState
      v-else-if="productError && productError.kind === 'generic'"
      tone="error"
      title="加载失败"
      :description="productError.message"
      :show-retry="true"
      :retry-loading="loading"
      :show-home="true"
      home-label="返回列表"
      @retry="loadAll"
      @home="goBackToList"
    />

    <div v-else-if="loading && !product" class="pdetail__layout pdetail__layout--skeleton">
      <div class="pdetail__cover">
        <Skeleton :width="'100%'" :height="'auto'" radius="lg" block class="pdetail__cover-art" />
      </div>
      <div class="pdetail__side">
        <Skeleton :height="'18px'" :width="'72px'" block />
        <Skeleton :height="'34px'" :width="'80%'" block />
        <Skeleton :height="'40px'" :width="'45%'" block />
        <Hairline />
        <Skeleton :height="'16px'" :width="'60%'" block />
        <Skeleton :height="'40px'" :width="'40%'" block />
        <Skeleton :height="'48px'" :width="'100%'" block />
        <Skeleton :height="'48px'" :width="'100%'" block />
        <Hairline />
        <SkeletonText :lines="3" :line-height="'13px'" :gap="'8px'" />
      </div>
    </div>

    <div v-else-if="product" class="pdetail__layout">
      <div class="pdetail__cover">
        <ProductCover
          :product-id="product.productId"
          :name="product.name"
          aspect="4:5"
          grade="detail"
          size="full"
        />
      </div>

      <aside class="pdetail__side">
        <div class="pdetail__status">
          <Pill :tone="isOffShelf ? 'neutral' : 'success'" soft>
            {{ isOffShelf ? '已下架' : '在售' }}
          </Pill>
        </div>

        <h1 class="pdetail__title" :title="product.name">{{ product.name }}</h1>

        <div class="pdetail__price">
          <PriceText :amount="product.price" size="lg" />
        </div>

        <Hairline />

        <div class="pdetail__stock">
          <Notice v-if="inventoryError" tone="warn" title="库存信息加载失败">
            <div class="pdetail__stock-error">
              <span>{{ inventoryError }}</span>
              <Button
                variant="ghost"
                size="sm"
                :loading="inventoryLoading"
                @click="loadInventory"
              >
                重试
              </Button>
            </div>
          </Notice>

          <template v-else-if="inventory && stockChip">
            <DotStatus :tone="stockChip.tone">
              {{ stockChip.text }}
            </DotStatus>
            <span class="pdetail__stock-count">
              库存 <strong>{{ inventory.availableStock }}</strong>
            </span>
          </template>

          <template v-else-if="inventoryLoading">
            <Skeleton :height="'14px'" :width="'80px'" />
          </template>
        </div>

        <div class="pdetail__qty">
          <span class="pdetail__qty-label">数量</span>
          <QuantityStepper
            v-model="quantity"
            :min="1"
            :max="maxQuantity"
            :disabled="stepperDisabled"
          />
        </div>

        <div class="pdetail__cta">
          <Button
            variant="primary"
            size="lg"
            :full="true"
            :disabled="!canOrder"
            @click="onOrder"
          >
            立即下单
          </Button>
          <Button
            variant="ghost"
            size="lg"
            :full="true"
            @click="goBackToList"
          >
            返回列表
          </Button>
        </div>

        <p v-if="disabledHint" class="pdetail__hint">{{ disabledHint }}</p>

        <Hairline />

        <section class="pdetail__desc">
          <h2 class="pdetail__section-title">商品描述</h2>
          <p class="pdetail__desc-body">{{ product.description || '暂无描述' }}</p>
        </section>

        <div class="pdetail__sku">
          <span class="pdetail__sku-label">商品编号</span>
          <span class="pdetail__sku-value">{{ product.productId }}</span>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.pdetail {
  padding: 24px 0 40px;
}

.pdetail__layout {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
  gap: 40px;
  align-items: start;
}

.pdetail__cover {
  width: 100%;
  background: var(--canvas-darker);
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--ink-100);
}

.pdetail__cover-art {
  aspect-ratio: 4 / 5;
}

.pdetail__cover :deep(.cover) {
  border-radius: 0;
  width: 100%;
}

.pdetail__side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.pdetail__layout--skeleton .pdetail__side > * {
  flex-shrink: 0;
}

.pdetail__status {
  display: flex;
}

.pdetail__title {
  margin: 0;
  font-family: var(--font-display, var(--font-sans));
  font-size: var(--t-display-size, 32px);
  font-weight: 600;
  line-height: 1.2;
  color: var(--ink-900);
  word-break: break-word;
}

.pdetail__price {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.pdetail__stock {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 20px;
}

.pdetail__stock-count {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}

.pdetail__stock-count strong {
  color: var(--ink-900);
  font-weight: 600;
}

.pdetail__stock-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pdetail__qty {
  display: flex;
  align-items: center;
  gap: 16px;
}

.pdetail__qty-label {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--ink-500);
}

.pdetail__cta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.pdetail__hint {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--warn, var(--ink-500));
}

.pdetail__section-title {
  margin: 0 0 8px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-700);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.pdetail__desc {
  display: flex;
  flex-direction: column;
}

.pdetail__desc-body {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink-700);
  white-space: pre-wrap;
}

.pdetail__sku {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
}

.pdetail__sku-label {
  color: var(--ink-500);
}

.pdetail__sku-value {
  color: var(--ink-700);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 960px) {
  .pdetail__layout {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
</style>
