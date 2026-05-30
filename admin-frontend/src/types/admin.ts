// Mirrors user-service DTOs: LoginResponse and CurrentUserResponse.
// role is the backend UserRole enum, serialized as a stable name.
export type AdminRole = 'USER' | 'ADMIN'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  userId: number
  username: string
  role: AdminRole
}

export interface CurrentUserResponse {
  userId: number
  username: string
  role: AdminRole
}
