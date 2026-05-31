import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type { AdminNotification } from '@/types/notification'

vi.mock('@/api/notifications', () => ({
  listNotifications: vi.fn(),
  getNotification: vi.fn(),
}))

import { getNotification, listNotifications } from '@/api/notifications'
import NotificationsView from '@/views/NotificationsView.vue'

const mockedList = vi.mocked(listNotifications)
const mockedGet = vi.mocked(getNotification)

function notification(over: Partial<AdminNotification> = {}): AdminNotification {
  return {
    id: 5,
    eventId: 'EVT-1',
    orderNo: 'ORD-1',
    notificationType: 'PAYMENT_SUCCESS',
    status: 'SENT',
    errorMessage: null,
    payload: '{"orderNo":"ORD-1"}',
    sentAt: '2026-05-01T00:05:00',
    createdAt: '2026-05-01T00:00:00',
    updatedAt: '2026-05-01T00:05:00',
    ...over,
  }
}

function pageOf(content: AdminNotification[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function mountView() {
  return mount(NotificationsView, { global: { plugins: [createPinia(), ElementPlus] } })
}

enableAutoUnmount(afterEach)

describe('NotificationsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([notification()]))
  })

  it('loads the first page on mount and renders rows', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(wrapper.text()).toContain('EVT-1')
    expect(wrapper.text()).toContain('PAYMENT_SUCCESS')
  })

  it('searches with the eventId filter', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[placeholder="事件 ID"]').setValue('EVT-9')
    const searchBtn = wrapper.findAll('button').find((b) => b.text() === '搜索')
    await searchBtn!.trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, eventId: 'EVT-9' })
  })

  it('loads notification detail when opening a row', async () => {
    mockedGet.mockResolvedValue(notification())
    const wrapper = mountView()
    await flushPromises()

    const btn = wrapper.findAll('button').find((b) => b.text() === '详情')
    await btn!.trigger('click')
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledWith(5)
  })
})
