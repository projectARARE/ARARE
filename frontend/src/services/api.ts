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
  ConflictSuggestion,
  CsvZipImportResponse,
  CsvImportResponse,
  ImportOrderStep,
  SessionCreateRequest,
  SolveJobResponse,
  PreAllocation,
  PreAllocationRequest,
  TeacherAssignment,
  TeacherAssignmentRequest,
  Institute,
  InstituteRequest,
  SubjectOffering,
  SubjectOfferingRequest,
} from '../types'

const api = axios.create({ baseURL: '/api/v1', timeout: 20000 })

function toMessage(value: unknown): string | null {
  if (value == null) return null
  if (typeof value === 'string') return value.trim() || null
  if (Array.isArray(value)) {
    const parts = value
      .map((v) => {
        if (v == null) return null
        if (typeof v === 'string') return v
        if (typeof v === 'object') {
          const o = v as { message?: unknown; detail?: unknown; msg?: unknown; error?: unknown }
          const inner = o.message ?? o.detail ?? o.msg ?? o.error
          return inner != null && typeof inner === 'string' ? inner : null
        }
        return String(v)
      })
      .filter((v): v is string => !!v)
    return parts.length > 0 ? parts.join('; ') : null
  }
  if (typeof value === 'object') {
    const o = value as { message?: unknown; detail?: unknown; msg?: unknown; error?: unknown }
    const inner = o.message ?? o.detail ?? o.msg ?? o.error
    return toMessage(inner)
  }
  return String(value)
}

async function extractErrorMessage(err: unknown): Promise<string> {
  const data: unknown = (err as { response?: { data?: unknown } } | undefined)?.response?.data
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text()) as { detail?: unknown; message?: unknown }
      return toMessage(parsed.detail) ?? toMessage(parsed.message) ?? (err as Error).message
    } catch {
      // Non-JSON error body (e.g. a proxy error page) — fall back to axios message.
    }
  }
  if (typeof data === 'string' && data.trim()) {
    return data.trim()
  }
  const obj = data as { detail?: unknown; message?: unknown } | undefined
  return toMessage(obj?.detail) ?? toMessage(obj?.message) ?? (err as Error).message ?? 'Request failed'
}

