/**
 * API contracts mirroring the backend DTOs.
 */

/** Envelope returned by every endpoint, success or failure. */
export interface ApiResult<T> {
  code: number
  message: string
  data?: T
  path?: string
  requestId?: string
  fieldErrors?: Record<string, string>
  timestamp?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface Employee {
  id: number
  firstName: string
  lastName: string
  email: string
  jobTitle: string | null
  salary: number
  departmentId: number | null
  createdAt: string
}

/** Payload accepted by both POST and PUT /api/employees. */
export interface EmployeePayload {
  firstName: string
  lastName: string
  email: string
  jobTitle: string | null
  salary: number
  departmentId: number | null
}

export interface EmployeeQuery {
  firstName?: string
  lastName?: string
  email?: string
  jobTitle?: string
  departmentId?: number
  minSalary?: number
  maxSalary?: number
  page: number
  size: number
  sort: string
}

export interface DepartmentEmployee {
  id: number
  firstName: string
  lastName: string
  email: string
  jobTitle: string | null
}

export interface Department {
  id: number
  name: string
  employees: DepartmentEmployee[]
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  username: string
  /** Granted authorities, e.g. ["ROLE_ADMIN"]. */
  roles: string[]
}
