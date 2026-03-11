import api from './api'
import type { Batch, BatchRequest } from '../types'

const BASE = '/batches'

export const batchesApi = {
  getAll: () => api.get<Batch[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Batch>(`${BASE}/${id}`).then((r) => r.data),
  getByDepartment: (deptId: number) =>
    api.get<Batch[]>(`${BASE}?departmentId=${deptId}`).then((r) => r.data),
  create: (data: BatchRequest) => api.post<Batch>(BASE, data).then((r) => r.data),
  update: (id: number, data: BatchRequest) =>
    api.put<Batch>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
