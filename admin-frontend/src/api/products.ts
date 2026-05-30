import { get, post, put } from './http'
import type { PageResponse } from '@/types/api'
import type {
  AdminProduct,
  CreateProductRequest,
  ProductListParams,
  ProductStatus,
  UpdateProductRequest,
} from '@/types/product'

export function listProducts(params: ProductListParams): Promise<PageResponse<AdminProduct>> {
  return get<PageResponse<AdminProduct>>('/api/admin/products', { params })
}

export function getProduct(productId: string): Promise<AdminProduct> {
  return get<AdminProduct>(`/api/admin/products/${encodeURIComponent(productId)}`)
}

export function createProduct(payload: CreateProductRequest): Promise<AdminProduct> {
  return post<AdminProduct>('/api/admin/products', payload)
}

export function updateProduct(productId: string, payload: UpdateProductRequest): Promise<AdminProduct> {
  return put<AdminProduct>(`/api/admin/products/${encodeURIComponent(productId)}`, payload)
}

export function updateProductStatus(productId: string, status: ProductStatus): Promise<AdminProduct> {
  return put<AdminProduct>(`/api/admin/products/${encodeURIComponent(productId)}/status`, { status })
}
