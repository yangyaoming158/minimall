import { get } from './http'
import type { Inventory } from '@/types/inventory'

export function getInventory(productId: string): Promise<Inventory> {
  return get<Inventory>(`/api/inventories/${productId}`)
}
