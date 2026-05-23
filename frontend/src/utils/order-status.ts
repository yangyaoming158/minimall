import type { OrderStatus } from '@/types/order'

export type OrderStatusTagType = 'warning' | 'success' | 'info' | 'danger'

export interface OrderStatusMeta {
  label: string
  type: OrderStatusTagType
}

const STATUS_META: Record<OrderStatus, OrderStatusMeta> = {
  PENDING_PAYMENT: { label: '待支付', type: 'warning' },
  PAID: { label: '已支付', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
  CLOSED: { label: '已关闭', type: 'info' },
  REFUNDED: { label: '已退款', type: 'danger' },
}

// Defensive: backend may add new statuses (e.g. SHIPPED, COMPLETED) later;
// show the raw value in a neutral tag rather than crashing the page.
export function getOrderStatusMeta(status: string): OrderStatusMeta {
  return STATUS_META[status as OrderStatus] ?? { label: status, type: 'info' }
}
