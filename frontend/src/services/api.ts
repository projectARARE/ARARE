import axios from 'axios'
import type {
  Building,
  BuildingRequest,
  Department,
  DepartmentRequest,
  Room,
  RoomRequest,
  Teacher,
  TeacherRequest,
  Subject,
  SubjectRequest,
  Batch,
  BatchRequest,
  ClassSection,
  ClassSectionRequest,
  Timeslot,
  TimeslotRequest,
  UniversityConfig,
  UniversityConfigRequest,
  Schedule,
  ScheduleRequest,
  ScoreExplanation,
  ClassSession,
  Event,
  EventRequest,
  SessionAssignmentRequest,
  AcademicTerm,
  AcademicTermRequest,
  DisruptionRequest,
  DisruptionResponse,
  FeasibilityCheckResult,
} from '../types'

const api = axios.create({ baseURL: '/api/v1' })

api.interceptors.response.use(
  (r) => r,
  (err) => {
    const msg =
      (err.response?.data as { detail?: string; message?: string } | undefined)?.detail ||
      (err.response?.data as { detail?: string; message?: string } | undefined)?.message ||
      (err.message as string)
    return Promise.reject(new Error(msg))
  },
)

// Default export for backward-compat with existing service files
export default api

// Buildings
export const buildingApi = {
  getAll: () => api.get<Building[]>('/buildings').then((r) => r.data),
  getById: (id: number) => api.get<Building>(`/buildings/${id}`).then((r) => r.data),
  create: (data: BuildingRequest) => api.post<Building>('/buildings', data).then((r) => r.data),
  update: (id: number, data: BuildingRequest) =>
    api.put<Building>(`/buildings/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/buildings/${id}`),
}

// Departments
export const departmentApi = {
  getAll: () => api.get<Department[]>('/departments').then((r) => r.data),
  getById: (id: number) => api.get<Department>(`/departments/${id}`).then((r) => r.data),
  create: (data: DepartmentRequest) =>
    api.post<Department>('/departments', data).then((r) => r.data),
  update: (id: number, data: DepartmentRequest) =>
    api.put<Department>(`/departments/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/departments/${id}`),
}

// Rooms
export const roomApi = {
  getAll: () => api.get<Room[]>('/rooms').then((r) => r.data),
  getById: (id: number) => api.get<Room>(`/rooms/${id}`).then((r) => r.data),
  create: (data: RoomRequest) => api.post<Room>('/rooms', data).then((r) => r.data),
  update: (id: number, data: RoomRequest) =>
    api.put<Room>(`/rooms/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/rooms/${id}`),
}

// Teachers
export const teacherApi = {
  getAll: () => api.get<Teacher[]>('/teachers').then((r) => r.data),
  getById: (id: number) => api.get<Teacher>(`/teachers/${id}`).then((r) => r.data),
  create: (data: TeacherRequest) => api.post<Teacher>('/teachers', data).then((r) => r.data),
  update: (id: number, data: TeacherRequest) =>
    api.put<Teacher>(`/teachers/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/teachers/${id}`),
}

// Subjects
export const subjectApi = {
  getAll: () => api.get<Subject[]>('/subjects').then((r) => r.data),
  getById: (id: number) => api.get<Subject>(`/subjects/${id}`).then((r) => r.data),
  create: (data: SubjectRequest) => api.post<Subject>('/subjects', data).then((r) => r.data),
  update: (id: number, data: SubjectRequest) =>
    api.put<Subject>(`/subjects/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/subjects/${id}`),
}

// Batches
export const batchApi = {
  getAll: () => api.get<Batch[]>('/batches').then((r) => r.data),
  getById: (id: number) => api.get<Batch>(`/batches/${id}`).then((r) => r.data),
  create: (data: BatchRequest) => api.post<Batch>('/batches', data).then((r) => r.data),
  update: (id: number, data: BatchRequest) =>
    api.put<Batch>(`/batches/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/batches/${id}`),
}

