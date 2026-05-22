export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PageParams {
  page?: number
  size?: number
  sort?: string
}

export const ErrorCode = {
  SUCCESS: '0',
  BAD_REQUEST: '40000',
  VALIDATION_ERROR: '40001',
  UNAUTHORIZED: '40100',
  FORBIDDEN: '40300',
  TOO_MANY_REQUESTS: '42900',
  NOT_FOUND: '40400',
  CONFLICT: '40900',
  ORDER_CANCELLED: '40901',
  ORDER_INVALID_STATE: '40902',
  PAYMENT_ALREADY_SUCCESS: '40903',
  INTERNAL_ERROR: '50000',
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

export class ApiError extends Error {
  readonly code: string
  readonly httpStatus?: number

  constructor(code: string, message: string, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}
