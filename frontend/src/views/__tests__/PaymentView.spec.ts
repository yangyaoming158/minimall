import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'

// Mock vue-router with a reactive route + stub router. Expose the reactive
// proxy back so tests can mutate it and trigger the component's watchers.
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  const route = reactive({
    params: { orderNo: 'ORDER-A' } as Record<string, string>,
    query: {} as Record<string, string>,
    fullPath: '/payments/ORDER-A',
  })
  const router = { push: vi.fn(), replace: vi.fn() }
  return {
    useRoute: () => route,
    useRouter: () => router,
    __testRoute: route,
    __testRouter: router,
  }
})

vi.mock('@/api/order', () => ({
  getOrder: vi.fn(),
  createOrder: vi.fn(),
  listMyOrders: vi.fn(),
  cancelOrder: vi.fn(),
}))

vi.mock('@/api/payment', () => ({
  getPayment: vi.fn(),
  payOrder: vi.fn(),
}))

import * as VueRouter from 'vue-router'
import { getOrder } from '@/api/order'
import { getPayment, payOrder } from '@/api/payment'
import PaymentView from '@/views/PaymentView.vue'

interface TestRoute {
  params: Record<string, string>
  query: Record<string, string>
  fullPath: string
}
const mockRoute = (VueRouter as unknown as { __testRoute: TestRoute }).__testRoute

function makeOrder(orderNo: string, totalAmount = 100) {
  return {
    orderNo,
    status: 'PENDING_PAYMENT' as const,
    totalAmount,
    items: [],
    createdAt: '2026-05-23T10:00:00',
    updatedAt: '2026-05-23T10:00:00',
    expireAt: '2026-05-23T10:15:00',
    paidAt: null,
    closedAt: null,
  }
}

// Element Plus itself calls crypto.randomUUID for internal IDs, so we can't
// assert call counts. Instead, drive a token value the component will read on
// each generateKey() invocation. Tests update `currentKey` between phases so
// they know which value the component captured when.
let currentKey = 'key-a'

enableAutoUnmount(afterEach)

describe('PaymentView - route param reuse', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.params.orderNo = 'ORDER-A'
    mockRoute.fullPath = '/payments/ORDER-A'
    currentKey = 'key-a'
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => currentKey) })
    ;(getOrder as unknown as ReturnType<typeof vi.fn>).mockImplementation(
      async (no: string) => makeOrder(no, no === 'ORDER-A' ? 100 : 200),
    )
    ;(getPayment as unknown as ReturnType<typeof vi.fn>).mockRejectedValue(
      new Error('no payment record'),
    )
    ;(payOrder as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      paymentNo: 'PAY-1',
      orderNo: 'ORDER-B',
      amount: 200,
      channel: 'MOCK',
      status: 'SUCCESS',
      paidAt: '2026-05-23T10:01:00',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads the initial orderNo on mount', async () => {
    const wrapper = mount(PaymentView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(getOrder).toHaveBeenCalledWith('ORDER-A')
    expect(wrapper.text()).toContain('ORDER-A')
  })

  it('reloads with the new orderNo when the route param changes', async () => {
    const wrapper = mount(PaymentView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(getOrder).toHaveBeenLastCalledWith('ORDER-A')

    currentKey = 'key-b'
    mockRoute.params.orderNo = 'ORDER-B'
    mockRoute.fullPath = '/payments/ORDER-B'
    await nextTick()
    await flushPromises()

    expect(getOrder).toHaveBeenLastCalledWith('ORDER-B')
    expect(wrapper.text()).toContain('ORDER-B')
    expect(wrapper.text()).not.toContain('ORDER-A')
  })

  it('submits payment for the current orderNo with the regenerated key', async () => {
    const wrapper = mount(PaymentView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    currentKey = 'key-b'
    mockRoute.params.orderNo = 'ORDER-B'
    mockRoute.fullPath = '/payments/ORDER-B'
    await nextTick()
    await flushPromises()

    const payBtn = wrapper.findAll('button').find((b) => b.text().includes('确认支付'))
    expect(payBtn).toBeTruthy()
    await payBtn!.trigger('click')
    await flushPromises()

    expect(payOrder).toHaveBeenCalledTimes(1)
    expect(payOrder).toHaveBeenCalledWith('ORDER-B', {
      channel: 'MOCK',
      idempotencyKey: 'key-b',
    })
  })

  it('drops a stale in-flight load when orderNo changes mid-request', async () => {
    let resolveA: (v: unknown) => void = () => {}
    ;(getOrder as unknown as ReturnType<typeof vi.fn>).mockImplementationOnce(
      () => new Promise((r) => { resolveA = r }),
    )

    const wrapper = mount(PaymentView, { global: { plugins: [ElementPlus] } })
    await nextTick()

    currentKey = 'key-b'
    mockRoute.params.orderNo = 'ORDER-B'
    mockRoute.fullPath = '/payments/ORDER-B'
    await nextTick()
    await flushPromises()

    // The component should now show ORDER-B (loaded from the second call).
    expect(wrapper.text()).toContain('ORDER-B')

    // Resolve the stale ORDER-A request. The component must ignore the result
    // and keep showing ORDER-B.
    resolveA(makeOrder('ORDER-A', 100))
    await flushPromises()

    expect(wrapper.text()).toContain('ORDER-B')
    expect(wrapper.text()).not.toContain('ORDER-A')
  })
})
