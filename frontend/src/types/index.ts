// ─── Enums ───────────────────────────────────────────────────────────────────

export type SchoolDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY'
export type RoomType = 'LECTURE' | 'LAB'
export type LabSubtype =
  | 'COMPUTER_LAB'
  | 'ELECTRONICS_LAB'
  | 'CHEMISTRY_LAB'
  | 'PHYSICS_LAB'
  | 'MECHANICAL_LAB'
  | 'CIVIL_LAB'
  | 'NETWORK_LAB'
  | 'GENERAL_LAB'
export type TimeslotType = 'CLASS' | 'BREAK' | 'BLOCKED'
export type ScheduleScope = 'DEPARTMENT' | 'COLLEGE' | 'UNIVERSITY'
export type ScheduleStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED' | 'PARTIAL' | 'INFEASIBLE'
export type EventType =
  | 'EXAM'
  | 'MAINTENANCE'
  | 'FESTIVAL'
  | 'TEACHER_LEAVE'
  | 'GUEST_LECTURE'
  | 'SPORTS_DAY'
  | 'SEMINAR'
  | 'HOLIDAY'
  | 'OTHER'

// ─── Building ────────────────────────────────────────────────────────────────

export interface Building {
  id: number
  name: string
  location?: string
  createdAt?: string
}
export interface BuildingRequest {
  name: string
  location?: string
}

// ─── Department ──────────────────────────────────────────────────────────────

export interface Department {
  id: number
  name: string
  code: string
  buildingsAllowed?: Building[]
  createdAt?: string
}
export interface DepartmentRequest {
  name: string
  code: string
  buildingIds?: number[]
}

// ─── Room ────────────────────────────────────────────────────────────────────

export interface Room {
  id: number
  buildingId: number
  buildingName?: string
  roomNumber: string
  type: RoomType
  labSubtype?: LabSubtype
  capacity: number
  availableTimeslotIds: number[]
}
export interface RoomRequest {
  buildingId: number
  roomNumber: string
  type: RoomType
  labSubtype?: LabSubtype
  capacity: number
  availableTimeslotIds?: number[]
}

// ─── Teacher ─────────────────────────────────────────────────────────────────

export interface Teacher {
  id: number
  name: string
  subjectIds: number[]
  subjectNames?: string[]
  availableTimeslotIds: number[]
  preferredBuildingIds: number[]
  maxDailyHours: number
  maxWeeklyHours: number
  maxConsecutiveClasses: number
  movementPenalty: number
  preferredFreeDay?: SchoolDay
}
export interface TeacherRequest {
  name: string
  subjectIds?: number[]
  availableTimeslotIds?: number[]
  preferredBuildingIds?: number[]
  maxDailyHours: number
  maxWeeklyHours: number
  maxConsecutiveClasses: number
  movementPenalty?: number
  preferredFreeDay?: SchoolDay
}

// ─── Subject ─────────────────────────────────────────────────────────────────

export interface Subject {
  id: number
  name: string
  code: string
  departmentId: number
  departmentName?: string
  weeklyHours: number
  chunkHours: number
  roomTypeRequired: RoomType
  labSubtypeRequired?: LabSubtype
  isLab: boolean
  requiresTeacher: boolean
  requiresRoom: boolean
  minGapBetweenSessions: number
  maxSessionsPerDay: number
  createdAt?: string
}
export interface SubjectRequest {
  name: string
  code: string
  departmentId: number
  weeklyHours: number
  chunkHours: number
  roomTypeRequired?: RoomType
  labSubtypeRequired?: LabSubtype
  isLab: boolean
  requiresTeacher: boolean
  requiresRoom: boolean
  minGapBetweenSessions?: number
  maxSessionsPerDay: number
}

// ─── Batch ───────────────────────────────────────────────────────────────────

export interface Batch {
  id: number
  departmentId: number
  departmentName?: string
  year: number
  section: string
  studentCount: number
  workingDays?: SchoolDay[]
  preferredFreeDay?: SchoolDay
  createdAt?: string
}
export interface BatchRequest {
  departmentId: number
  year: number
  section: string
  studentCount: number
  workingDays?: SchoolDay[]
  preferredFreeDay?: SchoolDay
}

// ─── ClassSection ────────────────────────────────────────────────────────────

export interface ClassSection {
  id: number
  batchId: number
  batchName?: string
  label: string
  size: number
}
export interface ClassSectionRequest {
  batchId: number
  label: string
  size: number
}

// ─── Timeslot ────────────────────────────────────────────────────────────────

export interface Timeslot {
  id: number
  day: SchoolDay
  startTime: string
  endTime: string
  type: TimeslotType
  createdAt?: string
}
export interface TimeslotRequest {
  day: SchoolDay
  startTime: string
  endTime: string
  type: TimeslotType
}

// ─── University Config ───────────────────────────────────────────────────────