// Class Sections
export const classSectionApi = {
  getAll: () => api.get<ClassSection[]>('/class-sections').then((r) => r.data),
  getByBatch: (batchId: number) =>
    api.get<ClassSection[]>(`/class-sections/batch/${batchId}`).then((r) => r.data),
  getById: (id: number) => api.get<ClassSection>(`/class-sections/${id}`).then((r) => r.data),
  create: (data: ClassSectionRequest) =>
    api.post<ClassSection>('/class-sections', data).then((r) => r.data),
  update: (id: number, data: ClassSectionRequest) =>
    api.put<ClassSection>(`/class-sections/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/class-sections/${id}`),
}

// Timeslots
export const timeslotApi = {
  getAll: () => api.get<Timeslot[]>('/timeslots').then((r) => r.data),
  getById: (id: number) => api.get<Timeslot>(`/timeslots/${id}`).then((r) => r.data),
  create: (data: TimeslotRequest) => api.post<Timeslot>('/timeslots', data).then((r) => r.data),
  update: (id: number, data: TimeslotRequest) =>
    api.put<Timeslot>(`/timeslots/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/timeslots/${id}`),
}

// University Config  (singleton endpoint – only one active config at a time)
export const universityConfigApi = {
  get: () => api.get<UniversityConfig>('/university-config').then((r) => r.data),
  save: (data: UniversityConfigRequest) =>
    api.post<UniversityConfig>('/university-config', data).then((r) => r.data),
}

// Schedules
export const scheduleApi = {
  getAll: () => api.get<Schedule[]>('/schedules').then((r) => r.data),
  getById: (id: number) => api.get<Schedule>(`/schedules/${id}`).then((r) => r.data),
  generate: (data: ScheduleRequest) =>
    api.post<Schedule>('/schedules/generate', data).then((r) => r.data),
  partialResolve: (id: number, sessionIds: number[]) =>
    api.post<Schedule>(`/schedules/${id}/partial-resolve`, sessionIds).then((r) => r.data),
  getScoreExplanation: (id: number) =>
    api.get<ScoreExplanation>(`/schedules/${id}/score-explanation`).then((r) => r.data),
  getExplanation: (id: number) =>
    api.get<string>(`/schedules/${id}/explanation`).then((r) => r.data),
  getSessions: (id: number) =>
    api.get<ClassSession[]>(`/sessions/schedule/${id}`).then((r) => r.data),
  delete: (id: number) => api.delete(`/schedules/${id}`),
  previewDisruption: (id: number, data: DisruptionRequest) =>
    api.post<DisruptionResponse>(`/schedules/${id}/disruption/preview`, data).then((r) => r.data),
  applyDisruption: (id: number, data: DisruptionRequest) =>
    api.post<Schedule>(`/schedules/${id}/disruption/apply`, data).then((r) => r.data),
  exportCsv: (id: number) =>
    api.get(`/schedules/${id}/export/csv`, { responseType: 'blob' }).then((r) => r.data as Blob),
  checkFeasibility: (req: Partial<ScheduleRequest>) =>
    api.post<FeasibilityCheckResult>('/schedules/feasibility-check', req).then((r) => r.data),
}

// Sessions (manual editing of timetable)
export const sessionApi = {
  updateAssignment: (id: number, data: SessionAssignmentRequest) =>
    api.patch<ClassSession>(`/sessions/${id}`, data).then((r) => r.data),
}

// Events
export const eventApi = {
  getAll: () => api.get<Event[]>('/events').then((r) => r.data),
  getById: (id: number) => api.get<Event>(`/events/${id}`).then((r) => r.data),
  create: (data: EventRequest) => api.post<Event>('/events', data).then((r) => r.data),
  update: (id: number, data: EventRequest) =>
    api.put<Event>(`/events/${id}`, data).then((r) => r.data),
  applyToSchedule: (id: number, scheduleId: number) =>
    api.post(`/events/${id}/apply/${scheduleId}`),
  delete: (id: number) => api.delete(`/events/${id}`),
}

// Academic Terms
export const academicTermApi = {
  getAll: () => api.get<AcademicTerm[]>('/academic-terms').then((r) => r.data),
  getById: (id: number) => api.get<AcademicTerm>(`/academic-terms/${id}`).then((r) => r.data),
  create: (data: AcademicTermRequest) =>
    api.post<AcademicTerm>('/academic-terms', data).then((r) => r.data),
  update: (id: number, data: AcademicTermRequest) =>
    api.put<AcademicTerm>(`/academic-terms/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/academic-terms/${id}`),
}

