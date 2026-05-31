import { get } from './http'
import type { PageResponse } from '@/types/api'
import type { AdminPayment, PaymentListParams } from '@/types/payment'

// Admin payment reads. Gateway-only: /api/admin/payments/** — read-only, no
// refund / reconciliation / callback (all out of Phase 2 scope).
export function listPayments(params: PaymentListParams): Promise<PageResponse<AdminPayment>> {
  return get<PageResponse<AdminPayment>>('/api/admin/payments', { params })
}

export function getPayment(paymentNo: string): Promise<AdminPayment> {
  return get<AdminPayment>(`/api/admin/payments/${encodeURIComponent(paymentNo)}`)
}

export function getPaymentByOrder(orderNo: string): Promise<AdminPayment> {
  return get<AdminPayment>(`/api/admin/payments/order/${encodeURIComponent(orderNo)}`)
}
