import { api } from './http'
import type { LoginRequest, LoginResponse } from './types'

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return api.post<LoginResponse>('/auth/login', payload)
}
