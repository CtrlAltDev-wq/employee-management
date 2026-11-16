import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from './types'
import { clearSession, getToken } from '@/utils/session'

/**
 * Vite proxies /api in development and Nginx does the same in production, so
 * the SPA always calls a same-origin path and CORS never comes into play.
 */
const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api'

const client = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

client.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let redirectingToLogin = false

function redirectToLogin(): void {
  if (redirectingToLogin) {
    return
  }
  redirectingToLogin = true
  clearSession()
  const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
  window.location.replace(`/login?redirect=${redirect}`)
}

function describeError(status: number | undefined, body: ApiResult<unknown> | undefined): string {
  const fieldErrors = body?.fieldErrors
  if (fieldErrors && Object.keys(fieldErrors).length > 0) {
    return Object.values(fieldErrors).join(' ')
  }
  if (body?.message) {
    return body.message
  }
  if (status === undefined) {
    return 'Cannot reach the API. Is the backend running?'
  }
  if (status === 500) {
    return 'The server failed to process the request.'
  }
  return `Request failed with status ${status}.`
}

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data

    if (status === 401) {
      if (window.location.pathname === '/login') {
        // A failed sign-in is shown on the form itself, not as a redirect.
        ElMessage.error(body?.message ?? 'Invalid username or password.')
      } else {
        ElMessage.error('Your session has expired. Please sign in again.')
        redirectToLogin()
      }
    } else if (status === 403) {
      ElMessage.error('You do not have permission to perform this action.')
    } else {
      ElMessage.error(describeError(status, body))
    }

    return Promise.reject(error)
  }
)

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await client.request<ApiResult<T>>(config)
  return response.data.data as T
}

/** Thin typed facade over the Axios instance; the envelope is unwrapped here. */
export const api = {
  get: <T>(url: string, params?: Record<string, unknown>) => request<T>({ url, method: 'get', params }),
  post: <T>(url: string, data?: unknown) => request<T>({ url, method: 'post', data }),
  put: <T>(url: string, data?: unknown) => request<T>({ url, method: 'put', data }),
  delete: <T>(url: string) => request<T>({ url, method: 'delete' })
}