export interface UniversityConfig {
  id?: number
  active: boolean
  daysPerWeek: number
  timeslotsPerDay: number
  maxClassesPerDay: number
  breakSlotIndices: number[]
  workingDays: SchoolDay[]
}
export interface UniversityConfigRequest {
  active?: boolean
  daysPerWeek: number
  timeslotsPerDay: number
  maxClassesPerDay: number
  breakSlotIndices?: number[]
  workingDays?: SchoolDay[]
}

// ─── Event ───────────────────────────────────────────────────────────────────

export interface Event {
  id: number
  title: string
  type: EventType
  startDate: string
  endDate: string
  affectedTeacherIds: number[]
  affectedRoomIds: number[]
  affectedTimeslotIds: number[]
  description?: string
  createdAt?: string
}
export interface EventRequest {
  title: string
  type: EventType
  startDate: string
  endDate: string
  affectedTeacherIds?: number[]
  affectedRoomIds?: number[]
  affectedTimeslotIds?: number[]
  description?: string
}

// ─── Schedule ────────────────────────────────────────────────────────────────

export interface Schedule {
  id: number
  name: string
  scope: ScheduleScope
  status: ScheduleStatus
  parentScheduleId?: number
  score?: string
  scoreExplanation?: string
  createdAt?: string
}
export interface ScheduleRequest {
  name: string
  scope: ScheduleScope
  parentScheduleId?: number
  departmentId?: number
  batchIds?: number[]
  teacherIds?: number[]
  roomIds?: number[]
  solvingTimeSeconds?: number
}

// ─── ClassSession ─────────────────────────────────────────────────────────────

export interface ClassSession {
  id: number
  subjectId?: number
  subjectName?: string
  isLab: boolean
  batchId?: number
  sectionId?: number
  batchLabel?: string
  teacherId?: number
  teacherName?: string
  roomId?: number
  roomNumber?: string
  buildingName?: string
  timeslotId?: number
  day?: SchoolDay
  startTime?: string
  endTime?: string
  duration: number
  isLocked: boolean
  scheduleId?: number
}

// ─── Score Explanation ────────────────────────────────────────────────────────

export interface ConstraintBreakdown {
  constraintName: string
  level: 'HARD' | 'MEDIUM' | 'SOFT'
  matchCount: number
  scoreImpact: string
}

export interface ScoreExplanation {
  score: string
  feasible: boolean
  hardScore: number
  mediumScore: number
  softScore: number
  constraints: ConstraintBreakdown[]
}

// ─── University Config Entry (key-value) ─────────────────────────────────────

export interface UniversityConfigEntry {
  id: number
  key: string
  value: string
  description?: string
}
export interface UniversityConfigEntryRequest {
  key: string
  value: string
  description?: string
}

// ─── Session Assignment (manual edit) ────────────────────────────────────────

export interface SessionAssignmentRequest {
  teacherId?: number | null
  roomId?: number | null
  timeslotId?: number | null
  locked?: boolean
}

// ─── AcademicTerm ─────────────────────────────────────────────────────────────

export type AcademicTermStatus = 'UPCOMING' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED'

export interface AcademicTerm {
  id: number
  name: string
  academicYear?: string
  startDate: string
  endDate: string
  examPeriodStart?: string
  examPeriodEnd?: string
  status: AcademicTermStatus
  description?: string
  createdAt?: string
}

export interface AcademicTermRequest {
  name: string
  academicYear?: string
  startDate: string
  endDate: string
  examPeriodStart?: string
  examPeriodEnd?: string
  status?: AcademicTermStatus
  description?: string
}

// ─── Disruption / Impact Analyzer ─────────────────────────────────────────────

export type DisruptionType =
  | 'TEACHER_UNAVAILABLE'
  | 'ROOM_UNAVAILABLE'
  | 'TIMESLOT_BLOCKED'
  | 'SESSION_CANCELLED'
  | 'SPECIAL_EVENT'

export interface DisruptionRequest {
  type: DisruptionType
  affectedEntityId: number
  date?: string              // ISO date e.g. "2026-03-15"
  description?: string
}

export interface ImpactedSession {
  id: number
  subjectName?: string
  batchLabel?: string
  teacherName?: string
  roomNumber?: string
  day?: string
  startTime?: string
  endTime?: string
  locked: boolean
}

export interface DisruptionResponse {
  type: DisruptionType
  affectedEntityId: number
  affectedEntityName: string
  disruption: string
  impactedSessionCount: number
  impactedSessions: ImpactedSession[]
  impactedSessionIds: number[]
}

// Feasibility Check (pre-solve Constraint Propagation)
export type FeasibilitySeverity = 'ERROR' | 'WARNING'

export interface FeasibilityIssue {
  severity: FeasibilitySeverity
  category: string
  message: string
  entityId?: number
  entityName?: string
}

export interface FeasibilityCheckResult {
  feasible: boolean
  errorCount: number
  warningCount: number
  totalSessionsEstimate: number
  availableTimeslots: number
  issues: FeasibilityIssue[]
}
