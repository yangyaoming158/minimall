<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import { listProducts } from '@/api/product'
import { ApiError } from '@/types/api'
import type { Product, ProductStatus } from '@/types/product'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 12

type StatusFilter = '' | ProductStatus

const products = ref<Product[]>([])
const total = ref(0)
const currentPage = ref(1) // 1-based for el-pagination; backend is 0-based
const statusFilter = ref<StatusFilter>('')
const loading = ref(false)
const errored = ref(false)

// Seed state from the URL so a refresh / shared link keeps page + filter.
function seedFromQuery() {
  const page = Number(route.query.page)
  currentPage.value = Number.isInteger(page) && page > 0 ? page : 1
  const status = route.query.status
  statusFilter.value = status === 'ON_SHELF' || status === 'OFF_SHELF' ? status : ''
}

// Reflect current state back into the URL without stacking history entries.
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
      page: currentPage.value - 1, // 1-based UI -> 0-based backend
      size: PAGE_SIZE,
      ...(statusFilter.value ? { status: statusFilter.value } : {}),
    })
    products.value = res.content
    total.value = res.totalElements
  } catch (err) {
    // Network/429/500 already surfaced by the http interceptor; here we just
    // flip to the error state so the user gets a retry affordance.
    errored.value = true
    if (err instanceof ApiError) {
      products.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
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

// Support back/forward navigation that only changes the query string.
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
  <section class="product-list">
    <header class="list-header">
      <h1 class="list-title">商品列表</h1>
      <el-select
        v-model="statusFilter"
        class="status-filter"
        placeholder="全部状态"
        @change="onFilterChange"
      >
        <el-option label="全部" value="" />
        <el-option label="在售" value="ON_SHELF" />
        <el-option label="已下架" value="OFF_SHELF" />
      </el-select>
    </header>

    <!-- Error state with retry -->
    <div v-if="errored" class="state-block">
      <el-result icon="warning" title="加载失败" sub-title="无法获取商品列表，请稍后重试。">
        <template #extra>
          <el-button type="primary" @click="fetchProducts">重试</el-button>
        </template>
      </el-result>
    </div>

    <!-- Loading state -->
    <div v-else v-loading="loading" class="grid-wrap">
      <!-- Empty state -->
      <el-empty v-if="!loading && products.length === 0" description="暂无商品" />

      <el-row v-else :gutter="16">
        <el-col
          v-for="product in products"
          :key="product.productId"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
          class="grid-col"
        >
          <ProductCard :product="product" @view="goDetail" />
        </el-col>
      </el-row>
    </div>

    <footer v-if="!errored && total > 0" class="list-footer">
      <el-pagination
        layout="prev, pager, next, total"
        background
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="currentPage"
        @current-change="onPageChange"
      />
    </footer>
  </section>
</template>

<style scoped>
.product-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.list-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.status-filter {
  width: 160px;
}

.grid-wrap {
  min-height: 200px;
}

.grid-col {
  margin-bottom: 16px;
}

.state-block {
  background: #ffffff;
  border-radius: 10px;
  padding: 24px;
}

.list-footer {
  display: flex;
  justify-content: center;
  padding: 8px 0 16px;
}
</style>
