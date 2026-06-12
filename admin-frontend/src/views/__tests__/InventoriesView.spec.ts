import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import type { AdminInventory, InventoryRecord } from '@/types/inventory'
import { ApiError } from '@/types/api'

vi.mock('@/api/inventories', () => ({
  listInventories: vi.fn(),
  getInventory: vi.fn(),
  initializeInventory: vi.fn(),
  adjustInventory: vi.fn(),
  getInventoryRecords: vi.fn(),
}))

import {
  adjustInventory,
  getInventoryRecords,
  initializeInventory,
  listInventories,
} from '@/api/inventories'
import InventoriesView from '@/views/InventoriesView.vue'
import InventoryInitDialog from '@/components/InventoryInitDialog.vue'
import InventoryAdjustDialog from '@/components/InventoryAdjustDialog.vue'

const mockedList = vi.mocked(listInventories)
const mockedInit = vi.mocked(initializeInventory)
const mockedAdjust = vi.mocked(adjustInventory)
const mockedRecords = vi.mocked(getInventoryRecords)

function inventory(over: Partial<AdminInventory> = {}): AdminInventory {
  return {
    productId: 'SKU-1',
    availableStock: 100,
    lockedStock: 5,
    safetyStock: 10,
    status: 'ACTIVE',
    stockState: 'IN_STOCK',
    lowStock: false,
    createdAt: '2026-05-01T00:00:00',
    updatedAt: '2026-05-01T00:00:00',
    ...over,
  }
}

function pageOf(content: AdminInventory[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function record(over: Partial<InventoryRecord> = {}): InventoryRecord {
  return {
    id: 1,
    productId: 'SKU-1',
    orderNo: null,
    requestId: 'req-abc',
    changeType: 'ADJUST_INCREASE',
    sourceType: 'ADMIN_ADJUSTMENT',
    quantity: 8,
    reason: '盘点入库',
    adminUserId: 1,
    adminUsername: 'admin',
    referenceNo: null,
    status: 'SUCCESS',
    createdAt: '2026-05-02T10:00:00',
    updatedAt: '2026-05-02T10:00:00',
    ...over,
  }
}

function mountView() {
  return mount(InventoriesView, { global: { plugins: [createPinia(), ElementPlus] } })
}

// For asserting the teleported records drawer inline: stub Teleport so the
// drawer renders in place, and stub ElSelect (the stockState filter), which
// otherwise hits a render recursion under the Teleport stub.
function mountViewInline() {
  return mount(InventoriesView, {
    global: { plugins: [createPinia(), ElementPlus], stubs: { teleport: true, ElSelect: true } },
  })
}

async function openRecords(wrapper: VueWrapper): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === '流水')
  await btn!.trigger('click')
  await flushPromises()
}

