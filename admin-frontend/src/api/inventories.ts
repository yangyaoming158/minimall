import { get } from './http'
import type { PageResponse } from '@/types/api'
import type { AdminInventory, InventoryListParams } from '@/types/inventory'

// Admin inventory reads. Gateway-only: /api/admin/inventories/** — never a
// service port and never an internal-only route. Write paths (initialize and
// adjust) and the record timeline land in later subtasks.
export function listInventories(
  params: InventoryListParams,
): Promise<PageResponse<AdminInventory>> {
  return get<PageResponse<AdminInventory>>('/api/admin/inventories', { params })
}

export function getInventory(productId: string): Promise<AdminInventory> {
  return get<AdminInventory>(`/api/admin/inventories/${encodeURIComponent(productId)}`)
}
