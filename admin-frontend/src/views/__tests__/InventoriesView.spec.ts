import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type { AdminInventory } from '@/types/inventory'

vi.mock('@/api/inventories', () => ({
  listInventories: vi.fn(),
  getInventory: vi.fn(),
}))

import { listInventories } from '@/api/inventories'
import InventoriesView from '@/views/InventoriesView.vue'

const mockedList = vi.mocked(listInventories)

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

function mountView() {
  return mount(InventoriesView, { global: { plugins: [createPinia(), ElementPlus] } })
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
})
