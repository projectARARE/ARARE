import api from './api'
import type { Timeslot, TimeslotRequest } from '../types'

const BASE = '/timeslots'

export const timeslotsApi = {
  getAll: () => api.get<Timeslot[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Timeslot>(`${BASE}/${id}`).then((r) => r.data),
  create: (data: TimeslotRequest) =>
    api.post<Timeslot>(BASE, data).then((r) => r.data),
  update: (id: number, data: TimeslotRequest) =>
    api.put<Timeslot>(`${BASE}/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
