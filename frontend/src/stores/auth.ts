import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as apiLogin, register as apiRegister } from '@/api/user'
import { clearToken, getToken, setToken } from '@/utils/token'
import type { LoginRequest, RegisterRequest, UserResponse } from '@/types/user'

export interface CurrentUser {
  userId: number
  username: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const currentUser = ref<CurrentUser | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(payload: LoginRequest): Promise<void> {
    const res = await apiLogin(payload)
    token.value = res.token
    setToken(res.token)
    // Login response already carries identity; no extra /me round-trip needed.
    currentUser.value = { userId: res.userId, username: res.username }
  }

  async function register(payload: RegisterRequest): Promise<UserResponse> {
    return apiRegister(payload)
  }

  // Restores currentUser after a hard refresh, when the token survives in
  // localStorage but the in-memory user is gone.
  async function fetchCurrentUser(): Promise<void> {
    if (!token.value) {
      return
    }
    const me = await getCurrentUser()
    currentUser.value = { userId: me.userId, username: me.username }
  }

  function logout(): void {
    token.value = null
    currentUser.value = null
    clearToken()
  }

  return { token, currentUser, isLoggedIn, login, register, fetchCurrentUser, logout }
})
