export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED'
export type PaymentChannel = 'MOCK'

// Both fields optional; backend defaults channel to MOCK when omitted.
export interface PayPaymentRequest {
  channel?: PaymentChannel
  idempotencyKey?: string
}

export interface Payment {
  paymentNo: string
  orderNo: string
  userId: number
  productId: string
  status: PaymentStatus
  amount: number
  channel: PaymentChannel
  paidAt: string | null
}
