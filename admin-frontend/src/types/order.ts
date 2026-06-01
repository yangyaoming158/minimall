import type { PageParams } from './api'

// Mirrors order-service AdminOrderResponse / OrderEventResponse.
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'CLOSED'

export interface OrderItem {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
}

export interface AdminOrder {
  orderNo: string
  userId: number
  username: string
  status: OrderStatus
  totalAmount: number
  items: OrderItem[]
  createdAt: string
  updatedAt: string
  expireAt: string | null
  paidAt: string | null
  closedAt: string | null
}

// Recorded events carry eventType/eventId/payload; derived (state-timestamp)
// events leave those null and only carry the status transition.
export interface OrderEvent {
  eventType: string | null
  fromStatus: OrderStatus | null
  toStatus: OrderStatus | null
  occurredAt: string
  eventId: string | null
  payload: string | null
}

export interface OrderListParams extends PageParams {
  orderNo?: string
  username?: string
  userId?: number
  status?: OrderStatus
  productId?: string
  createdFrom?: string
  createdTo?: string
}
