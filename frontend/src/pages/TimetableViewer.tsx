import { useState, useEffect, useMemo, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { AlertCircle, RefreshCw, Lock, Unlock, Zap, Download, Flame, Target, Bookmark, Plus, Pin, CheckCircle2, Archive, FileText, Sheet, Pencil, Trash2, Copy, X, BarChart3, AlertTriangle, Filter, Rows3, Columns3 } from 'lucide-react'
import { Card, Button, Select, Badge, Modal, ContextMenu, Input, ConfirmDialog, SearchableSelect, MultiSelect, Toggle } from '../components/ui'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import TimetableGrid from '../components/timetable/TimetableGrid'
import ScoreBreakdownPanel from '../components/timetable/ScoreBreakdownPanel'
import ConflictSolverSidecar, { buildConflictSuggestions } from '../components/timetable/ConflictSolverSidecar'
import { scheduleApi, timeslotApi, batchApi, teacherApi, roomApi, sessionApi, subjectApi, classSectionApi, preAllocationApi, type ExportView } from '../services/api'
import { waitForJob } from '../hooks/useSolveJob'
import type {
  Schedule,
  ClassSession,
  Timeslot,
  Batch,
  Teacher,
  Room,
  Subject,
  ClassSection,
  DisruptionRequest,
  DisruptionResponse,
  DisruptionType,
  ScoreExplanation,
  ConflictSuggestion,
  SessionCreateRequest,
  PreAllocation,
} from '../types'
import { useToast } from '../contexts/ToastContext'

type ViewMode = 'all' | 'batch' | 'teacher' | 'room'

type HeatEntry = {
  hard: number
  soft: number
  notes: string[]
}

const STATUS_VARIANT: Record<string, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  ACTIVE: 'green',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
  DRAFT: 'gray',
  ARCHIVED: 'blue',
}

function computeHeatMap(sessions: ClassSession[]): Record<number, HeatEntry> {
  const heat: Record<number, HeatEntry> = {}
  const ensure = (id: number) => {
    if (!heat[id]) heat[id] = { hard: 0, soft: 0, notes: [] }
    return heat[id]
  }
  const addHard = (sessionId: number, note: string) => {
    const entry = ensure(sessionId)
    entry.hard += 1
    if (!entry.notes.includes(note)) entry.notes.push(note)
  }
  const addSoft = (sessionId: number, note: string) => {
    const entry = ensure(sessionId)
    entry.soft += 1
    if (!entry.notes.includes(note)) entry.notes.push(note)
  }

  const bySlotTeacher = new Map<string, ClassSession[]>()
  const bySlotRoom = new Map<string, ClassSession[]>()
  const bySlotBatch = new Map<string, ClassSession[]>()

  for (const s of sessions) {
    if (!s.timeslotId || !s.day) continue
    if (s.teacherId) {
      const key = `${s.day}:${s.timeslotId}:teacher:${s.teacherId}`
      bySlotTeacher.set(key, [...(bySlotTeacher.get(key) ?? []), s])
    }
    if (s.roomId) {
      const key = `${s.day}:${s.timeslotId}:room:${s.roomId}`
      bySlotRoom.set(key, [...(bySlotRoom.get(key) ?? []), s])
    }
    if (s.batchId) {
      const key = `${s.day}:${s.timeslotId}:batch:${s.batchId}`
      bySlotBatch.set(key, [...(bySlotBatch.get(key) ?? []), s])
    }
  }

  for (const arr of [...bySlotTeacher.values(), ...bySlotRoom.values(), ...bySlotBatch.values()]) {
    if (arr.length < 2) continue
    for (const s of arr) {
      const note = s.teacherName
        ? `Hard conflict: ${s.teacherName} overlaps`
        : s.roomNumber
          ? `Hard conflict: room ${s.roomNumber} overlaps`
          : 'Hard conflict: batch overlap'
      addHard(s.id, note)
    }
  }

  const byBatchSubjectDay = new Map<string, ClassSession[]>()
  for (const s of sessions) {
    if (!s.batchId || !s.subjectId || !s.day) continue
    const key = `${s.batchId}:${s.subjectId}:${s.day}`
    byBatchSubjectDay.set(key, [...(byBatchSubjectDay.get(key) ?? []), s])
  }
  for (const arr of byBatchSubjectDay.values()) {
    if (arr.length <= 1) continue
    for (const s of arr) {
      addSoft(s.id, `Soft penalty: repeated ${s.subjectName ?? 'subject'} on ${s.day}`)
    }
  }

  return heat
}

interface SavedView {
  name: string
  mode: ViewMode
  batchIds?: number[]
  teacherIds?: number[]
  roomIds?: number[]
}

type SlotContextMenuState = {
  x: number
  y: number
  slot: Timeslot
}

type SessionContextMenuState = {
  x: number
  y: number
  session: ClassSession
}

const savedViewsKey = (scheduleId: number) => `arare.savedViews.${scheduleId}`
const MAX_SAVED_VIEWS = 20

function loadSavedViews(scheduleId: number): SavedView[] {
  try {
    const raw = window.localStorage.getItem(savedViewsKey(scheduleId))
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? (parsed as SavedView[]) : []
  } catch {
    return []
  }
}

/**
 * Human-readable label for an entity in a given view dimension. Used to name
 * per-entity tab bar entries and to suffix downloaded per-entity files.
 */
function entityLabel(
  mode: ViewMode,
  id: number,
  batches: Batch[],
  teachers: Teacher[],
  rooms: Room[],
): string {
  if (mode === 'batch') {
    const b = batches.find((x) => x.id === id)
    return b
      ? b.departmentName
        ? `${b.departmentName} · Yr ${b.year}-${b.section}`
        : `Yr ${b.year}-${b.section}`
      : `Batch #${id}`
  }
  if (mode === 'teacher') {
    const t = teachers.find((x) => x.id === id)
    return t ? t.name : `Teacher #${id}`
  }
  const r = rooms.find((x) => x.id === id)
  return r ? `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''}` : `Room #${id}`
}

/**
 * Filesystem-safe slug derived from an entity label, used when naming a
 * single-entity downloaded file (e.g. "cse-yr-1a" for a batch).
 */
