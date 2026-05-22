import { get } from './http'
import type { PageResponse } from '@/types/api'
import type { Product, ProductListParams } from '@/types/product'

export function listProducts(params?: ProductListParams): Promise<PageResponse<Product>> {
  return get<PageResponse<Product>>('/api/products', { params })
}

export function getProduct(productId: string): Promise<Product> {
  return get<Product>(`/api/products/${productId}`)
}
