import api from './api'
import type { Event, EventRequest } from '../types'

const BASE = '/events'

export const eventsApi = {
  getAll: () => api.get<Event[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Event>(`${BASE}/${id}`).then((r) => r.data),
  create: (data: EventRequest) => api.post<Event>(BASE, data).then((r) => r.data),
  applyToSchedule: (eventId: number, scheduleId: number) =>
    api.post(`${BASE}/${eventId}/apply/${scheduleId}`),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
