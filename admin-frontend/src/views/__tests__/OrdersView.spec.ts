import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type { AdminOrder } from '@/types/order'

vi.mock('@/api/orders', () => ({
  listOrders: vi.fn(),
  getOrder: vi.fn(),
  getOrderEvents: vi.fn(),
}))

import { getOrder, getOrderEvents, listOrders } from '@/api/orders'
import OrdersView from '@/views/OrdersView.vue'

const mockedList = vi.mocked(listOrders)
const mockedGet = vi.mocked(getOrder)
const mockedEvents = vi.mocked(getOrderEvents)

function order(over: Partial<AdminOrder> = {}): AdminOrder {
  return {
    orderNo: 'ORD-1',
    userId: 7,
    username: 'alice',
    status: 'PAID',
    totalAmount: 19.9,
    items: [{ productId: 'SKU-1', productName: 'Widget', quantity: 2, unitPrice: 9.95 }],
    createdAt: '2026-05-01T00:00:00',
    updatedAt: '2026-05-01T00:00:00',
    expireAt: null,
    paidAt: '2026-05-01T00:05:00',
    closedAt: null,
    ...over,
  }
}

function pageOf(content: AdminOrder[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function mountView() {
  return mount(OrdersView, { global: { plugins: [createPinia(), ElementPlus] } })
}

enableAutoUnmount(afterEach)

describe('OrdersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([order()]))
  })

  it('loads the first page on mount and renders rows', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(wrapper.text()).toContain('ORD-1')
    expect(wrapper.text()).toContain('alice')
  })

  it('searches with the orderNo filter', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[placeholder="订单号"]').setValue('ORD-9')
    const searchBtn = wrapper.findAll('button').find((b) => b.text() === '搜索')
    await searchBtn!.trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, orderNo: 'ORD-9' })
  })

  it('loads order detail and events when opening a row', async () => {
    mockedGet.mockResolvedValue(order())
    mockedEvents.mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    const btn = wrapper.findAll('button').find((b) => b.text() === '详情')
    await btn!.trigger('click')
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledWith('ORD-1')
    expect(mockedEvents).toHaveBeenCalledWith('ORD-1')
  })
})
