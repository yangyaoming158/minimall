import { get, post } from './http'
import type { CurrentUserResponse, LoginRequest, LoginResponse } from '@/types/admin'

export function adminLogin(payload: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/api/admin/login', payload)
}

export function getAdminMe(): Promise<CurrentUserResponse> {
  return get<CurrentUserResponse>('/api/admin/me')
}
