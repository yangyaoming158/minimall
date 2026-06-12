<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import ProductFormDialog, { type ProductFormPayload } from '@/components/ProductFormDialog.vue'
import {
  createProduct,
  listProducts,
  updateProduct,
  updateProductStatus,
} from '@/api/products'
import type { AdminProduct, ProductStatus } from '@/types/product'

const PAGE_SIZE = 10

const products = ref<AdminProduct[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const filters = reactive<{ keyword: string; status: '' | ProductStatus }>({
  keyword: '',
  status: '',
})

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'ON_SHELF', label: '在售' },
  { value: 'OFF_SHELF', label: '已下架' },
]

// Form dialog state.
const formVisible = ref(false)
const editing = ref<AdminProduct | null>(null)
const submitting = ref(false)

// Detail drawer state.
const detailVisible = ref(false)
const detail = ref<AdminProduct | null>(null)

async function fetchProducts(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listProducts({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(filters.keyword.trim() ? { keyword: filters.keyword.trim() } : {}),
      ...(filters.status ? { status: filters.status } : {}),
    })
    products.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchProducts()
}

function onPageChange(next: number): void {
  page.value = next
  fetchProducts()
}

function openCreate(): void {
  editing.value = null
  formVisible.value = true
}

function openEdit(product: AdminProduct): void {
  editing.value = product
  formVisible.value = true
}

function openDetail(product: AdminProduct): void {
  detail.value = product
  detailVisible.value = true
}

async function onFormSubmit(payload: ProductFormPayload): Promise<void> {
  submitting.value = true
  try {
    if (editing.value) {
      await updateProduct(editing.value.productId, {
        name: payload.name,
        description: payload.description,
        imageUrl: payload.imageUrl,
        price: payload.price,
      })
      ElMessage.success('商品已更新')
    } else {
      await createProduct({
        productId: payload.productId,
        name: payload.name,
        description: payload.description,
        imageUrl: payload.imageUrl,
        price: payload.price,
      })
      ElMessage.success('商品已创建')
    }
    formVisible.value = false
    await fetchProducts()
  } catch {
    // Field-level errors already surfaced by the http interceptor / ApiError.
  } finally {
    submitting.value = false
  }
}

async function onToggleStatus(product: AdminProduct): Promise<void> {
  const next: ProductStatus = product.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF'
  const action = next === 'ON_SHELF' ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确认${action}商品「${product.name}」吗？`, `${action}确认`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消',
    })
  } catch {
    return // cancelled
  }
  try {
    await updateProductStatus(product.productId, next)
    ElMessage.success(`已${action}`)
    await fetchProducts()
  } catch {
    /* surfaced by interceptor */
  }
}

onMounted(fetchProducts)
</script>

<template>
  <div>
    <PageHeader title="商品管理" description="商品列表、搜索、状态筛选、新增 / 编辑、上架 / 下架。">
      <template #actions>
        <el-button type="primary" @click="openCreate">新增商品</el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="按商品 ID / 名称搜索"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="filters.status" placeholder="状态" style="width: 140px" @change="onSearch">
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="products" class="table" empty-text="暂无商品">
      <el-table-column prop="productId" label="商品 ID" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <img v-if="row.imageUrl" :src="row.imageUrl" alt="" class="thumb" />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="120">
        <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="row.status === 'ON_SHELF' ? '在售' : '已下架'" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="onToggleStatus(row)">
            {{ row.status === 'ON_SHELF' ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="errored" class="error-hint">加载失败，请稍后重试。</div>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <ProductFormDialog
      v-model="formVisible"
      :product="editing"
      :submitting="submitting"
      @submit="onFormSubmit"
    />

    <el-drawer v-model="detailVisible" title="商品详情" size="420px">
      <div v-if="detail" class="detail">
        <img v-if="detail.imageUrl" :src="detail.imageUrl" alt="" class="detail-img" />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="商品 ID">{{ detail.productId }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="价格">¥{{ Number(detail.price).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="detail.status === 'ON_SHELF' ? '在售' : '已下架'" />
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  gap: var(--space-12);
  margin-bottom: var(--space-16);
}

.table {
  width: 100%;
}

.thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  vertical-align: middle;
}

.muted {
  color: var(--text-faint);
}

.error-hint {
  margin-top: var(--space-12);
  color: var(--danger);
  font-size: var(--text-sm);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-16);
}

.detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-16);
}

.detail-img {
  width: 100%;
  max-height: 220px;
  object-fit: cover;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}
</style>
