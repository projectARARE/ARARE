import { useState, useEffect, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { AlertCircle, Move, RefreshCw, Lock, Unlock, Zap, Download, Flame, Target } from 'lucide-react'
import { Card, Button, Select, Badge, Modal } from '../components/ui'
import TimetableGrid from '../components/timetable/TimetableGrid'
import ScoreBreakdownPanel from '../components/timetable/ScoreBreakdownPanel'
import ConflictSolverSidecar, { buildConflictSuggestions } from '../components/timetable/ConflictSolverSidecar'
import { scheduleApi, timeslotApi, batchApi, teacherApi, roomApi, sessionApi } from '../services/api'
import type {
  Schedule,
  ClassSession,
  Timeslot,
  Batch,
  Teacher,
  Room,
  DisruptionRequest,
  DisruptionResponse,
  DisruptionType,
  ScoreExplanation,
  ConflictSuggestion,
} from '../types'
import { useToast } from '../contexts/ToastContext'

type ViewMode = 'batch' | 'teacher' | 'room'

type HeatEntry = {
  hard: number
  soft: number
  notes: string[]
}

type DragPreview = {
  slotId: number
  severity: 'hard' | 'soft' | 'clean'
  label: string
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

function buildDragPreview(dragged: ClassSession | null, slot: Timeslot | null, sessions: ClassSession[]): DragPreview | null {
  if (!dragged || !slot || !dragged.id) return null

  let hardConflicts = 0
  let softPenalty = 0
  const withoutDragged = sessions.filter((s) => s.id !== dragged.id)

  for (const s of withoutDragged) {
    if (s.timeslotId !== slot.id || s.day !== slot.day) continue
    if (dragged.teacherId && s.teacherId && dragged.teacherId === s.teacherId) hardConflicts += 1
    if (dragged.roomId && s.roomId && dragged.roomId === s.roomId) hardConflicts += 1
    if (dragged.batchId && s.batchId && dragged.batchId === s.batchId) hardConflicts += 1
  }

  if (dragged.batchId && dragged.subjectId) {
    const sameDaySubject = withoutDragged.filter((s) =>
      s.day === slot.day && s.batchId === dragged.batchId && s.subjectId === dragged.subjectId,
    )
    softPenalty += sameDaySubject.length
  }

  if (hardConflicts > 0) {
    return {
      slotId: slot.id,
      severity: 'hard',
      label: '!! HARD CONFLICT',
    }
  }

  if (softPenalty > 0) {
    return {
      slotId: slot.id,
      severity: 'soft',
      label: `+${softPenalty} soft penalty`,
    }
  }

  return {
    slotId: slot.id,
    severity: 'clean',
    label: 'Clean move',
  }
}

export default function TimetableViewer() {
  const { id } = useParams<{ id: string }>()
  const scheduleId = Number(id)
  const { toast } = useToast()

  const [schedule, setSchedule] = useState<Schedule | null>(null)
  const [sessions, setSessions] = useState<ClassSession[]>([])
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [viewMode, setViewMode] = useState<ViewMode>('batch')
  const [batchFilterId, setBatchFilterId] = useState<number | undefined>()
  const [teacherFilterId, setTeacherFilterId] = useState<number | undefined>()
  const [roomFilterId, setRoomFilterId] = useState<number | undefined>()
  const [selectedSession, setSelectedSession] = useState<ClassSession | null>(null)
  const [editMode, setEditMode] = useState(false)
  const [editTeacherId, setEditTeacherId] = useState<string>('')
  const [editRoomId, setEditRoomId] = useState<string>('')
  const [editTimeslotId, setEditTimeslotId] = useState<string>('')
  const [editLocked, setEditLocked] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const [scoreBreakdown, setScoreBreakdown] = useState<ScoreExplanation | null>(null)
  const [rawExplanation, setRawExplanation] = useState<string | null>(null)
  const [heatmapEnabled, setHeatmapEnabled] = useState(false)
  const [highlightedSessionIds, setHighlightedSessionIds] = useState<Set<number>>(new Set())
  const [hoveredSession, setHoveredSession] = useState<ClassSession | null>(null)
  const [conflictSession, setConflictSession] = useState<ClassSession | null>(null)
  const [backendSuggestions, setBackendSuggestions] = useState<ConflictSuggestion[] | null>(null)

  const [draggedSession, setDraggedSession] = useState<ClassSession | null>(null)
  const [dragPreview, setDragPreview] = useState<DragPreview | null>(null)

  const [showDisruptionPanel, setShowDisruptionPanel] = useState(false)
  const [disruptionType, setDisruptionType] = useState<DisruptionType>('TEACHER_UNAVAILABLE')
  const [disruptionEntityId, setDisruptionEntityId] = useState<string>('')
  const [disruptionDate, setDisruptionDate] = useState<string>('')
  const [disruptionDescription, setDisruptionDescription] = useState<string>('')
  const [disruptionPreview, setDisruptionPreview] = useState<DisruptionResponse | null>(null)
  const [disruptionPreviewing, setDisruptionPreviewing] = useState(false)
  const [disruptionApplying, setDisruptionApplying] = useState(false)

  const heatBySessionId = useMemo(() => computeHeatMap(sessions), [sessions])

  const unassignedCount = useMemo(
    () => sessions.filter((s) => !s.timeslotId).length,
    [sessions],
  )
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
    setLoading(true)
    Promise.all([
      scheduleApi.getById(scheduleId),
      scheduleApi.getSessions(scheduleId),
      timeslotApi.getAll(),
      batchApi.getAll(),
      teacherApi.getAll(),
      roomApi.getAll(),
    ])
      .then(([sched, sess, ts, b, t, r]) => {
        setSchedule(sched)
        setSessions(sess)
        setTimeslots(ts)
        setBatches(b)
        setTeachers(t)
        setRooms(r)
        const batchInSessions = new Set(sess.map((s) => s.batchId).filter((x): x is number => x != null))
        const teacherInSessions = new Set(sess.map((s) => s.teacherId).filter((x): x is number => x != null))
        const roomInSessions = new Set(sess.map((s) => s.roomId).filter((x): x is number => x != null))
        setBatchFilterId(b.find((x) => batchInSessions.has(x.id))?.id)
        setTeacherFilterId(t.find((x) => teacherInSessions.has(x.id))?.id)
        setRoomFilterId(r.find((x) => roomInSessions.has(x.id))?.id)
      })
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to load schedule'))
      .finally(() => setLoading(false))

    loadExplanations()
  }

  useEffect(() => { load() }, [scheduleId])

  const handleExportCsv = async () => {
    try {
      const blob = await scheduleApi.exportCsv(scheduleId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `timetable-${schedule?.name ?? scheduleId}.csv`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Export failed')
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
      await scheduleApi.applyDisruption(scheduleId, buildDisruptionRequest())
      toast.success(`Disruption applied - re-solving ${disruptionPreview?.impactedSessionCount ?? '?'} sessions`)
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
      scheduleApi.getSessions(scheduleId)
        .then(setSessions)
        .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to reload sessions'))
      loadExplanations()
    } catch (e) {
      setEditError(e instanceof Error ? e.message : 'Failed to save assignment')
    } finally {
      setEditSaving(false)
    }
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
    { value: 'batch', label: 'By Batch' },
    { value: 'teacher', label: 'By Teacher' },
    { value: 'room', label: 'By Room' },
  ]

  const entityOptions: { value: number; label: string }[] =
    viewMode === 'batch'
      ? batches.map((b) => ({ value: b.id, label: `Yr ${b.year} - ${b.section}` }))
      : viewMode === 'teacher'
        ? teachers.map((t) => ({ value: t.id, label: t.name }))
        : rooms.map((r) => ({ value: r.id, label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''} [${r.type}]` }))

  const currentFilterId =
    viewMode === 'batch' ? batchFilterId :
    viewMode === 'teacher' ? teacherFilterId :
    roomFilterId

  const setCurrentFilterId = (nextId: number) => {
    if (viewMode === 'batch') setBatchFilterId(nextId)
    else if (viewMode === 'teacher') setTeacherFilterId(nextId)
    else setRoomFilterId(nextId)
  }

  const teacherOptions = [
    { value: '', label: '- Unassigned -' },
    ...teachers.map((t) => ({ value: t.id, label: t.name })),
  ]
  const roomOptions = [
    { value: '', label: '- Unassigned -' },
    ...rooms.map((r) => ({ value: r.id, label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''}` })),
  ]
  const timeslotOptions = [
    { value: '', label: '- Unassigned -' },
    ...timeslots
      .filter((ts) => ts.type === 'CLASS')
      .map((ts) => ({ value: ts.id, label: `${ts.day} ${ts.startTime}-${ts.endTime}` })),
  ]

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

  if (loading) return <div className="animate-pulse h-96 bg-gray-100 rounded-lg" />

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
              {schedule?.score && (
                <code className="text-xs bg-slate-100 px-1.5 py-0.5 rounded text-slate-700">{schedule.score}</code>
              )}
              {unassignedCount > 0 && (
                <span className="flex items-center gap-1 text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded">
                  <AlertCircle size={11} />
                  {unassignedCount} unassigned
                </span>
              )}
              {lockedCount > 0 && (
                <span className="flex items-center gap-1 text-xs text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded">
                  <Lock size={11} />
                  {lockedCount} locked
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
            <Button variant={heatmapEnabled ? 'primary' : 'secondary'} size="sm" icon={<Target size={14} />} onClick={() => setHeatmapEnabled((v) => !v)}>
              Heatmap
            </Button>
            <Button variant="secondary" size="sm" icon={<Download size={14} />} onClick={handleExportCsv}>
              Export CSV
            </Button>
            <Button variant="secondary" size="sm" icon={<Zap size={14} />} onClick={() => { setShowDisruptionPanel(true); setDisruptionPreview(null) }}>
              Disruptions
            </Button>
            <Button variant="secondary" size="sm" icon={<RefreshCw size={14} />} onClick={load}>
              Refresh
            </Button>
          </div>
        </div>
      </Card>

      <div className="grid xl:grid-cols-[minmax(0,1fr)_340px] gap-4">
        <div className="space-y-4">
          <Card>
            <div className="flex items-center gap-4 mb-4 flex-wrap">
              <Select
                value={viewMode}
                onChange={(e) => { setViewMode(e.target.value as ViewMode) }}
                options={viewOptions}
              />
              {entityOptions.length > 0 && (
                <Select
                  value={currentFilterId ?? ''}
                  onChange={(e) => setCurrentFilterId(+e.target.value)}
                  options={entityOptions}
                />
              )}
              <div className="ml-auto text-xs text-slate-500 flex items-center gap-1.5">
                <Move size={13} />
                Drag any session to preview impact and drop to edit
              </div>
            </div>
            <TimetableGrid
              sessions={sessions}
              timeslots={timeslots}
              filterBatchId={viewMode === 'batch' ? batchFilterId : undefined}
              filterTeacherId={viewMode === 'teacher' ? teacherFilterId : undefined}
              filterRoomId={viewMode === 'room' ? roomFilterId : undefined}
              onSessionClick={openSessionDetail}
              onSessionHover={setHoveredSession}
              heatmapEnabled={heatmapEnabled}
              heatBySessionId={heatBySessionId}
              highlightedSessionIds={highlightedSessionIds}
              onSessionDragStart={(session) => {
                setDraggedSession(session)
                setDragPreview(null)
              }}
              onSessionDragEnd={() => {
                setDraggedSession(null)
                setDragPreview(null)
              }}
              onSlotDragHover={(slot) => setDragPreview(buildDragPreview(draggedSession, slot, sessions))}
              onSlotDrop={(slot) => {
                if (!draggedSession) return
                setSelectedSession(draggedSession)
                setEditMode(true)
                setEditTeacherId(draggedSession.teacherId?.toString() ?? '')
                setEditRoomId(draggedSession.roomId?.toString() ?? '')
                setEditTimeslotId(String(slot.id))
                setEditLocked(draggedSession.isLocked)
                setDraggedSession(null)
                setDragPreview(null)
              }}
              dragPreview={dragPreview}
            />
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

        <ScoreBreakdownPanel
          score={scoreBreakdown}
          rawExplanation={rawExplanation}
          sessions={sessions}
          highlightedSessionIds={highlightedSessionIds}
          onViolationClick={highlightByText}
          onClearHighlight={() => setHighlightedSessionIds(new Set())}
        />

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
            {!editMode && (
              <Button onClick={() => setEditMode(true)}>
                Edit Assignment
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
                <Select
                  label="Teacher"
                  value={editTeacherId}
                  onChange={(e) => setEditTeacherId(e.target.value)}
                  options={teacherOptions}
                />
                <Select
                  label="Room"
                  value={editRoomId}
                  onChange={(e) => setEditRoomId(e.target.value)}
                  options={roomOptions}
                />
                <Select
                  label="Timeslot"
                  value={editTimeslotId}
                  onChange={(e) => setEditTimeslotId(e.target.value)}
                  options={timeslotOptions}
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
            <Select
              label={
                disruptionType === 'TEACHER_UNAVAILABLE' ? 'Affected Teacher' :
                disruptionType === 'ROOM_UNAVAILABLE' ? 'Affected Room' :
                disruptionType === 'TIMESLOT_BLOCKED' ? 'Blocked Timeslot' :
                'Cancelled Session'
              }
              value={disruptionEntityId}
              onChange={(e) => { setDisruptionEntityId(e.target.value); setDisruptionPreview(null) }}
              options={[{ value: '', label: '- Select -' }, ...disruptionEntityOptions]}
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
