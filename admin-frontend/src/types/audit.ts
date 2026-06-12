import type { PageParams } from './api'

// Mirrors common-core AdminAuditLogResponse + audit enums.
export type AdminAuditAction =
  | 'PRODUCT_CREATE'
  | 'PRODUCT_UPDATE'
  | 'PRODUCT_ON_SHELF'
  | 'PRODUCT_OFF_SHELF'
  | 'INVENTORY_INITIALIZE'
  | 'INVENTORY_ADJUST'
  | 'INBOUND_ORDER_CREATE'
  | 'INBOUND_ORDER_CONFIRM'
  | 'AI_SUGGESTION_CREATE'
  | 'AI_SUGGESTION_APPLY'
  | 'AI_SUGGESTION_REJECT'

export type AdminAuditResourceType =
  | 'PRODUCT'
  | 'INVENTORY'
  | 'INVENTORY_RECORD'
  | 'ORDER'
  | 'PAYMENT'
  | 'NOTIFICATION'
  | 'ADMIN_USER'
  | 'INBOUND_ORDER'
  | 'AI_SUGGESTION'

export type AdminAuditSourceType =
  | 'ADMIN_MANUAL'
  | 'INVENTORY_ADJUSTMENT'
  | 'INBOUND_ORDER'
  | 'AI_SUGGESTION'

export interface AdminAuditLog {
  id: number
  adminUserId: number
  adminUsername: string
  action: AdminAuditAction
  resourceType: AdminAuditResourceType
  resourceId: string | null
  requestId: string | null
  sourceType: AdminAuditSourceType
  referenceNo: string | null
  beforeSnapshot: unknown
  afterSnapshot: unknown
  ip: string | null
  userAgent: string | null
  summary: string
  createdAt: string
}

export interface AuditLogListParams extends PageParams {
  adminUserId?: number
  action?: AdminAuditAction
  resourceType?: AdminAuditResourceType
  resourceId?: string
  requestId?: string
  sourceType?: AdminAuditSourceType
  referenceNo?: string
  createdFrom?: string
  createdTo?: string
}
