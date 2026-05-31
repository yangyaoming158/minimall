import { get, post } from './http'
import type { PageResponse } from '@/types/api'
import type {
  AdjustInventoryRequest,
  AdminInventory,
  InitializeInventoryRequest,
  InventoryListParams,
  InventoryRecord,
} from '@/types/inventory'

// Admin inventory APIs. Gateway-only: /api/admin/inventories/** — never a
// service port and never an internal-only route.
export function listInventories(
  params: InventoryListParams,
): Promise<PageResponse<AdminInventory>> {
  return get<PageResponse<AdminInventory>>('/api/admin/inventories', { params })
}

export function getInventory(productId: string): Promise<AdminInventory> {
  return get<AdminInventory>(`/api/admin/inventories/${encodeURIComponent(productId)}`)
}

export function initializeInventory(
  payload: InitializeInventoryRequest,
): Promise<AdminInventory> {
  return post<AdminInventory>('/api/admin/inventories', payload)
}

export function adjustInventory(
  productId: string,
  payload: AdjustInventoryRequest,
): Promise<AdminInventory> {
  return post<AdminInventory>(
    `/api/admin/inventories/${encodeURIComponent(productId)}/adjust`,
    payload,
  )
}

// Returns the record timeline already ordered most-recent-first by the backend.
export function getInventoryRecords(productId: string): Promise<InventoryRecord[]> {
  return get<InventoryRecord[]>(
    `/api/admin/inventories/${encodeURIComponent(productId)}/records`,
  )
}
