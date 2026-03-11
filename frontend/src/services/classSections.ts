import api from './api'
import type { ClassSection, ClassSectionRequest } from '../types'

const BASE = '/class-sections'

export const classSectionsApi = {
  getAll: () => api.get<ClassSection[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<ClassSection>(`${BASE}/${id}`).then((r) => r.data),
  getByBatch: (batchId: number) =>
    api.get<ClassSection[]>(`${BASE}/batch/${batchId}`).then((r) => r.data),
  create: (data: ClassSectionRequest) =>
    api.post<ClassSection>(BASE, data).then((r) => r.data),
  update: (id: number, data: ClassSectionRequest) =>
    api.put<ClassSection>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
