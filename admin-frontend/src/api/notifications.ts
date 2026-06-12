import { get } from './http'
import type { PageResponse } from '@/types/api'
import type { AdminNotification, NotificationListParams } from '@/types/notification'

// Admin notification reads. Gateway-only: /api/admin/notifications/** —
// read-only, no resend (out of Phase 2 scope).
export function listNotifications(
  params: NotificationListParams,
): Promise<PageResponse<AdminNotification>> {
  return get<PageResponse<AdminNotification>>('/api/admin/notifications', { params })
}

export function getNotification(id: number): Promise<AdminNotification> {
  return get<AdminNotification>(`/api/admin/notifications/${id}`)
}
