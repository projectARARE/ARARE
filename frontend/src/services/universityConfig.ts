import api from './api'
import type { UniversityConfig } from '../types'

const BASE = '/university-config'

export const universityConfigApi = {
  get: () => api.get<UniversityConfig>(BASE).then((r) => r.data),
  save: (data: UniversityConfig) =>
    api.post<UniversityConfig>(BASE, data).then((r) => r.data),
}
