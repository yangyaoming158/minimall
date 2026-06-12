import type { PageParams } from './api'

// Mirrors product-service ProductResponse + request DTOs.
export type ProductStatus = 'ON_SHELF' | 'OFF_SHELF'

export interface AdminProduct {
  productId: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  status: ProductStatus
  createdAt: string
  updatedAt: string
}

export interface ProductListParams extends PageParams {
  keyword?: string
  status?: ProductStatus
}

export interface CreateProductRequest {
  productId: string
  name: string
  description?: string | null
  imageUrl?: string | null
  price: number
}

export interface UpdateProductRequest {
  name: string
  description?: string | null
  imageUrl?: string | null
  price: number
}