// Selecting the per-row 调整 action sets the adjust target before we drive the
// dialog's submit directly (the dialog body teleports, so we emit from the
// component instance instead of clicking inside the teleported footer).
async function openAdjust(wrapper: VueWrapper): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === '调整')
  await btn!.trigger('click')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('InventoriesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([inventory()]))
  })

  it('loads the first page on mount and renders rows', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(wrapper.text()).toContain('SKU-1')
  })

  it('searches with the product keyword', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[placeholder="按商品 ID 搜索"]').setValue('SKU')
    const searchBtn = wrapper.findAll('button').find((b) => b.text() === '搜索')
    await searchBtn!.trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, keyword: 'SKU' })
  })

  it('filters by the low-stock toggle', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.el-switch').trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, lowStock: true })
  })

  it('flags low-stock rows from backend data', async () => {
    mockedList.mockResolvedValue(pageOf([inventory({ lowStock: true })]))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('低库存')
  })

  it('initializes inventory and refreshes the list', async () => {
    mockedInit.mockResolvedValue(inventory())
    const wrapper = mountView()
    await flushPromises()
    expect(mockedList).toHaveBeenCalledTimes(1)

    wrapper.findComponent(InventoryInitDialog).vm.$emit('submit', {
      productId: 'SKU-9',
      initialStock: 50,
      safetyStock: 5,
    })
    await flushPromises()

    expect(mockedInit).toHaveBeenCalledWith({ productId: 'SKU-9', initialStock: 50, safetyStock: 5 })
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('surfaces a 40900 conflict when initialization fails', async () => {
    const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    mockedInit.mockRejectedValue(new ApiError('40900', '库存已初始化', 409))
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(InventoryInitDialog).vm.$emit('submit', {
      productId: 'SKU-1',
      initialStock: 1,
      safetyStock: 0,
    })
    await flushPromises()

    expect(error).toHaveBeenCalledWith('库存已初始化')
  })

  it('adjusts stock up with a generated requestId and refreshes', async () => {
    mockedAdjust.mockResolvedValue(inventory())
    const wrapper = mountView()
    await flushPromises()
    await openAdjust(wrapper)

    wrapper.findComponent(InventoryAdjustDialog).vm.$emit('submit', { delta: 5, reason: '补货' })
    await flushPromises()

    expect(mockedAdjust).toHaveBeenCalledTimes(1)
    const [productId, payload] = mockedAdjust.mock.calls[0]
    expect(productId).toBe('SKU-1')
    expect(payload.delta).toBe(5)
    expect(payload.reason).toBe('补货')
    expect(typeof payload.requestId).toBe('string')
    expect(payload.requestId.length).toBeGreaterThan(0)
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('adjusts stock down with a negative delta', async () => {
    mockedAdjust.mockResolvedValue(inventory())
    const wrapper = mountView()
    await flushPromises()
    await openAdjust(wrapper)

    wrapper.findComponent(InventoryAdjustDialog).vm.$emit('submit', { delta: -3, reason: '盘亏' })
    await flushPromises()

    expect(mockedAdjust.mock.calls[0][1].delta).toBe(-3)
  })

  it('mints a fresh requestId on each submit attempt', async () => {
    mockedAdjust.mockResolvedValue(inventory())
    const wrapper = mountView()
    await flushPromises()
    const dialog = wrapper.findComponent(InventoryAdjustDialog)

    await openAdjust(wrapper)
    dialog.vm.$emit('submit', { delta: 1, reason: 'a' })
    await flushPromises()
    await openAdjust(wrapper)
    dialog.vm.$emit('submit', { delta: 1, reason: 'b' })
    await flushPromises()

    expect(mockedAdjust).toHaveBeenCalledTimes(2)
    const id1 = mockedAdjust.mock.calls[0][1].requestId
    const id2 = mockedAdjust.mock.calls[1][1].requestId
    expect(id1).not.toBe(id2)
  })

  it('guards against a double submit while one is in flight', async () => {
    let resolve!: (value: AdminInventory) => void
    mockedAdjust.mockReturnValue(
      new Promise<AdminInventory>((r) => {
        resolve = r
      }),
    )
    const wrapper = mountView()
    await flushPromises()
    await openAdjust(wrapper)
    const dialog = wrapper.findComponent(InventoryAdjustDialog)

    dialog.vm.$emit('submit', { delta: 1, reason: 'x' })
    dialog.vm.$emit('submit', { delta: 1, reason: 'x' })
    await flushPromises()

    expect(mockedAdjust).toHaveBeenCalledTimes(1)
    resolve(inventory())
    await flushPromises()
  })

  it('surfaces a 40900 insufficient-stock error on adjust', async () => {
    const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    mockedAdjust.mockRejectedValue(new ApiError('40900', '库存不足', 409))
    const wrapper = mountView()
    await flushPromises()
    await openAdjust(wrapper)

    wrapper.findComponent(InventoryAdjustDialog).vm.$emit('submit', { delta: -999, reason: '超量出库' })
    await flushPromises()

    expect(error).toHaveBeenCalledWith('库存不足')
  })

  it('opens the records timeline and renders traceability fields', async () => {
    mockedRecords.mockResolvedValue([record()])
    const wrapper = mountViewInline()
    await flushPromises()
    await openRecords(wrapper)

    expect(mockedRecords).toHaveBeenCalledWith('SKU-1')
    const text = wrapper.text()
    expect(text).toContain('调增') // changeType label
    expect(text).toContain('管理员调整') // sourceType label
    expect(text).toContain('+8') // signed quantity from changeType
    expect(text).toContain('盘点入库') // reason
    expect(text).toContain('admin') // operator (adminUsername)
    expect(text).toContain('req-abc') // requestId traceability
  })

  it('shows an empty state when there are no records', async () => {
    mockedRecords.mockResolvedValue([])
    const wrapper = mountViewInline()
    await flushPromises()
    await openRecords(wrapper)

    expect(wrapper.text()).toContain('暂无库存流水')
  })
})
