import type { PageParams } from './api'

// Mirrors notification-service AdminNotificationResponse. Note (Phase 2): the
// backend sources orderNo from the recipient column and channel maps onto
// notificationType, which today only has PAYMENT_SUCCESS.
export type NotificationLogStatus = 'PENDING' | 'SENT' | 'FAILED'
export type NotificationType = 'PAYMENT_SUCCESS'

export interface AdminNotification {
  id: number
  eventId: string
  orderNo: string | null
  notificationType: NotificationType
  status: NotificationLogStatus
  errorMessage: string | null
  payload: string | null
  sentAt: string | null
  createdAt: string
  updatedAt: string
}

export interface NotificationListParams extends PageParams {
  eventId?: string
  orderNo?: string
  status?: NotificationLogStatus
  channel?: NotificationType
  createdFrom?: string
  createdTo?: string
}
