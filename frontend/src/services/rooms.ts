import api from './api'
import type { Room, RoomRequest } from '../types'

const BASE = '/rooms'

export const roomsApi = {
  getAll: () => api.get<Room[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Room>(`${BASE}/${id}`).then((r) => r.data),
  getByBuilding: (buildingId: number) =>
    api.get<Room[]>(`${BASE}?buildingId=${buildingId}`).then((r) => r.data),
  create: (data: RoomRequest) => api.post<Room>(BASE, data).then((r) => r.data),
  update: (id: number, data: RoomRequest) =>
    api.put<Room>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
