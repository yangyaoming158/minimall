import type { PageParams } from './api'

// Mirrors payment-service AdminPaymentResponse.
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED'
export type PaymentChannel = 'MOCK'

export interface AdminPayment {
  paymentNo: string
  orderNo: string
  userId: number | null
  productId: string | null
  status: PaymentStatus
  amount: number
  channel: PaymentChannel
  paidAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PaymentListParams extends PageParams {
  paymentNo?: string
  orderNo?: string
  status?: PaymentStatus
  paidFrom?: string
  paidTo?: string
}
