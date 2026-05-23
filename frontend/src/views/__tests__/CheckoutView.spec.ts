import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'

vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  const route = reactive({
    params: {} as Record<string, string>,
    query: { productId: 'SKU-A', quantity: '1' } as Record<string, string>,
    fullPath: '/checkout?productId=SKU-A&quantity=1',
  })
  const router = { push: vi.fn(), replace: vi.fn() }
  return {
    useRoute: () => route,
    useRouter: () => router,
    __testRoute: route,
    __testRouter: router,
  }
})

vi.mock('@/api/product', () => ({
  getProduct: vi.fn(),
  listProducts: vi.fn(),
}))

vi.mock('@/api/inventory', () => ({
  getInventory: vi.fn(),
}))

vi.mock('@/api/order', () => ({
  createOrder: vi.fn(),
  getOrder: vi.fn(),
  listMyOrders: vi.fn(),
  cancelOrder: vi.fn(),
}))

import * as VueRouter from 'vue-router'
import { getProduct } from '@/api/product'
import { getInventory } from '@/api/inventory'
import { createOrder } from '@/api/order'
import CheckoutView from '@/views/CheckoutView.vue'

interface TestRoute {
  params: Record<string, string>
  query: Record<string, string>
  fullPath: string
}
const mockRoute = (VueRouter as unknown as { __testRoute: TestRoute }).__testRoute

function makeProduct(productId: string, price = 50) {
  return {
    productId,
    name: `Product ${productId}`,
    description: 'test',
    price,
    status: 'ON_SHELF' as const,
  }
}

function makeInventory(productId: string, availableStock = 10) {
  return {
    productId,
    availableStock,
    lockedStock: 0,
    stockState: 'IN_STOCK' as const,
  }
}

let currentKey = 'key-a'

enableAutoUnmount(afterEach)

describe('CheckoutView - query param reuse', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Replace the whole query object so reactivity sees a clean slate per test.
    mockRoute.query = { productId: 'SKU-A', quantity: '1' }
    mockRoute.fullPath = '/checkout?productId=SKU-A&quantity=1'
    currentKey = 'key-a'
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => currentKey) })
    ;(getProduct as unknown as ReturnType<typeof vi.fn>).mockImplementation(
      async (id: string) => makeProduct(id, id === 'SKU-A' ? 50 : 80),
    )
    ;(getInventory as unknown as ReturnType<typeof vi.fn>).mockImplementation(
      async (id: string) => makeInventory(id, 10),
    )
    ;(createOrder as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      orderNo: 'ORD-1',
      status: 'PENDING_PAYMENT',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads SKU-A on mount', async () => {
    const wrapper = mount(CheckoutView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(getProduct).toHaveBeenCalledWith('SKU-A')
    expect(getInventory).toHaveBeenCalledWith('SKU-A')
    expect(wrapper.text()).toContain('SKU-A')
  })

  it('reloads with SKU-B when the query changes', async () => {
    const wrapper = mount(CheckoutView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(getProduct).toHaveBeenLastCalledWith('SKU-A')

    currentKey = 'key-b'
    mockRoute.query.productId = 'SKU-B'
    mockRoute.query.quantity = '2'
    mockRoute.fullPath = '/checkout?productId=SKU-B&quantity=2'
    await nextTick()
    await flushPromises()

    expect(getProduct).toHaveBeenLastCalledWith('SKU-B')
    expect(getInventory).toHaveBeenLastCalledWith('SKU-B')
    expect(wrapper.text()).toContain('SKU-B')
    expect(wrapper.text()).not.toContain('Product SKU-A')
  })

  it('submits createOrder with the current query and the regenerated key', async () => {
    const wrapper = mount(CheckoutView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    currentKey = 'key-b'
    mockRoute.query.productId = 'SKU-B'
    mockRoute.query.quantity = '2'
    mockRoute.fullPath = '/checkout?productId=SKU-B&quantity=2'
    await nextTick()
    await flushPromises()

    const submitBtn = wrapper.findAll('button').find((b) => b.text().includes('确认下单'))
    expect(submitBtn).toBeTruthy()
    await submitBtn!.trigger('click')
    await flushPromises()

    expect(createOrder).toHaveBeenCalledTimes(1)
    expect(createOrder).toHaveBeenCalledWith({
      productId: 'SKU-B',
      quantity: 2,
      idempotencyKey: 'key-b',
    })
  })

  it('does not load or submit when params are invalid', async () => {
    mockRoute.query = {}
    mockRoute.fullPath = '/checkout'

    const wrapper = mount(CheckoutView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(getProduct).not.toHaveBeenCalled()
    expect(getInventory).not.toHaveBeenCalled()
    expect(createOrder).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('参数无效')
  })

  it('drops a stale in-flight product load when query changes mid-request', async () => {
    let resolveA: (v: unknown) => void = () => {}
    ;(getProduct as unknown as ReturnType<typeof vi.fn>).mockImplementationOnce(
      () => new Promise((r) => { resolveA = r }),
    )

    const wrapper = mount(CheckoutView, { global: { plugins: [ElementPlus] } })
    await nextTick()

    currentKey = 'key-b'
    mockRoute.query.productId = 'SKU-B'
    mockRoute.query.quantity = '2'
    mockRoute.fullPath = '/checkout?productId=SKU-B&quantity=2'
    await nextTick()
    await flushPromises()

    // Should now display SKU-B from the second load.
    expect(wrapper.text()).toContain('SKU-B')

    // Resolve the stale SKU-A request. The component must ignore the result.
    resolveA(makeProduct('SKU-A', 50))
    await flushPromises()

    expect(wrapper.text()).toContain('SKU-B')
    expect(wrapper.text()).not.toContain('Product SKU-A')
  })
})
