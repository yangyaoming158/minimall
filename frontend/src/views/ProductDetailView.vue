<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProduct } from '@/api/product'
import { getInventory } from '@/api/inventory'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'
import type { Product } from '@/types/product'
import type { Inventory } from '@/types/inventory'

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

// Clamp quantity so the input stays consistent with whatever stock value loaded.
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

const priceText = computed(() =>
  product.value ? `¥${product.value.price.toFixed(2)}` : '',
)

const isOffShelf = computed(() => product.value?.status === 'OFF_SHELF')

const stockStateLabel = computed(() => {
  if (!inventory.value) return null
  switch (inventory.value.stockState) {
    case 'IN_STOCK':
      return { text: '有货', type: 'success' as const }
    case 'OUT_OF_STOCK':
      return { text: '无货', type: 'danger' as const }
    case 'INACTIVE':
      return { text: '未上架', type: 'info' as const }
    default:
      return null
  }
})

const maxQuantity = computed(() => {
  const available = inventory.value?.availableStock ?? 0
  // el-input-number requires max >= min; keep max=1 when no stock so the input
  // is bounded even though the order button will be disabled anyway.
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

const disabledHint = computed(() => {
  if (!product.value) return ''
  if (isOffShelf.value) return '该商品已下架，暂不可下单'
  if (!inventory.value) return '库存信息暂不可用，无法下单'
  if (inventory.value.stockState === 'INACTIVE') return '该商品未激活库存，暂不可下单'
  if (inventory.value.stockState === 'OUT_OF_STOCK' || inventory.value.availableStock <= 0) {
    return '库存不足，暂不可下单'
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
  <section v-loading="loading" class="page">
    <!-- Product load failed: replace the whole layout. -->
    <el-result
      v-if="productError && productError.kind === 'not-found'"
      icon="warning"
      title="商品不存在"
      :sub-title="productError.message"
    >
      <template #extra>
        <el-button type="primary" @click="goBackToList">返回商品列表</el-button>
      </template>
    </el-result>

    <el-result
      v-else-if="productError && productError.kind === 'generic'"
      icon="error"
      title="加载失败"
      :sub-title="productError.message"
    >
      <template #extra>
        <el-button type="primary" @click="loadAll">重试</el-button>
        <el-button @click="goBackToList">返回商品列表</el-button>
      </template>
    </el-result>

    <!-- Success state: detail + inventory + order panel. -->
    <el-row v-else-if="product" :gutter="24" class="content">
      <el-col :xs="24" :md="14">
        <div class="info-card">
          <div class="info-head">
            <h1 class="product-name" :title="product.name">{{ product.name }}</h1>
            <el-tag
              :type="isOffShelf ? 'info' : 'success'"
              size="default"
              effect="light"
            >
              {{ isOffShelf ? '已下架' : '在售' }}
            </el-tag>
          </div>

          <div class="product-price">{{ priceText }}</div>

          <div class="meta">
            <span class="meta-label">商品编号</span>
            <span class="meta-value">{{ product.productId }}</span>
          </div>

          <el-divider />

          <h2 class="section-title">商品描述</h2>
          <p class="product-desc">{{ product.description || '暂无描述' }}</p>
        </div>
      </el-col>

      <el-col :xs="24" :md="10">
        <div class="order-card">
          <h2 class="section-title">库存信息</h2>

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
            <div class="stock-row">
              <span class="stock-label">库存状态</span>
              <el-tag
                v-if="stockStateLabel"
                :type="stockStateLabel.type"
                size="small"
                effect="light"
              >
                {{ stockStateLabel.text }}
              </el-tag>
            </div>
            <div class="stock-row">
              <span class="stock-label">可售库存</span>
              <span class="stock-value">{{ inventory.availableStock }}</span>
            </div>
            <div class="stock-row">
              <span class="stock-label">已锁定</span>
              <span class="stock-value muted">{{ inventory.lockedStock }}</span>
            </div>
          </template>

          <el-divider />

          <h2 class="section-title">购买</h2>

          <div class="qty-row">
            <span class="qty-label">数量</span>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="maxQuantity"
              :step="1"
              :precision="0"
              :disabled="!inventory || isOffShelf || inventory.stockState !== 'IN_STOCK'"
              controls-position="right"
            />
          </div>

          <el-button
            class="order-btn"
            type="primary"
            size="large"
            :disabled="!canOrder"
            @click="onOrder"
          >
            立即下单
          </el-button>

          <p v-if="disabledHint" class="disabled-hint">{{ disabledHint }}</p>
        </div>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.page {
  min-height: 320px;
}

.content {
  margin: 0;
}

.info-card,
.order-card {
  background: #ffffff;
  border-radius: 10px;
  padding: 28px;
  border: 1px solid #ebeef5;
  height: 100%;
}

.info-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.product-name {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.4;
  word-break: break-word;
}

.product-price {
  font-size: 28px;
  font-weight: 700;
  color: #f56c6c;
  margin: 4px 0 16px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #909399;
}

.meta-value {
  color: #606266;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.product-desc {
  margin: 0;
  color: #4e5969;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.stock-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}

.stock-label {
  color: #606266;
}

.stock-value {
  color: #1f2329;
  font-weight: 600;
}

.stock-value.muted {
  color: #909399;
  font-weight: 500;
}

.qty-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.qty-label {
  color: #606266;
  font-size: 14px;
}

.order-btn {
  width: 100%;
}

.disabled-hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: #e6a23c;
  text-align: center;
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
</style>
