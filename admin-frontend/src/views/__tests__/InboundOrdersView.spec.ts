import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import type { InboundOrder } from '@/types/inbound'
import { ApiError } from '@/types/api'

vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  const route = reactive({ query: {} as Record<string, string>, fullPath: '/inbound-orders' })
  const router = { push: vi.fn(), replace: vi.fn() }
  return {
    useRoute: () => route,
    useRouter: () => router,
    __testRoute: route,
  }
})

vi.mock('@/api/inboundOrders', () => ({
  listInboundOrders: vi.fn(),
  getInboundOrder: vi.fn(),
  createInboundOrderDraft: vi.fn(),
  cancelInboundOrder: vi.fn(),
  confirmInboundOrder: vi.fn(),
}))

import * as VueRouter from 'vue-router'
import {
  cancelInboundOrder,
  confirmInboundOrder,
  getInboundOrder,
  listInboundOrders,
} from '@/api/inboundOrders'
import InboundOrdersView from '@/views/InboundOrdersView.vue'

interface TestRoute {
  query: Record<string, string>
  fullPath: string
}
const mockRoute = (VueRouter as unknown as { __testRoute: TestRoute }).__testRoute

const mockedList = vi.mocked(listInboundOrders)
const mockedGet = vi.mocked(getInboundOrder)
const mockedCancel = vi.mocked(cancelInboundOrder)
const mockedConfirm = vi.mocked(confirmInboundOrder)

function order(over: Partial<InboundOrder> = {}): InboundOrder {
  return {
    inboundNo: 'IB-TEST-1',
    status: 'DRAFT',
    source: 'AI_SUGGESTION',
    createdByAdminUserId: 1,
    createdByAdminUsername: 'admin',
    confirmRequestId: null,
    confirmedByAdminUserId: null,
    confirmedByAdminUsername: null,
    confirmedAt: null,
    itemCount: 1,
    totalQuantity: 8,
    items: [{ productId: 'SKU-LOW-1', quantity: 8 }],
    createdAt: '2026-06-11T11:00:00',
    updatedAt: '2026-06-11T11:00:00',
    ...over,
  }
}

function pageOf(content: InboundOrder[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

// Stub Teleport so drawer content renders in place, and stub ElSelect (status
// filter), which hits a render recursion under the Teleport stub.
function mountView() {
  return mount(InboundOrdersView, {
    global: { plugins: [createPinia(), ElementPlus], stubs: { teleport: true, ElSelect: true } },
  })
}

async function clickButton(wrapper: VueWrapper, text: string): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === text)
  expect(btn, `button "${text}" not found`).toBeTruthy()
  await btn!.trigger('click')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('InboundOrdersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
    mockedList.mockResolvedValue(pageOf([order()]))
  })

  it('loads the first page on mount and renders inbound fields', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    const text = wrapper.text()
    expect(text).toContain('IB-TEST-1')
    expect(text).toContain('草稿')
    expect(text).toContain('AI 建议')
    expect(text).toContain('admin')
  })

  it('states that confirmation is the only stock-increasing operation', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('入库单确认是唯一正式增加库存的操作')
  })

  it('opens the detail drawer with items', async () => {
    mockedGet.mockResolvedValue(order())
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '详情')

    expect(mockedGet).toHaveBeenCalledWith('IB-TEST-1')
    expect(wrapper.text()).toContain('SKU-LOW-1')
  })

  it('confirms a draft with a generated X-Request-Id and refreshes', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    mockedConfirm.mockResolvedValue(order({ status: 'APPLIED', confirmedByAdminUsername: 'admin' }))
    const wrapper = mountView()
    await flushPromises()
    expect(mockedList).toHaveBeenCalledTimes(1)

    await clickButton(wrapper, '确认入库')

    expect(mockedConfirm).toHaveBeenCalledTimes(1)
    const [inboundNo, requestId] = mockedConfirm.mock.calls[0]
    expect(inboundNo).toBe('IB-TEST-1')
    expect(typeof requestId).toBe('string')
    expect(requestId.length).toBeGreaterThan(0)
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('reuses the same requestId when retrying a failed confirm', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    mockedConfirm.mockRejectedValueOnce(new ApiError('40900', '库存状态冲突', 409))
    mockedConfirm.mockResolvedValueOnce(order({ status: 'APPLIED' }))
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '确认入库')
    await clickButton(wrapper, '确认入库')

    expect(mockedConfirm).toHaveBeenCalledTimes(2)
    expect(mockedConfirm.mock.calls[0][1]).toBe(mockedConfirm.mock.calls[1][1])
  })

  it('does not confirm when the admin cancels the confirmation box', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '确认入库')

    expect(mockedConfirm).not.toHaveBeenCalled()
  })

  it('cancels a draft without touching stock and refreshes', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    mockedCancel.mockResolvedValue(order({ status: 'CANCELLED' }))
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '取消')

    expect(mockedCancel).toHaveBeenCalledWith('IB-TEST-1')
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('hides draft actions for non-draft orders', async () => {
    mockedList.mockResolvedValue(
      pageOf([order({ status: 'APPLIED', confirmedByAdminUsername: 'admin' })]),
    )
    const wrapper = mountView()
    await flushPromises()

    const labels = wrapper.findAll('button').map((b) => b.text())
    expect(labels).not.toContain('确认入库')
    expect(labels).not.toContain('取消')
    expect(wrapper.text()).toContain('已入库')
  })

  it('filters by status', async () => {
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as { statusFilter: string; onSearch: () => void }
    vm.statusFilter = 'DRAFT'
    vm.onSearch()
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, status: 'DRAFT' })
  })

  it('opens the detail drawer from an inboundNo deep link', async () => {
    mockRoute.query = { inboundNo: 'IB-LINKED-9' }
    mockedGet.mockResolvedValue(order({ inboundNo: 'IB-LINKED-9' }))
    const wrapper = mountView()
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledWith('IB-LINKED-9')
    expect(wrapper.text()).toContain('IB-LINKED-9')
  })

  it('surfaces a 40400 from a stale deep link and closes the drawer', async () => {
    const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    mockRoute.query = { inboundNo: 'IB-GONE' }
    mockedGet.mockRejectedValue(new ApiError('40400', '入库单不存在', 404))
    mountView()
    await flushPromises()

    expect(error).toHaveBeenCalledWith('入库单不存在')
  })

  it('shows an error hint when the list fails to load', async () => {
    mockedList.mockRejectedValue(new ApiError('50000', '服务器开小差了', 500))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败，请稍后重试。')
  })
})
