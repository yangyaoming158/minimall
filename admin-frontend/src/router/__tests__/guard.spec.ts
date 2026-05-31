import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@/types/api'

// Mock the auth API so the guard's /api/admin/me check is deterministic.
vi.mock('@/api/auth', () => ({
  adminLogin: vi.fn(),
  getAdminMe: vi.fn(),
}))

import router from '@/router'
import { getAdminMe } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const mockedGetAdminMe = vi.mocked(getAdminMe)

describe('admin router guard', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    mockedGetAdminMe.mockReset()
  })

  it('redirects an unauthenticated user from a protected route to /login', async () => {
    await router.replace('/products')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(mockedGetAdminMe).not.toHaveBeenCalled()
  })

  it('sends a non-admin token to /403 when /api/admin/me is forbidden', async () => {
    useAuthStore().token = 'non-admin-token'
    mockedGetAdminMe.mockRejectedValue(new ApiError('40300', 'forbidden', 403))

    await router.replace('/products')

    expect(router.currentRoute.value.path).toBe('/403')
  })

  it('admits an admin into a protected route after /api/admin/me succeeds', async () => {
    useAuthStore().token = 'admin-token'
    mockedGetAdminMe.mockResolvedValue({ userId: 1, username: 'admin', role: 'ADMIN' })

    await router.replace('/products')

    expect(router.currentRoute.value.path).toBe('/products')
    expect(mockedGetAdminMe).toHaveBeenCalledOnce()
  })

  it('redirects an authenticated admin away from the guest-only login page', async () => {
    useAuthStore().token = 'admin-token'
    mockedGetAdminMe.mockResolvedValue({ userId: 1, username: 'admin', role: 'ADMIN' })
    await router.replace('/products')

    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/products')
  })

  it('clears a residual non-admin token and shows /login instead of looping', async () => {
    const auth = useAuthStore()
    auth.token = 'non-admin-token'
    mockedGetAdminMe.mockRejectedValue(new ApiError('40300', 'forbidden', 403))

    await router.replace('/login')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(auth.token).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
  })
})
