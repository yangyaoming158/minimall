import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type { AdminPayment } from '@/types/payment'

vi.mock('@/api/payments', () => ({
  listPayments: vi.fn(),
  getPayment: vi.fn(),
  getPaymentByOrder: vi.fn(),
}))

import { getPayment, listPayments } from '@/api/payments'
import PaymentsView from '@/views/PaymentsView.vue'

const mockedList = vi.mocked(listPayments)
const mockedGet = vi.mocked(getPayment)

function payment(over: Partial<AdminPayment> = {}): AdminPayment {
  return {
    paymentNo: 'PAY-1',
    orderNo: 'ORD-1',
    userId: 7,
    productId: 'SKU-1',
    status: 'SUCCESS',
    amount: 19.9,
    channel: 'MOCK',
    paidAt: '2026-05-01T00:05:00',
    createdAt: '2026-05-01T00:00:00',
    updatedAt: '2026-05-01T00:05:00',
    ...over,
  }
}

function pageOf(content: AdminPayment[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function mountView() {
  return mount(PaymentsView, { global: { plugins: [createPinia(), ElementPlus] } })
}

enableAutoUnmount(afterEach)

describe('PaymentsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([payment()]))
  })

  it('loads the first page on mount and renders rows', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(wrapper.text()).toContain('PAY-1')
    expect(wrapper.text()).toContain('MOCK')
  })

  it('searches with the paymentNo filter', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[placeholder="支付单号"]').setValue('PAY-9')
    const searchBtn = wrapper.findAll('button').find((b) => b.text() === '搜索')
    await searchBtn!.trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, paymentNo: 'PAY-9' })
  })

  it('loads payment detail when opening a row', async () => {
    mockedGet.mockResolvedValue(payment())
    const wrapper = mountView()
    await flushPromises()

    const btn = wrapper.findAll('button').find((b) => b.text() === '详情')
    await btn!.trigger('click')
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledWith('PAY-1')
  })
})
