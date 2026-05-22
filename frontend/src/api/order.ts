import { get, post } from './http'
import type { PageParams, PageResponse } from '@/types/api'
import type {
  CancelOrderResponse,
  CreateOrderRequest,
  CreateOrderResponse,
  Order,
} from '@/types/order'

export function createOrder(payload: CreateOrderRequest): Promise<CreateOrderResponse> {
  return post<CreateOrderResponse>('/api/orders', payload)
}

export function listMyOrders(params?: PageParams): Promise<PageResponse<Order>> {
  return get<PageResponse<Order>>('/api/orders/my', { params })
}

export function getOrder(orderNo: string): Promise<Order> {
  return get<Order>(`/api/orders/${orderNo}`)
}

export function cancelOrder(orderNo: string): Promise<CancelOrderResponse> {
  return post<CancelOrderResponse>(`/api/orders/${orderNo}/cancel`)
}
