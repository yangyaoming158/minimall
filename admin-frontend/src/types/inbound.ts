export type InboundOrderStatus = 'DRAFT' | 'CONFIRMED' | 'APPLIED' | 'CANCELLED'

export type InboundOrderSource = 'ADMIN_MANUAL' | 'AI_SUGGESTION'

export interface InboundOrderItem {
  productId: string
  quantity: number
}

export interface InboundOrder {
  inboundNo: string
  status: InboundOrderStatus
  source: InboundOrderSource
  createdByAdminUserId: number | null
  createdByAdminUsername: string | null
  confirmRequestId: string | null
  confirmedByAdminUserId: number | null
  confirmedByAdminUsername: string | null
  confirmedAt: string | null
  itemCount: number
  totalQuantity: number
  items: InboundOrderItem[]
  createdAt: string
  updatedAt: string
}

export interface InboundOrderListParams {
  status?: InboundOrderStatus
  page?: number
  size?: number
  sort?: string
}

export interface CreateInboundOrderDraftRequest {
  items: InboundOrderItem[]
}
