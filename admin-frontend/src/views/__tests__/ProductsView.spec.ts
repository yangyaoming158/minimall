import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessageBox } from 'element-plus'
import type { AdminProduct } from '@/types/product'

vi.mock('@/api/products', () => ({
  listProducts: vi.fn(),
  getProduct: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  updateProductStatus: vi.fn(),
}))

import { listProducts, updateProductStatus } from '@/api/products'
import ProductsView from '@/views/ProductsView.vue'

const mockedList = vi.mocked(listProducts)
const mockedToggle = vi.mocked(updateProductStatus)

function product(over: Partial<AdminProduct> = {}): AdminProduct {
  return {
    productId: 'SKU-1',
    name: 'Widget',
    description: 'a widget',
    imageUrl: null,
    price: 9.9,
    status: 'ON_SHELF',
    createdAt: '2026-05-01T00:00:00',
    updatedAt: '2026-05-01T00:00:00',
    ...over,
  }
}

function pageOf(content: AdminProduct[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function mountView() {
  return mount(ProductsView, { global: { plugins: [createPinia(), ElementPlus] } })
}

enableAutoUnmount(afterEach)

describe('ProductsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([product()]))
  })

  it('loads the first page on mount and renders rows', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(wrapper.text()).toContain('Widget')
  })

  it('searches with keyword and status filters', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[placeholder="按商品 ID / 名称搜索"]').setValue('SKU')
    const searchBtn = wrapper.findAll('button').find((b) => b.text() === '搜索')
    await searchBtn!.trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, keyword: 'SKU' })
  })

  it('toggles status to OFF_SHELF after confirmation', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    mockedToggle.mockResolvedValue(product({ status: 'OFF_SHELF' }))

    const wrapper = mountView()
    await flushPromises()

    const offBtn = wrapper.findAll('button').find((b) => b.text() === '下架')
    await offBtn!.trigger('click')
    await flushPromises()

    expect(mockedToggle).toHaveBeenCalledWith('SKU-1', 'OFF_SHELF')
  })

  it('does not change status when the confirmation is cancelled', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')

    const wrapper = mountView()
    await flushPromises()

    const offBtn = wrapper.findAll('button').find((b) => b.text() === '下架')
    await offBtn!.trigger('click')
    await flushPromises()

    expect(mockedToggle).not.toHaveBeenCalled()
  })
})
