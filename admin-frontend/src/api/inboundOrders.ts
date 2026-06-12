import { get, post } from './http'
import type { PageResponse } from '@/types/api'
import type {
  CreateInboundOrderDraftRequest,
  InboundOrder,
  InboundOrderListParams,
} from '@/types/inbound'

// Phase 2.5 inbound order APIs (reused). Gateway-only. Confirmation is the
// ONLY admin action that applies stock changes, and it is idempotent on the
// X-Request-Id the browser supplies.
export function listInboundOrders(
  params: InboundOrderListParams,
): Promise<PageResponse<InboundOrder>> {
  return get<PageResponse<InboundOrder>>('/api/admin/inbound-orders', { params })
}

export function getInboundOrder(inboundNo: string): Promise<InboundOrder> {
  return get<InboundOrder>(`/api/admin/inbound-orders/${encodeURIComponent(inboundNo)}`)
}

export function createInboundOrderDraft(
  payload: CreateInboundOrderDraftRequest,
): Promise<InboundOrder> {
  return post<InboundOrder>('/api/admin/inbound-orders/drafts', payload)
}

export function cancelInboundOrder(inboundNo: string): Promise<InboundOrder> {
  return post<InboundOrder>(`/api/admin/inbound-orders/${encodeURIComponent(inboundNo)}/cancel`)
}

// requestId is the idempotency key: the backend replays the prior result for
// a repeated confirm with the same id instead of applying stock twice. Keep
// the same id when retrying a failed confirm of the same inbound order.
export function confirmInboundOrder(inboundNo: string, requestId: string): Promise<InboundOrder> {
  return post<InboundOrder>(
    `/api/admin/inbound-orders/${encodeURIComponent(inboundNo)}/confirm`,
    undefined,
    { headers: { 'X-Request-Id': requestId } },
  )
}
