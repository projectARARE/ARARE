import api from './api'
import type { Teacher, TeacherRequest } from '../types'

const BASE = '/teachers'

export const teachersApi = {
  getAll: () => api.get<Teacher[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Teacher>(`${BASE}/${id}`).then((r) => r.data),
  create: (data: TeacherRequest) => api.post<Teacher>(BASE, data).then((r) => r.data),
  update: (id: number, data: TeacherRequest) =>
    api.put<Teacher>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
