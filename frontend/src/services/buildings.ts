import api from './api'
import type { Building, BuildingRequest } from '../types'

const BASE = '/buildings'

export const buildingsApi = {
  getAll: () => api.get<Building[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Building>(`${BASE}/${id}`).then((r) => r.data),
  create: (data: BuildingRequest) => api.post<Building>(BASE, data).then((r) => r.data),
  update: (id: number, data: BuildingRequest) =>
    api.put<Building>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
