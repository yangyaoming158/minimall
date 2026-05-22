import type { PageParams } from './api'

export type ProductStatus = 'ON_SHELF' | 'OFF_SHELF'

export interface Product {
  productId: string
  name: string
  description: string | null
  price: number
  status: ProductStatus
  createdAt: string
  updatedAt: string
}

export interface ProductListParams extends PageParams {
  status?: ProductStatus
}
