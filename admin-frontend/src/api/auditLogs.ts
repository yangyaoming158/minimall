import { get } from './http'
import type { PageResponse } from '@/types/api'
import type { AdminAuditLog, AuditLogListParams } from '@/types/audit'

// Admin audit-log reads. Gateway-only: /api/admin/audit-logs (user-service).
// List-only by contract — there is no detail endpoint; the detail drawer
// renders the row, which already carries before/after snapshots.
export function listAuditLogs(params: AuditLogListParams): Promise<PageResponse<AdminAuditLog>> {
  return get<PageResponse<AdminAuditLog>>('/api/admin/audit-logs', { params })
}
