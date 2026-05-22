export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface LoginRequest {
  username: string
  password: string
}

// password length must be between 6 and 128 (backend @Size constraint)
export interface RegisterRequest {
  username: string
  password: string
  email?: string
  phone?: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  userId: number
  username: string
}

export interface UserResponse {
  userId: number
  username: string
  email: string | null
  phone: string | null
  status: UserStatus
}

export interface CurrentUserResponse {
  userId: number
  username: string
}
