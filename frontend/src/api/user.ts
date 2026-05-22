import { get, post } from './http'
import type {
  CurrentUserResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserResponse,
} from '@/types/user'

export function register(payload: RegisterRequest): Promise<UserResponse> {
  return post<UserResponse>('/api/users/register', payload)
}

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/api/users/login', payload)
}

export function getCurrentUser(): Promise<CurrentUserResponse> {
  return get<CurrentUserResponse>('/api/users/me')
}
