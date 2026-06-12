import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ApiError } from '@/types/api'

vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  const route = reactive({ query: {} as Record<string, string>, fullPath: '/login' })
  const router = { push: vi.fn(), replace: vi.fn() }
  return {
    useRoute: () => route,
    useRouter: () => router,
    __testRoute: route,
    __testRouter: router,
  }
})

vi.mock('@/api/auth', () => ({
  adminLogin: vi.fn(),
  getAdminMe: vi.fn(),
}))

import * as VueRouter from 'vue-router'
import { adminLogin } from '@/api/auth'
import { getToken } from '@/utils/token'
import LoginView from '@/views/LoginView.vue'

interface TestRoute {
  query: Record<string, string>
  fullPath: string
}
interface TestRouter {
  push: ReturnType<typeof vi.fn>
  replace: ReturnType<typeof vi.fn>
}
const mockRoute = (VueRouter as unknown as { __testRoute: TestRoute }).__testRoute
const mockRouter = (VueRouter as unknown as { __testRouter: TestRouter }).__testRouter
const mockedAdminLogin = vi.mocked(adminLogin)

function mountLogin() {
  return mount(LoginView, { global: { plugins: [createPinia(), ElementPlus] } })
}

async function fillAndSubmit(
  wrapper: ReturnType<typeof mountLogin>,
  username: string,
  password: string,
): Promise<void> {
  const inputs = wrapper.findAll('input')
  await inputs[0].setValue(username)
  await inputs[1].setValue(password)
  const submit = wrapper.findAll('button').find((b) => b.text().includes('登录'))
  await submit!.trigger('click')
  await flushPromises()
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    mockRoute.query = {}
    mockRoute.fullPath = '/login'
  })

  it('logs an admin in, stores the token, and enters /products', async () => {
    mockedAdminLogin.mockResolvedValue({
      token: 'admin-token',
      tokenType: 'Bearer',
      userId: 1,
      username: 'admin',
      role: 'ADMIN',
    })

    const wrapper = mountLogin()
    await fillAndSubmit(wrapper, 'admin', 'secret')

    expect(mockedAdminLogin).toHaveBeenCalledWith({ username: 'admin', password: 'secret' })
    expect(getToken()).toBe('admin-token')
    expect(mockRouter.push).toHaveBeenCalledWith('/products')
  })

  it('honors a same-origin redirect query on success', async () => {
    mockRoute.query = { redirect: '/orders' }
    mockedAdminLogin.mockResolvedValue({
      token: 'admin-token',
      tokenType: 'Bearer',
      userId: 1,
      username: 'admin',
      role: 'ADMIN',
    })

    const wrapper = mountLogin()
    await fillAndSubmit(wrapper, 'admin', 'secret')

    expect(mockRouter.push).toHaveBeenCalledWith('/orders')
  })

  it('rejects a non-admin (403) with a permission message and no navigation', async () => {
    mockedAdminLogin.mockRejectedValue(new ApiError('40300', 'Forbidden', 403))

    const wrapper = mountLogin()
    await fillAndSubmit(wrapper, 'user', 'secret')

    expect(mockRouter.push).not.toHaveBeenCalled()
    expect(getToken()).toBeNull()
    expect(wrapper.text()).toContain('没有管理员权限')
  })

  it('surfaces bad credentials (401) and does not navigate', async () => {
    mockedAdminLogin.mockRejectedValue(
      new ApiError('40100', 'Username or password is incorrect', 401),
    )

    const wrapper = mountLogin()
    await fillAndSubmit(wrapper, 'admin', 'wrong')

    expect(mockRouter.push).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Username or password is incorrect')
  })
})
