import api from './api'
import type { Subject, SubjectRequest } from '../types'

const BASE = '/subjects'

export const subjectsApi = {
  getAll: () => api.get<Subject[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Subject>(`${BASE}/${id}`).then((r) => r.data),
  getByDepartment: (deptId: number) =>
    api.get<Subject[]>(`${BASE}?departmentId=${deptId}`).then((r) => r.data),
  create: (data: SubjectRequest) => api.post<Subject>(BASE, data).then((r) => r.data),
  update: (id: number, data: SubjectRequest) =>
    api.put<Subject>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
