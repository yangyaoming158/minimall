import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { adminLogin, getAdminMe } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/token'
import type { AdminRole, LoginRequest } from '@/types/admin'

export interface CurrentAdmin {
  userId: number
  username: string
  role: AdminRole
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const currentAdmin = ref<CurrentAdmin | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(payload: LoginRequest): Promise<void> {
    const res = await adminLogin(payload)
    token.value = res.token
    setToken(res.token)
    // Login response already carries identity; no extra /me round-trip needed.
    currentAdmin.value = { userId: res.userId, username: res.username, role: res.role }
  }

  // Restores currentAdmin after a hard refresh, when the token survives in
  // localStorage but the in-memory identity is gone. A non-admin token yields a
  // 403 here (handled by the router guard -> /403); an invalid token yields a
  // 401 (handled by the http interceptor -> clear + /login).
  async function fetchCurrentAdmin(): Promise<void> {
    if (!token.value) {
      return
    }
    const me = await getAdminMe()
    currentAdmin.value = { userId: me.userId, username: me.username, role: me.role }
  }

  // Phase 2 MVP has no server-side logout; the frontend only clears local state.
  function logout(): void {
    token.value = null
    currentAdmin.value = null
    clearToken()
  }

  return { token, currentAdmin, isLoggedIn, login, fetchCurrentAdmin, logout }
})
