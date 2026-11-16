import { api } from './http'
import type { Employee, EmployeePayload, EmployeeQuery, PageResponse } from './types'

export function searchEmployees(query: EmployeeQuery): Promise<PageResponse<Employee>> {
  return api.get<PageResponse<Employee>>('/employees', {
    firstName: query.firstName || undefined,
    lastName: query.lastName || undefined,
    email: query.email || undefined,
    jobTitle: query.jobTitle || undefined,
    departmentId: query.departmentId,
    minSalary: query.minSalary,
    maxSalary: query.maxSalary,
    page: query.page,
    size: query.size,
    sort: query.sort
  })
}

export function createEmployee(payload: EmployeePayload): Promise<Employee> {
  return api.post<Employee>('/employees', payload)
}

export function updateEmployee(id: number, payload: EmployeePayload): Promise<Employee> {
  return api.put<Employee>(`/employees/${id}`, payload)
}

export function deleteEmployee(id: number): Promise<void> {
  return api.delete<void>(`/employees/${id}`)
}
