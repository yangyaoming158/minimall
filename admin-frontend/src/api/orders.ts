import { get } from './http'
import type { PageResponse } from '@/types/api'
import type { AdminOrder, OrderEvent, OrderListParams } from '@/types/order'

// Admin order reads. Gateway-only: /api/admin/orders/** — read-only, no status
// mutation (Phase 2 forbids admin order status changes).
export function listOrders(params: OrderListParams): Promise<PageResponse<AdminOrder>> {
  return get<PageResponse<AdminOrder>>('/api/admin/orders', { params })
}

export function getOrder(orderNo: string): Promise<AdminOrder> {
  return get<AdminOrder>(`/api/admin/orders/${encodeURIComponent(orderNo)}`)
}

export function getOrderEvents(orderNo: string): Promise<OrderEvent[]> {
  return get<OrderEvent[]>(`/api/admin/orders/${encodeURIComponent(orderNo)}/events`)
}
