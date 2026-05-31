import type { PageParams } from './api'

// Mirrors inventory-service AdminInventoryResponse. Record/request DTOs are
// added by later subtasks (13.3 init/adjust, 13.4 records) to keep this slice
// focused on list + detail reads.
export type StockState = 'IN_STOCK' | 'OUT_OF_STOCK' | 'INACTIVE'
export type InventoryStatus = 'ACTIVE' | 'INACTIVE'

export interface AdminInventory {
  productId: string
  availableStock: number
  lockedStock: number
  safetyStock: number
  status: InventoryStatus
  stockState: StockState
  lowStock: boolean
  createdAt: string
  updatedAt: string
}

export interface InventoryListParams extends PageParams {
  keyword?: string
  stockState?: StockState
  lowStock?: boolean
}
