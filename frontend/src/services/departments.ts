import api from './api'
import type { Department, DepartmentRequest } from '../types'

const BASE = '/departments'

export const departmentsApi = {
  getAll: () => api.get<Department[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Department>(`${BASE}/${id}`).then((r) => r.data),
  create: (data: DepartmentRequest) => api.post<Department>(BASE, data).then((r) => r.data),
  update: (id: number, data: DepartmentRequest) =>
    api.put<Department>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
