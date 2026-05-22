import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import { ApiError, type ApiResponse } from '@/types/api'
import { clearToken, getToken } from '@/utils/token'

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

function redirectToLogin(): void {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (!body.success) {
      throw new ApiError(body.code, body.message, response.status)
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (!error.response) {
      const message =
        error.code === 'ECONNABORTED'
          ? '请求超时，请稍后重试'
          : '网络异常，请检查网络后重试'
      ElMessage.error(message)
      return Promise.reject(new ApiError('NETWORK_ERROR', message))
    }

    const status = error.response.status
    const body = error.response.data
    const code = body?.code ?? String(status)
    const message = body?.message ?? '请求失败'

    switch (status) {
      case 401:
        clearToken()
        redirectToLogin()
        break
      case 403:
        ElMessage.error('没有权限执行此操作')
        break
      case 429:
        ElMessage.warning('操作过于频繁，请稍后再试')
        break
      case 500:
        ElMessage.error('服务器开小差了，请稍后重试')
        break
      default:
        break
    }

    return Promise.reject(new ApiError(code, message, status))
  },
)

export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.get<ApiResponse<T>>(url, config)
  return response.data.data
}

export async function post<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await instance.post<ApiResponse<T>>(url, data, config)
  return response.data.data
}
