<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PillGroup from '@/components/atoms/PillGroup.vue'
import Pager from '@/components/atoms/Pager.vue'
import Skeleton from '@/components/atoms/Skeleton.vue'
import SkeletonText from '@/components/atoms/SkeletonText.vue'
import { listProducts } from '@/api/product'
import { ApiError } from '@/types/api'
import type { Product, ProductStatus } from '@/types/product'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 12

type StatusFilter = '' | ProductStatus

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'ON_SHELF', label: '在售' },
  { value: 'OFF_SHELF', label: '已下架' },
]

const products = ref<Product[]>([])
const total = ref(0)
const currentPage = ref(1)
const statusFilter = ref<StatusFilter>('')
const loading = ref(false)
const errored = ref(false)

function seedFromQuery() {
  const page = Number(route.query.page)
  currentPage.value = Number.isInteger(page) && page > 0 ? page : 1
  const status = route.query.status
  statusFilter.value = status === 'ON_SHELF' || status === 'OFF_SHELF' ? status : ''
}

function syncQuery() {
  router.replace({
    query: {
      ...(currentPage.value > 1 ? { page: String(currentPage.value) } : {}),
      ...(statusFilter.value ? { status: statusFilter.value } : {}),
    },
  })
}

async function fetchProducts() {
  loading.value = true
  errored.value = false
  try {
    const res = await listProducts({
      page: currentPage.value - 1,
      size: PAGE_SIZE,
      ...(statusFilter.value ? { status: statusFilter.value } : {}),
    })
    products.value = res.content
    total.value = res.totalElements
  } catch (err) {
    errored.value = true
    if (err instanceof ApiError) {
      products.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function onFilterChange(value: string) {
  statusFilter.value = value as StatusFilter
  currentPage.value = 1
  syncQuery()
  fetchProducts()
}

function onPageChange(page: number) {
  currentPage.value = page
  syncQuery()
  fetchProducts()
}

function goDetail(productId: string) {
  router.push(`/products/${productId}`)
}

onMounted(() => {
  seedFromQuery()
  fetchProducts()
})

watch(
  () => route.query,
  () => {
    const page = Number(route.query.page) || 1
    const status = route.query.status
    const normalizedStatus: StatusFilter =
      status === 'ON_SHELF' || status === 'OFF_SHELF' ? status : ''
    if (page !== currentPage.value || normalizedStatus !== statusFilter.value) {
      currentPage.value = page
      statusFilter.value = normalizedStatus
      fetchProducts()
    }
  },
)
</script>

<template>
  <section class="plist">
    <header class="plist__header">
      <div class="plist__heading">
        <h1 class="plist__title">商品</h1>
        <p v-if="!loading && !errored" class="plist__meta">
          共 {{ total }} 件
        </p>
      </div>
      <PillGroup
        :options="STATUS_OPTIONS"
        :model-value="statusFilter"
        @update:model-value="onFilterChange"
      />
    </header>

    <ErrorState
      v-if="errored"
      tone="error"
      title="加载失败"
      description="无法获取商品列表,请稍后重试。"
      :show-retry="true"
      retry-label="重试"
      :retry-loading="loading"
      @retry="fetchProducts"
    />

    <div v-else-if="loading" class="plist__grid" aria-busy="true">
      <article v-for="i in 8" :key="`sk-${i}`" class="plist__skeleton">
        <Skeleton :width="'100%'" :height="'auto'" radius="md" block class="plist__skeleton-cover" />
        <div class="plist__skeleton-body">
          <Skeleton :height="'18px'" :width="'80%'" block />
          <SkeletonText :lines="2" :line-height="'12px'" :gap="'8px'" />
          <Skeleton :height="'20px'" :width="'40%'" block />
        </div>
      </article>
    </div>

    <EmptyState
      v-else-if="products.length === 0"
      title="暂无商品"
      :description="statusFilter ? '当前筛选条件下没有商品,试试切换筛选。' : '商品稍后上架,先去逛逛吧。'"
    />

    <div v-else class="plist__grid">
      <ProductCard
        v-for="product in products"
        :key="product.productId"
        :product="product"
        @view="goDetail"
      />
    </div>

    <footer v-if="!errored && !loading && total > 0" class="plist__footer">
      <Pager
        :total="total"
        :page-size="PAGE_SIZE"
        :current="currentPage"
        @change="onPageChange"
      />
    </footer>
  </section>
</template>

<style scoped>
.plist {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px 0 32px;
}

.plist__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.plist__heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.plist__title {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  font-weight: var(--t-h1-weight);
  line-height: var(--t-h1-lh);
  color: var(--ink-900);
}

.plist__meta {
  margin: 0;
  font-family: var(--font-sans);
  font-size: var(--t-caption-size);
  line-height: var(--t-caption-lh);
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}

.plist__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 24px;
}

.plist__skeleton {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.plist__skeleton-cover {
  aspect-ratio: 4 / 5;
}

.plist__skeleton-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}

.plist__footer {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