function entitySlug(mode: ViewMode, id: number, batches: Batch[], teachers: Teacher[], rooms: Room[]): string {
  const label = entityLabel(mode, id, batches, teachers, rooms)
  return label.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

export default function TimetableViewer() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const parsedId = Number(id)
  const scheduleId = Number.isFinite(parsedId) && parsedId > 0 ? parsedId : NaN
  const { toast } = useToast()

  const abortRef = useRef<AbortController | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    abortRef.current = controller
    return () => controller.abort()
  }, [])

  const [schedule, setSchedule] = useState<Schedule | null>(null)
  const [sessions, setSessions] = useState<ClassSession[]>([])
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [sections, setSections] = useState<ClassSection[]>([])
  const [preAllocations, setPreAllocations] = useState<PreAllocation[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [viewMode, setViewMode] = useState<ViewMode>('batch')
  const [focusedEntityId, setFocusedEntityId] = useState<number | null>(null)
  const [activeTabId, setActiveTabId] = useState<number | null>(null)
  const [filterBatchIds, setFilterBatchIds] = useState<number[]>([])
  const [filterTeacherIds, setFilterTeacherIds] = useState<number[]>([])
  const [filterRoomIds, setFilterRoomIds] = useState<number[]>([])
  const [filterDepartmentIds, setFilterDepartmentIds] = useState<number[]>([])
  const [density, setDensity] = useState<'compact' | 'comfortable'>('comfortable')
  const [showConflictsOnly, setShowConflictsOnly] = useState(false)
  const [showUnplacedOnly, setShowUnplacedOnly] = useState(false)
  const [filterBarOpen, setFilterBarOpen] = useState(true)
  const [showScorePanel, setShowScorePanel] = useState(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 1024 : true,
  )
  const [showConflictsPanel, setShowConflictsPanel] = useState(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 1280 : true,
  )
  const [selectedSession, setSelectedSession] = useState<ClassSession | null>(null)
  const [editMode, setEditMode] = useState(false)
  const [editTeacherId, setEditTeacherId] = useState<string>('')
  const [editRoomId, setEditRoomId] = useState<string>('')
  const [editTimeslotId, setEditTimeslotId] = useState<string>('')
  const [editLocked, setEditLocked] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const [publishing, setPublishing] = useState(false)
  const [archiving, setArchiving] = useState(false)

  const [scoreBreakdown, setScoreBreakdown] = useState<ScoreExplanation | null>(null)
  const [rawExplanation, setRawExplanation] = useState<string | null>(null)
  const [heatmapEnabled, setHeatmapEnabled] = useState(false)
  const [highlightedSessionIds, setHighlightedSessionIds] = useState<Set<number>>(new Set())
  const [hoveredSession, setHoveredSession] = useState<ClassSession | null>(null)
  const [conflictSession, setConflictSession] = useState<ClassSession | null>(null)
  const [backendSuggestions, setBackendSuggestions] = useState<ConflictSuggestion[] | null>(null)

  const [slotContextMenu, setSlotContextMenu] = useState<SlotContextMenuState | null>(null)
  const [sessionContextMenu, setSessionContextMenu] = useState<SessionContextMenuState | null>(null)
  const [deleteSession, setDeleteSession] = useState<ClassSession | null>(null)
  const [deleteSaving, setDeleteSaving] = useState(false)
  const [createSessionOpen, setCreateSessionOpen] = useState(false)
  const [createSlot, setCreateSlot] = useState<Timeslot | null>(null)
  const [createSubjectId, setCreateSubjectId] = useState('')
  const [createBatchId, setCreateBatchId] = useState('')
  const [createSectionId, setCreateSectionId] = useState('')
  const [createTeacherId, setCreateTeacherId] = useState('')
  const [createRoomId, setCreateRoomId] = useState('')
  const [createDuration, setCreateDuration] = useState(1)
  const [createLocked, setCreateLocked] = useState(false)
  const [createSaving, setCreateSaving] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [showDisruptionPanel, setShowDisruptionPanel] = useState(false)
  const [disruptionType, setDisruptionType] = useState<DisruptionType>('TEACHER_UNAVAILABLE')
  const [disruptionEntityId, setDisruptionEntityId] = useState<string>('')
  const [disruptionDate, setDisruptionDate] = useState<string>('')
  const [disruptionDescription, setDisruptionDescription] = useState<string>('')
  const [disruptionPreview, setDisruptionPreview] = useState<DisruptionResponse | null>(null)
  const [disruptionPreviewing, setDisruptionPreviewing] = useState(false)
  const [disruptionApplying, setDisruptionApplying] = useState(false)

  const [savedViews, setSavedViews] = useState<SavedView[]>([])

  const scheduleReadonly = schedule?.status === 'ARCHIVED' || schedule?.status === 'INFEASIBLE'

  useEffect(() => {
    setSavedViews(loadSavedViews(scheduleId))
  }, [scheduleId])

  const heatBySessionId = useMemo(() => computeHeatMap(sessions), [sessions])

  const preAllocatedSessionIds = useMemo(() => {
    if (preAllocations.length === 0) return new Set<number>()
    const keys = new Set(preAllocations.map((p) => `${p.batchId}:${p.subjectId}`))
    return new Set(
      sessions
        .filter((s) => s.batchId != null && s.subjectId != null && keys.has(`${s.batchId}:${s.subjectId}`))
        .map((s) => s.id),
    )
  }, [preAllocations, sessions])

  const unassignedCount = useMemo(
    () => sessions.filter((s) => !s.timeslotId).length,
    [sessions],
  )
  // Sessions TimetableGrid could not place into a cell (e.g. assigned a
  // timeslotId that is no longer present in the loaded timeslot set). Surfaced
  // alongside the unassigned count so the operator never loses visibility of
  // data the grid can't render.
  const [orphanCount, setOrphanCount] = useState(0)
  const totalUnassigned = unassignedCount + orphanCount
  const lockedCount = useMemo(
    () => sessions.filter((s) => s.isLocked).length,
    [sessions],
  )
  const heatSummary = useMemo(() => {
    let hard = 0
    let soft = 0
    for (const entry of Object.values(heatBySessionId)) {
      if (entry.hard > 0) hard += 1
      else if (entry.soft > 0) soft += 1
    }
    return { hard, soft }
  }, [heatBySessionId])

  const publishBlockReason = useMemo(() => {
    const placed = sessions.filter((s) => s.timeslotId != null).length
    if (placed === 0) return 'Publish requires at least one placed session — generate or place sessions first.'
    if (heatSummary.hard > 0) {
      return `Cannot publish: ${heatSummary.hard} session(s) still have hard conflicts. Resolve conflicts or re-run the solver before publishing.`
    }
    return null
  }, [sessions, heatSummary.hard])

  const canPublish = publishBlockReason === null

  const fallbackSuggestions = useMemo(() => {
    if (!conflictSession) return []
    return buildConflictSuggestions(conflictSession, timeslots, sessions)
  }, [conflictSession, timeslots, sessions])

  useEffect(() => {
    if (!conflictSession) {
      setBackendSuggestions(null)
      return
    }
    scheduleApi
      .getConflictSuggestions(scheduleId, conflictSession.id, 4)
      .then(setBackendSuggestions)
      .catch(() => setBackendSuggestions(null))
  }, [conflictSession, scheduleId])

  // Selecting a conflicting session makes the conflict solver side panel
  // discoverable by auto-opening it (the operator can still collapse it).
  useEffect(() => {
    if (conflictSession) setShowConflictsPanel(true)
  }, [conflictSession])

  const loadExplanations = () => {
    Promise.all([
      scheduleApi.getScoreExplanation(scheduleId),
      scheduleApi.getExplanation(scheduleId),
    ])
      .then(([structured, raw]) => {
        setScoreBreakdown(structured)
        setRawExplanation(raw)
      })
      .catch(() => {
      })
  }

  const load = () => {
    if (!Number.isFinite(scheduleId)) {
      setSchedule(null)
      setSessions([])
      setLoading(false)
      return
    }
    setLoading(true)
    setLoadFailed(false)
    Promise.allSettled([
      scheduleApi.getById(scheduleId),
      scheduleApi.getSessions(scheduleId),
      timeslotApi.getAll(),
      batchApi.getAll(),
      teacherApi.getAll(),
      roomApi.getAll(),
      subjectApi.getAll(),
      classSectionApi.getAll(),
      preAllocationApi.getBySchedule(scheduleId),
    ])
      .then(([sched, sess, ts, b, t, r, subj, sec, pre]) => {
        // The schedule itself is mandatory; the rest degrade gracefully so a
        // single unrelated failure (e.g. a slow reference-data call) never
        // blanks the entire timetable.
        if (sched.status === 'rejected') {
          setLoadFailed(true)
          toast.error(sched.reason instanceof Error ? sched.reason.message : 'Failed to load schedule')
          return
        }
        setSchedule(sched.value)
        setSessions(sess.status === 'fulfilled' ? sess.value : [])
        setTimeslots(ts.status === 'fulfilled' ? ts.value : [])
        setBatches(b.status === 'fulfilled' ? b.value : [])
        setTeachers(t.status === 'fulfilled' ? t.value : [])
        setRooms(r.status === 'fulfilled' ? r.value : [])
        setSubjects(subj.status === 'fulfilled' ? subj.value : [])
        setSections(sec.status === 'fulfilled' ? sec.value : [])
        setPreAllocations(pre.status === 'fulfilled' ? pre.value : [])
        const failed = [sess, ts, b, t, r, subj, sec, pre].filter((x) => x.status === 'rejected').length
        if (failed > 0) {
          toast.warning(`Some schedule data failed to refresh (${failed}/8)`)
        }
        // Do NOT auto-pick a filter here: the grid must show every session by
        // default so dropped sessions never appear to vanish. The operator
        // chooses a batch/teacher/room filter explicitly.
      })
      .finally(() => setLoading(false))

    loadExplanations()
  }

  useEffect(() => { load() }, [scheduleId])

  const exportView = (): ExportView =>
    viewMode === 'teacher' ? 'TEACHER' : viewMode === 'room' ? 'ROOM' : viewMode === 'batch' ? 'BATCH' : 'ALL'

  const downloadBlob = (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const handleExportCsv = async () => {
    try {
      if (viewMode === 'all') {
        const blob = await scheduleApi.exportCsv(scheduleId, 'ALL')
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}.csv`)
      } else if (focusedEntityId != null) {
        const blob = await scheduleApi.exportCsv(scheduleId, exportView(), focusedEntityId)
        const slug = entitySlug(viewMode, focusedEntityId, batches, teachers, rooms)
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}-${slug}.csv`)
      } else {
        const blob = await scheduleApi.exportCsv(scheduleId, exportView())
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}-by-${viewMode}.zip`)
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Export failed')
    }
  }

  const handleExportPdf = async () => {
    try {
      const view = exportView()
      if (view !== 'ALL' && focusedEntityId == null) {
        const blob = await scheduleApi.exportPdf(scheduleId, view)
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}-by-${viewMode}.pdf`)
      } else {
        const entityId = focusedEntityId ?? undefined
        const blob = await scheduleApi.exportPdf(scheduleId, view, entityId)
        const suffix = entityId ? `-${entitySlug(viewMode ?? 'all', entityId, batches, teachers, rooms)}` : ''
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}${suffix}.pdf`)
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'PDF export failed')
    }
  }

  const handleExportExcel = async () => {
    try {
      const view = exportView()
      if (view !== 'ALL' && focusedEntityId == null) {
        const blob = await scheduleApi.exportExcel(scheduleId, view)
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}-by-${viewMode}.xlsx`)
      } else {
        const entityId = focusedEntityId ?? undefined
        const blob = await scheduleApi.exportExcel(scheduleId, view, entityId)
        const suffix = entityId ? `-${entitySlug(viewMode ?? 'all', entityId, batches, teachers, rooms)}` : ''
        downloadBlob(blob, `timetable-${schedule?.name ?? scheduleId}${suffix}.xlsx`)
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Excel export failed')
    }
  }

  const handlePublish = async () => {
    if (!schedule || publishing) return
    if (!canPublish) {
      toast.error(publishBlockReason ?? 'This schedule cannot be published yet')
      return
    }
    setPublishing(true)
    try {
      const updated = await scheduleApi.activate(schedule.id)
      setSchedule(updated)
      toast.success(`"${schedule.name}" is now active`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to activate schedule')
    } finally {
      setPublishing(false)
    }
  }

  const handleArchive = async () => {
    if (!schedule || archiving) return
    setArchiving(true)
    try {
      const updated = await scheduleApi.archive(schedule.id)
      setSchedule(updated)
      toast.success(`"${schedule.name}" archived`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to archive schedule')
    } finally {
      setArchiving(false)
    }
  }

  const buildDisruptionRequest = (): DisruptionRequest => ({
    type: disruptionType,
    affectedEntityId:
      disruptionType === 'SPECIAL_EVENT'
        ? null
        : (disruptionEntityId ? +disruptionEntityId : undefined),
    date: disruptionDate || undefined,
    description: disruptionDescription || undefined,
  })

  const handlePreviewDisruption = async () => {
    if (!disruptionEntityId && disruptionType !== 'SPECIAL_EVENT') {
      toast.error('Select an entity to disrupt')
      return
    }
    setDisruptionPreviewing(true)
    setDisruptionPreview(null)
    try {
      const result = await scheduleApi.previewDisruption(scheduleId, buildDisruptionRequest())
      setDisruptionPreview(result)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Preview failed')
    } finally {
      setDisruptionPreviewing(false)
    }
  }

  const handleApplyDisruption = async () => {
    setDisruptionApplying(true)
    try {
      const job = await scheduleApi.applyDisruption(scheduleId, buildDisruptionRequest())
      const finished = await waitForJob(job, abortRef.current?.signal)
      if (finished.status === 'FAILED') {
        toast.error(finished.errorMessage || 'Re-solving after disruption failed')
        return
      }
      if (finished.status === 'CANCELLED') {
        toast.error('Re-solving after disruption was cancelled')
        return
      }
      const updated = await scheduleApi.getById(scheduleId)
      if (updated.status === 'INFEASIBLE') {
        toast.warning(
          `Disruption applied, but the schedule is now infeasible (${updated.score ?? 'no score'}) — partial result kept`,
        )
      } else {
        toast.success(`Disruption applied - re-solved ${disruptionPreview?.impactedSessionCount ?? '?'} sessions`)
      }
      setShowDisruptionPanel(false)
      setDisruptionPreview(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Apply failed')
    } finally {
      setDisruptionApplying(false)
    }
  }

  const openSessionDetail = (s: ClassSession) => {
    if ((heatBySessionId[s.id]?.hard ?? 0) > 0) {
      setConflictSession(s)
    } else {
      setConflictSession(null)
    }
    setSelectedSession(s)
    setEditMode(false)
    setEditTeacherId(s.teacherId?.toString() ?? '')
    setEditRoomId(s.roomId?.toString() ?? '')
    setEditTimeslotId(s.timeslotId?.toString() ?? '')
    setEditLocked(s.isLocked)
    setEditError(null)
  }

  const handleSaveAssignment = async () => {
    if (!selectedSession) return
    if (scheduleReadonly) {
      setEditError('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    if (editLocked && !selectedSession.isLocked) {
      // Session is being created locked: nothing to do besides locking.
      setEditError(null)
    } else if (editLocked) {
      const changed =
        (editTeacherId && +editTeacherId !== selectedSession.teacherId) ||
        (!editTeacherId && selectedSession.teacherId != null) ||
        (editRoomId && +editRoomId !== selectedSession.roomId) ||
        (!editRoomId && selectedSession.roomId != null) ||
        (editTimeslotId && +editTimeslotId !== selectedSession.timeslotId) ||
        (!editTimeslotId && selectedSession.timeslotId != null)
      if (changed) {
        setEditError('Session is locked — uncheck "Locked" to edit its assignment')
        return
      }
    }
    setEditSaving(true)
    setEditError(null)
    try {
      await sessionApi.updateAssignment(selectedSession.id as number, {
        teacherId: editTeacherId ? +editTeacherId : null,
        roomId: editRoomId ? +editRoomId : null,
        timeslotId: editTimeslotId ? +editTimeslotId : null,
        clearTeacher: !editTeacherId,
        clearRoom: !editRoomId,
        clearTimeslot: !editTimeslotId,
        locked: editLocked,
      })
      setSelectedSession(null)
      setEditMode(false)
      toast.success('Session assignment updated')
      // Re-fetch the schedule (so the persisted score header is not stale
      // after the manual assignment), the sessions, and the score explanation.
      Promise.all([scheduleApi.getById(scheduleId), scheduleApi.getSessions(scheduleId)])
        .then(([sched, sess]) => {
          setSchedule(sched)
          setSessions(sess)
        })
        .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to reload schedule'))
      loadExplanations()
    } catch (e) {
      setEditError(e instanceof Error ? e.message : 'Failed to save assignment')
    } finally {
      setEditSaving(false)
    }
  }

  const handleQuickLockToggle = async () => {
    if (!selectedSession) return
    if (scheduleReadonly) {
      toast.error('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    try {
      await sessionApi.updateAssignment(selectedSession.id, { locked: !selectedSession.isLocked })
      toast.success(selectedSession.isLocked ? 'Session unlocked' : 'Session locked')
      setSelectedSession(null)
      refreshSessionsAndSchedule()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to update lock state')
    }
  }

  const handleDeleteSession = async () => {
    if (!deleteSession) return
    if (scheduleReadonly) {
      toast.error('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      setDeleteSession(null)
      setSessionContextMenu(null)
      return
    }
    setDeleteSaving(true)
    try {
      await sessionApi.delete(deleteSession.id)
      toast.success(`Session "${deleteSession.subjectName ?? `#${deleteSession.id}`}" deleted`)
      setDeleteSession(null)
      setSessionContextMenu(null)
      refreshSessionsAndSchedule()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to delete session')
    } finally {
      setDeleteSaving(false)
    }
  }

  const handleDuplicateSession = async (session: ClassSession) => {
    setSessionContextMenu(null)
    if (scheduleReadonly) {
      toast.error('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    if (!session.timeslotId) {
      toast.error('Cannot duplicate an unassigned session')
      return
    }
    setCreateSlot(timeslots.find((t) => t.id === session.timeslotId) ?? null)
    setCreateSessionOpen(true)
    setCreateSubjectId(String(session.subjectId ?? ''))
    setCreateBatchId(String(session.batchId ?? ''))
    setCreateSectionId(String(session.sectionId ?? ''))
    setCreateTeacherId(String(session.teacherId ?? ''))
    setCreateRoomId(String(session.roomId ?? ''))
    setCreateDuration(session.duration ?? 1)
    setCreateLocked(session.isLocked)
    setCreateError(null)
  }

  const handleSessionContextMenu = (session: ClassSession, x: number, y: number) => {
    setSessionContextMenu({ x, y, session })
  }

  const buildSessionContextItems = (s: ClassSession): ContextMenuItem[] => {
    if (scheduleReadonly) return []
    return [
    { label: 'Edit assignment', icon: <Pencil size={13} />, onClick: () => openSessionDetail(s) },
    { label: s.isLocked ? 'Unlock' : 'Lock', icon: s.isLocked ? <Unlock size={13} /> : <Lock size={13} />, onClick: () => handleQuickLockToggleFrom(s) },
    { label: 'Duplicate session', icon: <Copy size={13} />, onClick: () => handleDuplicateSession(s) },
    { label: 'Remove from slot', icon: <X size={13} />, onClick: () => handleClearTimeslot(s), disabled: !s.timeslotId || s.isLocked },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setDeleteSession(s) },
  ]
  }

  const handleQuickLockToggleFrom = async (s: ClassSession) => {
    setSessionContextMenu(null)
    if (scheduleReadonly) {
      toast.error('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    try {
      await sessionApi.updateAssignment(s.id, { locked: !s.isLocked })
      toast.success(s.isLocked ? 'Session unlocked' : 'Session locked')
      refreshSessionsAndSchedule()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to update lock state')
    }
  }

  const handleClearTimeslot = async (s: ClassSession) => {
    setSessionContextMenu(null)
    if (scheduleReadonly) {
      toast.error('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    try {
      await sessionApi.updateAssignment(s.id, { timeslotId: null, clearTimeslot: true, locked: s.isLocked })
      toast.success(`"${s.subjectName}" removed from its slot`)
      refreshSessionsAndSchedule()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to remove session from slot')
    }
  }

  const openCreateSession = (slot: Timeslot) => {
    setSlotContextMenu(null)
    if (scheduleReadonly) {
      toast.error('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    setCreateSlot(slot)
    setCreateSubjectId(subjects[0]?.id ? String(subjects[0].id) : '')
    setCreateBatchId(viewMode === 'batch' && displayEntityId != null ? String(displayEntityId) : '')
    setCreateSectionId('')
    setCreateTeacherId(viewMode === 'teacher' && displayEntityId != null ? String(displayEntityId) : '')
    setCreateRoomId(viewMode === 'room' && displayEntityId != null ? String(displayEntityId) : '')
    setCreateDuration(1)
    setCreateLocked(false)
    setCreateError(null)
    setCreateSessionOpen(true)
  }

  const handleCreateSession = async () => {
    if (!createSlot) return
    if (scheduleReadonly) {
      setCreateError('This schedule is read-only — archived or infeasible schedules can\'t be edited')
      return
    }
    if (!createSubjectId) {
      setCreateError('Select a subject')
      return
    }
    if (!createBatchId && !createSectionId) {
      setCreateError('Select a batch or a section')
      return
    }
    setCreateSaving(true)
    setCreateError(null)
    try {
      const payload: SessionCreateRequest = {
        scheduleId,
        subjectId: +createSubjectId,
        teacherId: createTeacherId ? +createTeacherId : null,
        roomId: createRoomId ? +createRoomId : null,
        timeslotId: createSlot.id,
        duration: createDuration,
        locked: createLocked,
      }
      if (createSectionId) {
        payload.sectionId = +createSectionId
      } else {
        payload.batchId = +createBatchId
      }
      await sessionApi.create(payload)
      toast.success('Session created')
      setCreateSessionOpen(false)
      setCreateSlot(null)
      refreshSessionsAndSchedule()
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : 'Failed to create session')
    } finally {
      setCreateSaving(false)
    }
  }

  const refreshSessionsAndSchedule = () => {
    Promise.allSettled([scheduleApi.getById(scheduleId), scheduleApi.getSessions(scheduleId)])
      .then(([sched, sess]) => {
        if (sched.status === 'fulfilled') setSchedule(sched.value)
        if (sess.status === 'fulfilled') setSessions(sess.value)
        if (sched.status === 'rejected' || sess.status === 'rejected') {
          toast.error('Failed to reload schedule — showing the last known data')
        }
      })
    loadExplanations()
  }

  const highlightByText = (text: string) => {
    const lowered = text.toLowerCase()
    const tokens = lowered.split(/[^a-z0-9]+/).filter((t) => t.length > 3)

    const matchedIds = sessions
      .filter((s) => {
        const haystack = [
          s.subjectName,
          s.teacherName,
          s.roomNumber,
          s.batchLabel,
          s.buildingName,
          s.day,
        ].join(' ').toLowerCase()

        if (haystack.includes(lowered)) return true
        return tokens.some((token) => haystack.includes(token))
      })
      .map((s) => s.id)

    setHighlightedSessionIds(new Set(matchedIds))
  }

  const viewOptions = [
    { value: 'all', label: 'Full timetable' },
    { value: 'batch', label: 'By Batch' },
    { value: 'teacher', label: 'By Teacher' },
    { value: 'room', label: 'By Room' },
  ]

  // Department selection expands into the set of batch ids in those departments.
  const effectiveBatchIds = useMemo(() => {
    if (filterDepartmentIds.length === 0) return filterBatchIds
    const fromDept = batches
      .filter((b) => b.departmentId != null && filterDepartmentIds.includes(b.departmentId))
      .map((b) => b.id)
    return Array.from(new Set([...filterBatchIds, ...fromDept]))
  }, [filterBatchIds, filterDepartmentIds, batches])

  const departmentOptions = useMemo(() => {
    const seen = new Set<number>()
    const opts: { value: number; label: string }[] = []
    for (const b of batches) {
      if (b.departmentId == null || b.departmentName == null || seen.has(b.departmentId)) continue
      seen.add(b.departmentId)
      opts.push({ value: b.departmentId, label: b.departmentName })
    }
    return opts.sort((a, b) => a.label.localeCompare(b.label))
  }, [batches])

  // Per-entity view model. When a view dimension (batch/teacher/room) is
  // active the schedule is shown as a separate timetable per entity, navigable
  // through a tab bar, or focused down to a single entity via the searchable
  // dropdown. "Full timetable" (viewMode === 'all') keeps the single merged
  // grid and the multiselect filters behave exactly as before.
  const modeEntities = useMemo(() => {
    const items = viewMode === 'batch' ? batches : viewMode === 'teacher' ? teachers : rooms
    return items
      .map((it) => ({ id: it.id, label: entityLabel(viewMode, it.id, batches, teachers, rooms) }))
      .sort((a, b) => a.label.localeCompare(b.label))
  }, [viewMode, batches, teachers, rooms])

  const entityIdsInSchedule = useMemo(() => {
    const set = new Set<number>()
    for (const s of sessions) {
      const id = viewMode === 'batch' ? s.batchId : viewMode === 'teacher' ? s.teacherId : s.roomId
      if (id != null) set.add(id)
    }
    return modeEntities.filter((e) => set.has(e.id))
  }, [sessions, viewMode, modeEntities])

  const displayEntityId = useMemo(() => {
    if (focusedEntityId != null) return focusedEntityId
    if (activeTabId != null && entityIdsInSchedule.some((e) => e.id === activeTabId)) return activeTabId
    return entityIdsInSchedule[0]?.id ?? null
  }, [focusedEntityId, activeTabId, entityIdsInSchedule])

  const entitySelectOptions = modeEntities.map((e) => ({ value: e.id, label: e.label }))

  const gridFilterBatchIds = viewMode === 'batch' ? (displayEntityId != null ? [displayEntityId] : effectiveBatchIds) : effectiveBatchIds
  const gridFilterTeacherIds = viewMode === 'teacher' ? (displayEntityId != null ? [displayEntityId] : filterTeacherIds) : filterTeacherIds
  const gridFilterRoomIds = viewMode === 'room' ? (displayEntityId != null ? [displayEntityId] : filterRoomIds) : filterRoomIds

  const handleSaveView = () => {
    const name = window.prompt('Name this view:')
    if (!name?.trim()) return
    const next = [
      ...savedViews.filter((v) => v.name !== name.trim()),
      {
        name: name.trim(),
        mode: viewMode,
        batchIds: filterBatchIds,
        teacherIds: filterTeacherIds,
        roomIds: filterRoomIds,
      },
    ].slice(-MAX_SAVED_VIEWS)
    setSavedViews(next)
    window.localStorage.setItem(savedViewsKey(scheduleId), JSON.stringify(next))
    toast.success(`View "${name.trim()}" saved`)
  }

  const handleApplyView = (name: string) => {
    const view = savedViews.find((v) => v.name === name)
    if (!view) return
    setViewMode(view.mode)
    setFocusedEntityId(null)
    setActiveTabId(null)
    setFilterBatchIds(view.batchIds ?? [])
    setFilterTeacherIds(view.teacherIds ?? [])
    setFilterRoomIds(view.roomIds ?? [])
    setFilterDepartmentIds([])
  }

  const teacherOptions = teachers.map((t) => ({ value: t.id, label: t.name }))
  const subjectOptions = subjects.map((s) => ({ value: s.id, label: `${s.code} - ${s.name}` }))
  const roomOptions = rooms.map((r) => ({ value: r.id, label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''}` }))
  const timeslotOptions = timeslots
    .filter((ts) => ts.type === 'CLASS')
    .map((ts) => ({ value: ts.id, label: `${ts.day} ${ts.startTime}-${ts.endTime}` }))

  const disruptionEntityOptions = useMemo(() => {
    switch (disruptionType) {
      case 'TEACHER_UNAVAILABLE':
        return teachers.map((t) => ({ value: String(t.id), label: t.name }))
      case 'ROOM_UNAVAILABLE':
        return rooms.map((r) => ({ value: String(r.id), label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''}` }))
      case 'TIMESLOT_BLOCKED':
        return timeslots
          .filter((ts) => ts.type === 'CLASS')
          .map((ts) => ({ value: String(ts.id), label: `${ts.day} ${ts.startTime}-${ts.endTime}` }))
      case 'SESSION_CANCELLED':
        return sessions.map((s) => ({
          value: String(s.id),
          label: `${s.subjectName ?? 'Session'} - ${s.batchLabel ?? s.day ?? '?'}`,
        }))
      default:
        return []
    }
  }, [disruptionType, teachers, rooms, timeslots, sessions])

  const createBatchOptions = batches.map((batch) => ({
    value: batch.id,
    label: batch.departmentName ? `${batch.departmentName} · Yr ${batch.year}-${batch.section}` : `Yr ${batch.year}-${batch.section}`,
  }))
  const createSectionOptions = sections
    .filter((section) => !createBatchId || section.batchId === +createBatchId)
    .map((section) => ({
      value: section.id,
      label: `${section.batchName ?? `Batch #${section.batchId}`} · ${section.label}`,
    }))

  const renderGrid = () => (
    <TimetableGrid
      sessions={sessions}
      timeslots={timeslots}
      density={density}
      filterBatchIds={gridFilterBatchIds}
      filterTeacherIds={gridFilterTeacherIds}
      filterRoomIds={gridFilterRoomIds}
      highlightConflictsOnly={showConflictsOnly}
      showUnplacedOnly={showUnplacedOnly}
      onSessionClick={openSessionDetail}
      onSessionHover={setHoveredSession}
      heatmapEnabled={heatmapEnabled}
      heatBySessionId={heatBySessionId}
      highlightedSessionIds={highlightedSessionIds}
      preAllocatedSessionIds={preAllocatedSessionIds}
      onSessionContextMenu={handleSessionContextMenu}
      onCellContextMenu={(slot, x, y) => {
        if (scheduleReadonly) return
        setSlotContextMenu({ slot, x, y })
      }}
      onSlotAdd={(slot) => openCreateSession(slot)}
      blockedDays={schedule?.blockedDays ?? []}
      onOrphanSessionsCount={setOrphanCount}
    />
  )

  if (loading) return <div className="animate-pulse h-96 bg-gray-100 rounded-lg" />

  if (loadFailed || !schedule) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 px-6 py-10 text-center">
        <p className="text-sm font-medium text-rose-800">
          {!Number.isFinite(scheduleId) ? 'Invalid schedule id in the URL' : 'Could not load this schedule'}
        </p>
        <p className="mt-1 text-sm text-rose-600">
          It may have been deleted, or the link is broken.
        </p>
        <Button variant="secondary" className="mt-4" icon={<X size={14} />} onClick={() => navigate('/schedule/history')}>
          Back to schedule history
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <Card className="card-glass border-slate-200 text-slate-900">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 className="text-lg font-semibold">{schedule?.name}</h2>
            <div className="flex items-center gap-2 mt-1 flex-wrap">
              {schedule?.status && (
                <Badge
                  label={schedule.status}
                  variant={STATUS_VARIANT[schedule.status] ?? 'gray'}
                  dot
                />
              )}
              {scheduleReadonly && (
                <span className="flex items-center gap-1 text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded">
                  <Lock size={11} />
                  Read-only — archived or infeasible schedules can't be edited
                </span>
              )}
              {schedule?.score && (
                <code className="text-xs bg-slate-100 px-1.5 py-0.5 rounded text-slate-700">{schedule.score}</code>
              )}
              {totalUnassigned > 0 && (
                <span className="flex items-center gap-1 text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded">
                  <AlertCircle size={11} />
                  {totalUnassigned} unassigned
                  {orphanCount > 0 && (
                    <span className="opacity-70">({orphanCount} unplaceable)</span>
                  )}
                </span>
              )}
              {lockedCount > 0 && (
                <span className="flex items-center gap-1 text-xs text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded">
                  <Lock size={11} />
                  {lockedCount} locked
                </span>
              )}
              {preAllocatedSessionIds.size > 0 && (
                <span className="flex items-center gap-1 text-xs text-cyan-700 bg-cyan-50 border border-cyan-200 px-2 py-0.5 rounded">
                  <Pin size={11} />
                  {preAllocatedSessionIds.size} pre-assigned
                </span>
              )}
              {(schedule?.blockedDays?.length ?? 0) > 0 && (
                <span className="flex items-center gap-1 text-xs text-rose-700 bg-rose-50 border border-rose-200 px-2 py-0.5 rounded">
                  <Flame size={11} />
                  {schedule!.blockedDays!.map((d) => d.slice(0, 3)).join(', ')} blocked
                </span>
              )}
              {heatmapEnabled && (
                <span className="flex items-center gap-1 text-xs text-rose-700 bg-rose-50 border border-rose-200 px-2 py-0.5 rounded">
                  <Flame size={11} />
                  {heatSummary.hard} hard - {heatSummary.soft} soft flagged
                </span>
              )}
            </div>
          </div>
          <div className="flex items-center gap-3">
            {savedViews.length > 0 && (
              <Select
                value=""
                onChange={(e) => e.target.value && handleApplyView(e.target.value)}
                options={[{ value: '', label: `Saved views (${savedViews.length})` }, ...savedViews.map((v) => ({ value: v.name, label: v.name }))]}
                className="w-44"
              />
            )}
            <Button variant="secondary" size="sm" icon={<Bookmark size={14} />} onClick={handleSaveView} title="Save current view filters">
              Save view
            </Button>
            <Button variant={heatmapEnabled ? 'primary' : 'secondary'} size="sm" icon={<Target size={14} />} onClick={() => setHeatmapEnabled((v) => !v)}>
              Heatmap
            </Button>
            <Button variant="secondary" size="sm" icon={<Download size={14} />} onClick={handleExportCsv}>
              Export CSV
            </Button>
            <Button variant="secondary" size="sm" icon={<FileText size={14} />} onClick={handleExportPdf} title="Download as PDF — one page per entity, or a single file when a specific entity is focused">
              PDF
            </Button>
            <Button variant="secondary" size="sm" icon={<Sheet size={14} />} onClick={handleExportExcel} title="Download as Excel — one sheet per entity, or a single sheet in the full timetable">
              Excel
            </Button>
            {schedule?.status === 'DRAFT' && (
              <span className="flex items-center gap-2">
                {publishBlockReason && (
                  <span className="text-xs text-rose-700 bg-rose-50 border border-rose-200 px-2 py-1 rounded" title={publishBlockReason}>
                    {heatSummary.hard > 0
                      ? `${heatSummary.hard} hard conflict${heatSummary.hard !== 1 ? 's' : ''} remaining`
                      : 'No placed sessions yet'}
                  </span>
                )}
                <Button
                  variant="primary"
                  size="sm"
                  loading={publishing}
                  icon={<CheckCircle2 size={14} />}
                  disabled={!canPublish}
                  title={publishBlockReason ?? 'Make this timetable the active published schedule'}
                  onClick={handlePublish}
                >
                  Publish
                </Button>
              </span>
            )}
            {schedule && (schedule.status === 'ACTIVE' || schedule.status === 'PARTIAL') && (
              <Button variant="secondary" size="sm" loading={archiving} icon={<Archive size={14} />} onClick={handleArchive}>
                Archive
              </Button>
            )}
            <Button
              variant="secondary"
              size="sm"
              icon={<Zap size={14} />}
              disabled={scheduleReadonly}
              title={scheduleReadonly ? 'Disruptions cannot be applied to archived or infeasible schedules' : undefined}
              onClick={() => { setShowDisruptionPanel(true); setDisruptionPreview(null) }}
            >
              Disruptions
            </Button>
            <Button
              variant={showScorePanel ? 'primary' : 'secondary'}
              size="sm"
              icon={<BarChart3 size={14} />}
              onClick={() => setShowScorePanel((v) => !v)}
              title="Toggle the score breakdown side panel"
            >
              Score
            </Button>
            <Button
              variant={showConflictsPanel ? 'primary' : 'secondary'}
              size="sm"
              icon={<AlertTriangle size={14} />}
              onClick={() => setShowConflictsPanel((v) => !v)}
              title="Toggle the conflict solver side panel"
            >
              Conflicts
            </Button>
            <Button variant="secondary" size="sm" icon={<RefreshCw size={14} />} onClick={load}>
              Refresh
            </Button>
          </div>
        </div>
      </Card>

      <div className={`grid gap-4 ${showScorePanel || showConflictsPanel ? 'xl:grid-cols-[minmax(0,1fr)_340px]' : 'grid-cols-1'}`}>
        <div className="space-y-4">
          <Card>
            <div className="flex items-center gap-3 flex-wrap mb-3">
              <Select
                value={viewMode}
                onChange={(e) => {
                  setViewMode(e.target.value as ViewMode)
                  setFocusedEntityId(null)
                  setActiveTabId(null)
                }}
                options={viewOptions}
              />
              {viewMode !== 'all' && (
                <div className="w-56">
                  <SearchableSelect
                    label={viewMode === 'batch' ? 'Focus a batch' : viewMode === 'teacher' ? 'Focus a teacher' : 'Focus a room'}
                    placeholder={viewMode === 'batch' ? 'All batches…' : viewMode === 'teacher' ? 'All teachers…' : 'All rooms…'}
                    value={focusedEntityId}
                    onChange={(v) => setFocusedEntityId(v == null ? null : Number(v))}
                    options={entitySelectOptions}
                    allowClear
                    maxHeight={260}
                  />
                </div>
              )}
              <div className="flex items-center rounded-md border border-gray-300 overflow-hidden">
                <button
                  type="button"
                  onClick={() => setDensity('compact')}
                  className={`flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium ${density === 'compact' ? 'bg-primary-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}
                  aria-pressed={density === 'compact'}
                >
                  <Rows3 size={13} /> Compact
                </button>
                <button
                  type="button"
                  onClick={() => setDensity('comfortable')}
                  className={`flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium border-l border-gray-300 ${density === 'comfortable' ? 'bg-primary-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}
                  aria-pressed={density === 'comfortable'}
                >
                  <Columns3 size={13} /> Comfortable
                </button>
              </div>
              <Toggle label="Conflicts only" checked={showConflictsOnly} onChange={setShowConflictsOnly} />
              <Toggle label="Unplaced only" checked={showUnplacedOnly} onChange={setShowUnplacedOnly} />
              <Button
                variant={filterBarOpen ? 'primary' : 'secondary'}
                size="sm"
                icon={<Filter size={14} />}
                onClick={() => setFilterBarOpen((v) => !v)}
              >
                Filters
              </Button>
            </div>
            {filterBarOpen && (
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4 border-t border-slate-100 pt-3 mb-3">
                <MultiSelect label="Departments" options={departmentOptions} selected={filterDepartmentIds} onChange={setFilterDepartmentIds} />
                <MultiSelect label="Batches" options={createBatchOptions} selected={filterBatchIds} onChange={setFilterBatchIds} />
                <MultiSelect label="Teachers" options={teacherOptions} selected={filterTeacherIds} onChange={setFilterTeacherIds} />
                <MultiSelect label="Rooms" options={roomOptions} selected={filterRoomIds} onChange={setFilterRoomIds} />
              </div>
            )}
            {viewMode === 'all' ? (
              renderGrid()
            ) : focusedEntityId != null ? (
              <div className="space-y-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                  <h3 className="text-sm font-semibold text-slate-800">
                    {viewMode === 'batch' ? 'Batch' : viewMode === 'teacher' ? 'Teacher' : 'Room'}: {displayEntityId != null ? entityLabel(viewMode, displayEntityId, batches, teachers, rooms) : ''}
                  </h3>
                  <Button variant="secondary" size="sm" onClick={() => setFocusedEntityId(null)}>
                    Show all {viewMode}s
                  </Button>
                </div>
                {displayEntityId != null ? renderGrid() : <p className="text-sm text-slate-500">No sessions for this {viewMode}.</p>}
              </div>
            ) : entityIdsInSchedule.length === 0 ? (
              <p className="text-sm text-slate-500">No {viewMode} assigned sessions yet.</p>
            ) : (
              <div className="space-y-3">
                <div className="flex flex-wrap gap-1.5 border-b border-slate-200 pb-2">
                  <span className="text-xs font-medium text-slate-500 mr-1 self-center">
                    {viewMode === 'batch' ? 'Batches' : viewMode === 'teacher' ? 'Teachers' : 'Rooms'}:
                  </span>
                  {entityIdsInSchedule.map((e) => (
                    <button
                      key={e.id}
                      type="button"
                      onClick={() => setActiveTabId(e.id)}
                      className={`rounded-md px-2.5 py-1 text-xs font-medium ${displayEntityId === e.id ? 'bg-primary-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
                    >
                      {e.label}
                    </button>
                  ))}
                </div>
                {displayEntityId != null ? renderGrid() : <p className="text-sm text-slate-500">Select a {viewMode} above.</p>}
              </div>
            )}
          </Card>

          <Card title="Conflict Inspector" description="Hover over a session to inspect why this slot is risky.">
            {!hoveredSession && (
              <p className="text-sm text-slate-500">Move the pointer over a session cell to inspect local conflict details.</p>
            )}
            {hoveredSession && (
              <div className="space-y-2 text-sm">
                <p className="font-semibold text-slate-900">{hoveredSession.subjectName}</p>
                <p className="text-xs text-slate-500">
                  {hoveredSession.teacherName ?? 'No teacher'} - {hoveredSession.roomNumber ?? 'No room'} - {hoveredSession.day ?? 'No day'}
                </p>
                {(heatBySessionId[hoveredSession.id]?.notes ?? []).length === 0 && (
                  <p className="text-sm text-emerald-700">No local heatmap penalties detected for this session.</p>
                )}
                {(heatBySessionId[hoveredSession.id]?.notes ?? []).map((note) => (
                  <div key={note} className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
                    {note}
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        {(showScorePanel || showConflictsPanel) && (
          <div className="space-y-4">
            {showScorePanel && (
              <ScoreBreakdownPanel
                score={scoreBreakdown}
                rawExplanation={rawExplanation}
                sessions={sessions}
                highlightedSessionIds={highlightedSessionIds}
                onViolationClick={highlightByText}
                onClearHighlight={() => setHighlightedSessionIds(new Set())}
              />
            )}
            {showConflictsPanel && (
              <ConflictSolverSidecar
                session={conflictSession}
                suggestions={backendSuggestions ?? fallbackSuggestions}
                onClose={() => setConflictSession(null)}
                onApplySuggestion={(timeslotId) => {
                  if (!conflictSession) return
                  setSelectedSession(conflictSession)
                  setEditMode(true)
                  setEditTeacherId(conflictSession.teacherId?.toString() ?? '')
                  setEditRoomId(conflictSession.roomId?.toString() ?? '')
                  setEditTimeslotId(String(timeslotId))
                }}
              />
            )}
          </div>
        )}
      </div>

      <Modal
        open={selectedSession !== null}
        onClose={() => { setSelectedSession(null); setEditMode(false) }}
        title={editMode ? 'Edit Session Assignment' : 'Session Details'}
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => { setSelectedSession(null); setEditMode(false) }}>
              Close
            </Button>
            {!editMode && !scheduleReadonly && (
              <Button onClick={() => setEditMode(true)}>
                Edit Assignment
              </Button>
            )}
            {!editMode && selectedSession && !scheduleReadonly && (
              <Button variant="secondary" icon={selectedSession.isLocked ? <Unlock size={14} /> : <Lock size={14} />} onClick={handleQuickLockToggle}>
                {selectedSession.isLocked ? 'Unlock' : 'Lock'}
              </Button>
            )}
            {editMode && (
              <Button loading={editSaving} onClick={handleSaveAssignment}>
                Save
              </Button>
            )}
          </>
        }
      >
        {selectedSession && (
          <div className="space-y-4">
            <dl className="space-y-2 text-sm">
              {[
                ['Subject', selectedSession.subjectName ?? '-'],
                ['Batch', selectedSession.batchLabel ?? '-'],
                ['Duration', `${selectedSession.duration}h`],
                ['Type', selectedSession.isLab ? 'Lab' : 'Lecture'],
              ].map(([key, val]) => (
                <div key={String(key)} className="flex justify-between border-b border-gray-100 pb-2">
                  <dt className="font-medium text-gray-600">{key}</dt>
                  <dd className="text-gray-900">{val}</dd>
                </div>
              ))}
            </dl>

            {editMode ? (
              <div className="space-y-3 pt-2">
                {editError && (
                  <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                    {editError}
                  </div>
                )}
                <SearchableSelect
                  label="Teacher"
                  value={editTeacherId ? +editTeacherId : null}
                  onChange={(v) => setEditTeacherId(v == null ? '' : String(v))}
                  options={teacherOptions}
                  allowClear
                />
                <SearchableSelect
                  label="Room"
                  value={editRoomId ? +editRoomId : null}
                  onChange={(v) => setEditRoomId(v == null ? '' : String(v))}
                  options={roomOptions}
                  allowClear
                />
                <SearchableSelect
                  label="Timeslot"
                  value={editTimeslotId ? +editTimeslotId : null}
                  onChange={(v) => setEditTimeslotId(v == null ? '' : String(v))}
                  options={timeslotOptions}
                  allowClear
                />
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={editLocked}
                    onChange={(e) => setEditLocked(e.target.checked)}
                  />
                  <span className="flex items-center gap-1">
                    {editLocked ? <Lock size={13} /> : <Unlock size={13} />}
                    Lock this session (prevent re-scheduling)
                  </span>
                </label>
              </div>
            ) : (
              <dl className="space-y-2 text-sm">
                {[
                  ['Teacher', selectedSession.teacherName ?? 'Unassigned'],
                  ['Room', selectedSession.roomNumber
                    ? `${selectedSession.roomNumber}${selectedSession.buildingName ? ` (${selectedSession.buildingName})` : ''}`
                    : 'Unassigned'],
                  ['Day', selectedSession.day ?? '-'],
                  ['Time', selectedSession.startTime
                    ? `${selectedSession.startTime} - ${selectedSession.endTime}`
                    : '-'],
                  ['Locked', selectedSession.isLocked ? 'Yes' : 'No'],
                ].map(([key, val]) => (
                  <div key={String(key)} className="flex justify-between border-b border-gray-100 pb-2">
                    <dt className="font-medium text-gray-600">{key}</dt>
                    <dd className="text-gray-900">{val}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
        )}
      </Modal>

      <Modal
        open={createSessionOpen}
        onClose={() => { setCreateSessionOpen(false); setCreateSlot(null) }}
        title={createSlot ? `Add Session Here - ${createSlot.day} ${createSlot.startTime}-${createSlot.endTime}` : 'Add Session'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => { setCreateSessionOpen(false); setCreateSlot(null) }}>Cancel</Button>
            <Button loading={createSaving} onClick={handleCreateSession}>Create</Button>
          </>
        }
      >
        <div className="space-y-4">
          {createError && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {createError}
            </div>
          )}
          <div className="grid md:grid-cols-2 gap-4">
            <SearchableSelect label="Subject" value={createSubjectId ? +createSubjectId : null} onChange={(v) => setCreateSubjectId(v == null ? '' : String(v))} options={subjectOptions} placeholder="Select subject" />
            <SearchableSelect label="Teacher" value={createTeacherId ? +createTeacherId : null} onChange={(v) => setCreateTeacherId(v == null ? '' : String(v))} options={teacherOptions} allowClear />
            <SearchableSelect label="Room" value={createRoomId ? +createRoomId : null} onChange={(v) => setCreateRoomId(v == null ? '' : String(v))} options={roomOptions} allowClear />
            <Input label="Duration (hours)" type="number" min={1} max={4} value={createDuration} onChange={(e) => setCreateDuration(Math.max(1, +e.target.value || 1))} />
          </div>
          <div className="grid md:grid-cols-2 gap-4">
            <SearchableSelect label="Batch" value={createBatchId ? +createBatchId : null} onChange={(v) => { setCreateBatchId(v == null ? '' : String(v)); setCreateSectionId('') }} options={createBatchOptions} placeholder="Select batch" helpText="Pick a batch or override it with a section." allowClear />
            <SearchableSelect label="Section" value={createSectionId ? +createSectionId : null} onChange={(v) => setCreateSectionId(v == null ? '' : String(v))} options={createSectionOptions} placeholder={createBatchId ? 'Optional section' : 'Select a batch first'} disabled={!createBatchId} helpText="Optional. If selected, it takes precedence over batch." allowClear />
          </div>
          <label className="flex items-center gap-2 text-sm cursor-pointer">
            <input type="checkbox" checked={createLocked} onChange={(e) => setCreateLocked(e.target.checked)} />
            <span className="flex items-center gap-1">
              {createLocked ? <Lock size={13} /> : <Unlock size={13} />}
              Lock this new session
            </span>
          </label>
          {createSlot && (
            <p className="text-xs text-gray-500">This session will be created in {createSlot.day} from {createSlot.startTime} to {createSlot.endTime}.</p>
          )}
        </div>
      </Modal>

      {slotContextMenu && (
        <ContextMenu
          x={slotContextMenu.x}
          y={slotContextMenu.y}
          onClose={() => setSlotContextMenu(null)}
          items={[
            { label: 'Add session here', icon: <Plus size={13} />, onClick: () => openCreateSession(slotContextMenu.slot) },
          ]}
        />
      )}

      {sessionContextMenu && (
        <ContextMenu
          x={sessionContextMenu.x}
          y={sessionContextMenu.y}
          onClose={() => setSessionContextMenu(null)}
          items={buildSessionContextItems(sessionContextMenu.session)}
        />
      )}

      <ConfirmDialog
        open={deleteSession !== null}
        onCancel={() => setDeleteSession(null)}
        onConfirm={handleDeleteSession}
        title="Delete session"
        message={
          deleteSession
            ? `This will permanently delete "${deleteSession.subjectName ?? `session #${deleteSession.id}`}"${deleteSession.batchLabel ? ` for ${deleteSession.batchLabel}` : ''}. This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        variant="danger"
        loading={deleteSaving}
      />

      <Modal
        open={showDisruptionPanel}
        onClose={() => { setShowDisruptionPanel(false); setDisruptionPreview(null) }}
        title="Disruption Management"
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => { setShowDisruptionPanel(false); setDisruptionPreview(null) }}>
              Cancel
            </Button>
            <Button
              variant="secondary"
              loading={disruptionPreviewing}
              onClick={handlePreviewDisruption}
            >
              Preview Impact
            </Button>
            {disruptionPreview && disruptionPreview.impactedSessionCount > 0 && (
              <Button loading={disruptionApplying} onClick={handleApplyDisruption}>
                Apply and Re-solve ({disruptionPreview.impactedSessionCount} sessions)
              </Button>
            )}
          </>
        }
      >
        <div className="space-y-4">
          <Select
            label="Disruption Type"
            value={disruptionType}
            onChange={(e) => { setDisruptionType(e.target.value as DisruptionType); setDisruptionEntityId(''); setDisruptionPreview(null) }}
            options={[
              { value: 'TEACHER_UNAVAILABLE', label: 'Teacher Unavailable' },
              { value: 'ROOM_UNAVAILABLE', label: 'Room Unavailable' },
              { value: 'TIMESLOT_BLOCKED', label: 'Timeslot Blocked' },
              { value: 'SESSION_CANCELLED', label: 'Session Cancelled' },
              { value: 'SPECIAL_EVENT', label: 'Special Event' },
            ]}
          />

          {disruptionType !== 'SPECIAL_EVENT' && (
            <SearchableSelect
              label={
                disruptionType === 'TEACHER_UNAVAILABLE' ? 'Affected Teacher' :
                disruptionType === 'ROOM_UNAVAILABLE' ? 'Affected Room' :
                disruptionType === 'TIMESLOT_BLOCKED' ? 'Blocked Timeslot' :
                'Cancelled Session'
              }
              value={disruptionEntityId ? +disruptionEntityId : null}
              onChange={(v) => { setDisruptionEntityId(v == null ? '' : String(v)); setDisruptionPreview(null) }}
              options={disruptionEntityOptions}
              allowClear
            />
          )}

          {(disruptionType === 'TEACHER_UNAVAILABLE' || disruptionType === 'ROOM_UNAVAILABLE') && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Date (optional - limits to that day)
              </label>
              <input
                type="date"
                value={disruptionDate}
                onChange={(e) => { setDisruptionDate(e.target.value); setDisruptionPreview(null) }}
                className="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
          )}

          {disruptionType === 'SPECIAL_EVENT' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input
                type="text"
                value={disruptionDescription}
                onChange={(e) => { setDisruptionDescription(e.target.value); setDisruptionPreview(null) }}
                placeholder="e.g. University Day - all sessions cancelled"
                className="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
          )}

          {disruptionPreview && (
            <div className="mt-2 border border-gray-200 rounded-lg overflow-hidden">
              <div className="bg-gray-50 px-4 py-2 flex items-center justify-between">
                <span className="text-sm font-medium text-gray-700">
                  {disruptionPreview.disruption}
                </span>
                <span className={`text-xs font-semibold px-2 py-0.5 rounded ${disruptionPreview.impactedSessionCount > 0 ? 'bg-amber-100 text-amber-700' : 'bg-green-100 text-green-700'}`}>
                  {disruptionPreview.impactedSessionCount === 0 ? 'No impact' : `${disruptionPreview.impactedSessionCount} session${disruptionPreview.impactedSessionCount !== 1 ? 's' : ''} affected`}
                </span>
              </div>
              {disruptionPreview.impactedSessions.length > 0 && (
                <div className="divide-y divide-gray-100 max-h-60 overflow-y-auto">
                  {disruptionPreview.impactedSessions.map((s) => (
                    <div key={s.id} className="px-4 py-2 text-sm flex items-center justify-between gap-2">
                      <div>
                        <span className="font-medium">{s.subjectName}</span>
                        {s.batchLabel && <span className="text-gray-500 ml-1">({s.batchLabel})</span>}
                      </div>
                      <div className="flex items-center gap-2 text-xs text-gray-500 shrink-0">
                        {s.teacherName && <span>{s.teacherName}</span>}
                        {s.roomNumber && <span>{s.roomNumber}</span>}
                        {s.day && s.startTime && <span>{s.day} {s.startTime}</span>}
                        {s.locked && <Lock size={11} className="text-indigo-500" />}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}
