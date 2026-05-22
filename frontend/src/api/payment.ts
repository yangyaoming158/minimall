import { get, post } from './http'
import type { PayPaymentRequest, Payment } from '@/types/payment'

export function payOrder(orderNo: string, payload?: PayPaymentRequest): Promise<Payment> {
  return post<Payment>(`/api/payments/${orderNo}/pay`, payload)
}

export function getPayment(orderNo: string): Promise<Payment> {
  return get<Payment>(`/api/payments/${orderNo}`)
}
