import api from './api'
import type { Schedule, ScheduleRequest, ClassSession, ScoreExplanation } from '../types'

const BASE = '/schedules'

export const schedulesApi = {
  getAll: () => api.get<Schedule[]>(BASE).then((r) => r.data),
  getById: (id: number) => api.get<Schedule>(`${BASE}/${id}`).then((r) => r.data),
  generate: (data: ScheduleRequest) =>
    api.post<Schedule>(`${BASE}/generate`, data).then((r) => r.data),
  partialResolve: (id: number, sessionIds: number[]) =>
    api.post<Schedule>(`${BASE}/${id}/partial-resolve`, sessionIds).then((r) => r.data),
  /** Plain-text explanation stored when the schedule was solved. */
  getExplanation: (id: number) =>
    api.get<string>(`${BASE}/${id}/explanation`).then((r) => r.data),
  /** Live Timefold constraint-breakdown for the current solution. */
  getScoreExplanation: (id: number) =>
    api.get<ScoreExplanation>(`${BASE}/${id}/score-explanation`).then((r) => r.data),
  getSessions: (id: number) =>
    api.get<ClassSession[]>(`${BASE}/${id}/sessions`).then((r) => r.data),
  delete: (id: number) => api.delete(`${BASE}/${id}`),
}