api.interceptors.response.use(
  (r) => r,
  async (err) => Promise.reject(new Error(await extractErrorMessage(err))),
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

// Institutes (constituent colleges within the university)
export const instituteApi = {
  getAll: () => api.get<Institute[]>('/institutes').then((r) => r.data),
  getById: (id: number) => api.get<Institute>(`/institutes/${id}`).then((r) => r.data),
  create: (data: InstituteRequest) => api.post<Institute>('/institutes', data).then((r) => r.data),
  update: (id: number, data: InstituteRequest) =>
    api.put<Institute>(`/institutes/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/institutes/${id}`),
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
  createMany: (data: { batchId: number; prefix: string; count: number; size: number }) =>
    api.post<ClassSection[]>('/class-sections/bulk', data).then((r) => r.data),
  update: (id: number, data: ClassSectionRequest) =>
    api.put<ClassSection>(`/class-sections/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/class-sections/${id}`),
}

// Teacher Assignments (term teaching allotment)
export const teacherAssignmentApi = {
  getAll: () => api.get<TeacherAssignment[]>('/teacher-assignments').then((r) => r.data),
  getById: (id: number) =>
    api.get<TeacherAssignment>(`/teacher-assignments/${id}`).then((r) => r.data),
  getByTeacher: (teacherId: number) =>
    api.get<TeacherAssignment[]>(`/teacher-assignments/teacher/${teacherId}`).then((r) => r.data),
  getByBatch: (batchId: number) =>
    api.get<TeacherAssignment[]>(`/teacher-assignments/batch/${batchId}`).then((r) => r.data),
  getBySubject: (subjectId: number) =>
    api.get<TeacherAssignment[]>(`/teacher-assignments/subject/${subjectId}`).then((r) => r.data),
  create: (data: TeacherAssignmentRequest) =>
    api.post<TeacherAssignment>('/teacher-assignments', data).then((r) => r.data),
  update: (id: number, data: TeacherAssignmentRequest) =>
    api.put<TeacherAssignment>(`/teacher-assignments/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/teacher-assignments/${id}`),
}

// Subject offerings (what is taught to which batch/section this term)
export const subjectOfferingApi = {
  getAll: () => api.get<SubjectOffering[]>('/subject-offerings').then((r) => r.data),
  getById: (id: number) =>
    api.get<SubjectOffering>(`/subject-offerings/${id}`).then((r) => r.data),
  getByBatch: (batchId: number) =>
    api.get<SubjectOffering[]>(`/subject-offerings/batch/${batchId}`).then((r) => r.data),
  getBySection: (sectionId: number) =>
    api.get<SubjectOffering[]>(`/subject-offerings/section/${sectionId}`).then((r) => r.data),
  getBySubject: (subjectId: number) =>
    api.get<SubjectOffering[]>(`/subject-offerings/subject/${subjectId}`).then((r) => r.data),
  create: (data: SubjectOfferingRequest) =>
    api.post<SubjectOffering>('/subject-offerings', data).then((r) => r.data),
  update: (id: number, data: SubjectOfferingRequest) =>
    api.put<SubjectOffering>(`/subject-offerings/${id}`, data).then((r) => r.data),
  delete: (id: number) => api.delete(`/subject-offerings/${id}`),
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
  diagnostics: () => api.get('/university-config/diagnostics').then((r) => r.data as {
    valid: boolean
    summary: string
    daysPerWeek: number | null
    timeslotsPerDay: number | null
    maxClassesPerDay: number | null
    workingDays: string[]
    classSlotsPerDay: Record<string, number>
    issues: string[]
  }),
}

// Schedules
export const scheduleApi = {
  getAll: () => api.get<Schedule[]>('/schedules').then((r) => r.data),
  getById: (id: number) => api.get<Schedule>(`/schedules/${id}`).then((r) => r.data),
  generate: (data: ScheduleRequest) =>
    api.post<SolveJobResponse>('/schedules/generate', data).then((r) => r.data),
  partialResolve: (id: number, impactedSessionIds: number[]) =>
    api.post<SolveJobResponse>(`/schedules/${id}/partial-resolve`, { impactedSessionIds }).then((r) => r.data),
  getScoreExplanation: (id: number) =>
    api.get<ScoreExplanation>(`/schedules/${id}/score-explanation`).then((r) => r.data),
  getExplanation: (id: number) =>
    api.get<string>(`/schedules/${id}/explanation`).then((r) => r.data),
  getSessions: (id: number) =>
    api.get<ClassSession[]>(`/sessions/schedule/${id}`).then((r) => r.data),
  activate: (id: number) => api.post<Schedule>(`/schedules/${id}/activate`).then((r) => r.data),
  archive: (id: number) => api.post<Schedule>(`/schedules/${id}/archive`).then((r) => r.data),
  delete: (id: number) => api.delete(`/schedules/${id}`),
  previewDisruption: (id: number, data: DisruptionRequest) =>
    api.post<DisruptionResponse>(`/schedules/${id}/disruption/preview`, data).then((r) => r.data),
  applyDisruption: (id: number, data: DisruptionRequest) =>
    api.post<SolveJobResponse>(`/schedules/${id}/disruption/apply`, data).then((r) => r.data),
  exportCsv: (id: number) =>
    api.get(`/schedules/${id}/export/csv`, { responseType: 'blob' }).then((r) => r.data as Blob),
  exportPdf: (id: number, view: 'ALL' | 'TEACHER' | 'BATCH' | 'ROOM', entityId?: number) =>
    api.get(`/schedules/${id}/export/pdf`, {
      responseType: 'blob',
      params: { view, ...(entityId ? { entityId } : {}) },
    }).then((r) => r.data as Blob),
  exportExcel: (id: number, view: 'ALL' | 'TEACHER' | 'BATCH' | 'ROOM', entityId?: number) =>
    api.get(`/schedules/${id}/export/excel`, {
      responseType: 'blob',
      params: { view, ...(entityId ? { entityId } : {}) },
    }).then((r) => r.data as Blob),
  checkFeasibility: (req: Partial<ScheduleRequest>) =>
    api.post<FeasibilityCheckResult>('/schedules/feasibility-check', req).then((r) => r.data),
  exportRowsExcel: (sheetName: string, headers: string[], rows: (string | number | null | undefined)[][]) =>
    api.post('/export/excel', { sheetName, headers, rows }, { responseType: 'blob' }).then((r) => r.data as Blob),
  getConflictSuggestions: (scheduleId: number, sessionId: number, limit = 4) =>
    api
      .get<ConflictSuggestion[]>(`/schedules/${scheduleId}/sessions/${sessionId}/suggestions`, {
        params: { limit },
      })
      .then((r) => r.data),
}

// Solve Jobs (async schedule generation progress)
export const solveJobApi = {
  getAll: (status?: string) =>
    api.get<SolveJobResponse[]>('/solve-jobs', { params: status ? { status } : undefined }).then((r) => r.data),
  getById: (id: number) => api.get<SolveJobResponse>(`/solve-jobs/${id}`).then((r) => r.data),
  listForSchedule: (scheduleId: number) =>
    api.get<SolveJobResponse[]>(`/solve-jobs/schedule/${scheduleId}`).then((r) => r.data),
  cancel: (id: number) => api.post(`/solve-jobs/${id}/cancel`),
}

// Sessions (manual editing of timetable)
export const sessionApi = {
  updateAssignment: (id: number, data: SessionAssignmentRequest) =>
    api.patch<ClassSession>(`/sessions/${id}`, data).then((r) => r.data),
  create: (data: SessionCreateRequest) =>
    api.post<ClassSession>('/sessions', data).then((r) => r.data),
  delete: (id: number) => api.delete(`/sessions/${id}`),
  getBySchedule: (scheduleId: number) =>
    api.get<ClassSession[]>(`/sessions/schedule/${scheduleId}`).then((r) => r.data),
}

// Pre-Allocations (pre-assigned teachers/rooms for a schedule)
export const preAllocationApi = {
  create: (data: PreAllocationRequest) =>
    api.post<PreAllocation>('/pre-allocations', data).then((r) => r.data),
  getById: (id: number) => api.get<PreAllocation>(`/pre-allocations/${id}`).then((r) => r.data),
  getBySchedule: (scheduleId: number) =>
    api.get<PreAllocation[]>(`/pre-allocations/schedule/${scheduleId}`).then((r) => r.data),
  delete: (id: number) => api.delete(`/pre-allocations/${id}`),
}

// Events
export const eventApi = {
  getAll: () => api.get<Event[]>('/events').then((r) => r.data),
  getById: (id: number) => api.get<Event>(`/events/${id}`).then((r) => r.data),
  create: (data: EventRequest) => api.post<Event>('/events', data).then((r) => r.data),
  update: (id: number, data: EventRequest) =>
    api.put<Event>(`/events/${id}`, data).then((r) => r.data),
  applyToSchedule: (id: number, scheduleId: number) =>
    api.post<SolveJobResponse>(`/events/${id}/apply/${scheduleId}`).then((r) => r.data),
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

export const importApi = {
  importZip: (file: File, dryRun = false) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<CsvZipImportResponse>('/import/zip', formData, {
      params: dryRun ? { dryRun: true } : undefined,
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((r) => r.data)
  },
  exportZip: () =>
    api.get('/import/export/zip', { responseType: 'blob' }).then((r) => r.data as Blob),
  importOrder: () => api.get<ImportOrderStep[]>('/import/order').then((r) => r.data),
  importCsv: (entityType: string, csvContent: string, dryRun = false) =>
    api.post<CsvImportResponse>(`/import/csv/${entityType}`, { csvContent, dryRun }).then((r) => r.data),
  exportCsv: (entityType: string) =>
    api.get(`/import/export/csv/${entityType}`, { responseType: 'blob' }).then((r) => r.data as Blob),
  exportTemplateCsv: (entityType: string) =>
    api.get(`/import/template/csv/${entityType}`, { responseType: 'blob' }).then((r) => r.data as Blob),
  exportTemplateZip: () =>
    api.get('/import/template/zip', { responseType: 'blob' }).then((r) => r.data as Blob),
}

