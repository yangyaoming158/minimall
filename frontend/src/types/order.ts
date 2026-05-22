export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'CLOSED' | 'REFUNDED'

export interface OrderItemSummary {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
}

// Shape shared by GET /api/orders/my (list) and GET /api/orders/{orderNo} (detail).
export interface Order {
  orderNo: string
  userId: number
  status: OrderStatus
  totalAmount: number
  items: OrderItemSummary[]
  createdAt: string
  updatedAt: string
  expireAt: string | null
  paidAt: string | null
  closedAt: string | null
}

export interface CreateOrderRequest {
  productId: string
  quantity: number
  idempotencyKey: string
}

export interface CreateOrderResponse {
  orderNo: string
  userId: number
  status: OrderStatus
  expireAt: string | null
  totalAmount: number
  productId: string
  quantity: number
}

export interface CancelOrderResponse {
  orderNo: string
  userId: number
  productId: string
  status: OrderStatus
}
