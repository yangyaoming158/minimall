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

export interface InitializeInventoryRequest {
  productId: string
  initialStock: number
  safetyStock: number
}

// requestId is the idempotency key; the view mints a fresh one per submit
// attempt so the backend can dedupe genuine retries by requestId.
export interface AdjustInventoryRequest {
  delta: number
  reason: string
  requestId: string
}
