import { useState, useEffect, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { Info, RefreshCw, Lock, Unlock, AlertCircle, Zap, Download } from 'lucide-react'
import { Card, Button, Select, Badge, Modal } from '../components/ui'
import TimetableGrid from '../components/timetable/TimetableGrid'
import { scheduleApi, timeslotApi, batchApi, teacherApi, roomApi, sessionApi } from '../services/api'
import type { Schedule, ClassSession, Timeslot, Batch, Teacher, Room, DisruptionRequest, DisruptionResponse, DisruptionType } from '../types'
import { useToast } from '../contexts/ToastContext'

type ViewMode = 'batch' | 'teacher' | 'room'

const STATUS_VARIANT: Record<string, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  ACTIVE: 'green',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
  DRAFT: 'gray',
  ARCHIVED: 'blue',
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
  const [explanation, setExplanation] = useState<string | null>(null)
  const [showExplanation, setShowExplanation] = useState(false)

  // Disruption panel state
  const [showDisruptionPanel, setShowDisruptionPanel] = useState(false)
  const [disruptionType, setDisruptionType] = useState<DisruptionType>('TEACHER_UNAVAILABLE')
  const [disruptionEntityId, setDisruptionEntityId] = useState<string>('')
  const [disruptionDate, setDisruptionDate] = useState<string>('')
  const [disruptionDescription, setDisruptionDescription] = useState<string>('')
  const [disruptionPreview, setDisruptionPreview] = useState<DisruptionResponse | null>(null)
  const [disruptionPreviewing, setDisruptionPreviewing] = useState(false)
  const [disruptionApplying, setDisruptionApplying] = useState(false)

  const unassignedCount = useMemo(
    () => sessions.filter((s) => !s.timeslotId).length,
    [sessions],
  )
  const lockedCount = useMemo(
    () => sessions.filter((s) => s.isLocked).length,
    [sessions],
  )

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
        if (b.length > 0) setBatchFilterId(b[0].id)
        if (t.length > 0) setTeacherFilterId(t[0].id)
        if (r.length > 0) setRoomFilterId(r[0].id)
      })
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to load schedule'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [scheduleId])

  const loadExplanation = async () => {
    try {
      const text = await scheduleApi.getExplanation(scheduleId)
      setExplanation(text)
      setShowExplanation(true)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to load explanation')
    }
  }

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
    affectedEntityId: disruptionEntityId ? +disruptionEntityId : 0,
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
      toast.success(`Disruption applied — re-solving ${disruptionPreview?.impactedSessionCount ?? '?'} sessions`)
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
        locked: editLocked,
      })
      setSelectedSession(null)
      setEditMode(false)
      toast.success('Session assignment updated')
      // Reload only sessions (not full data) to keep it fast
      scheduleApi.getSessions(scheduleId).then(setSessions).catch(() => {})
    } catch (e) {
      setEditError(e instanceof Error ? e.message : 'Failed to save assignment')
    } finally {
      setEditSaving(false)
    }
  }

  const viewOptions = [
    { value: 'batch', label: 'By Batch' },
    { value: 'teacher', label: 'By Teacher' },
    { value: 'room', label: 'By Room' },
  ]

  const entityOptions: { value: number; label: string }[] =
    viewMode === 'batch'
      ? batches.map((b) => ({ value: b.id, label: `Yr ${b.year} – ${b.section}` }))
      : viewMode === 'teacher'
        ? teachers.map((t) => ({ value: t.id, label: t.name }))
        : rooms.map((r) => ({ value: r.id, label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''} [${r.type}]` }))

  const currentFilterId =
    viewMode === 'batch' ? batchFilterId :
    viewMode === 'teacher' ? teacherFilterId :
    roomFilterId

  const setCurrentFilterId = (id: number) => {
    if (viewMode === 'batch') setBatchFilterId(id)
    else if (viewMode === 'teacher') setTeacherFilterId(id)
    else setRoomFilterId(id)
  }

  const teacherOptions = [
    { value: '', label: '— Unassigned —' },
    ...teachers.map((t) => ({ value: t.id, label: t.name })),
  ]
  const roomOptions = [
    { value: '', label: '— Unassigned —' },
    ...rooms.map((r) => ({ value: r.id, label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''}` })),
  ]
  const timeslotOptions = [
    { value: '', label: '— Unassigned —' },
    ...timeslots
      .filter((ts) => ts.type === 'CLASS')
      .map((ts) => ({ value: ts.id, label: `${ts.day} ${ts.startTime}–${ts.endTime}` })),
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
          .map((ts) => ({ value: String(ts.id), label: `${ts.day} ${ts.startTime}–${ts.endTime}` }))
      case 'SESSION_CANCELLED':
        return sessions.map((s) => ({
          value: String(s.id),
          label: `${s.subjectName ?? 'Session'} – ${s.batchLabel ?? s.day ?? '?'}`,
        }))
      default:
        return []
    }
  }, [disruptionType, teachers, rooms, timeslots, sessions])

  if (loading) return <div className="animate-pulse h-96 bg-gray-100 rounded-lg" />

  return (
    <div className="space-y-4">
      <Card>
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
                <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">{schedule.score}</code>
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
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Button variant="secondary" size="sm" icon={<Info size={14} />} onClick={loadExplanation}>
              Explain Score
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
        </div>
        <TimetableGrid
          sessions={sessions}
          timeslots={timeslots}
          filterBatchId={viewMode === 'batch' ? batchFilterId : undefined}
          filterTeacherId={viewMode === 'teacher' ? teacherFilterId : undefined}
          filterRoomId={viewMode === 'room' ? roomFilterId : undefined}
          onSessionClick={openSessionDetail}
        />
      </Card>

      {/* Session Detail / Edit Modal */}
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
            {/* Always-visible session info */}
            <dl className="space-y-2 text-sm">
              {[
                ['Subject', selectedSession.subjectName ?? '—'],
                ['Batch', selectedSession.batchLabel ?? '—'],
                ['Duration', `${selectedSession.duration}h`],
                ['Type', selectedSession.isLab ? 'Lab' : 'Lecture'],
              ].map(([key, val]) => (
                <div key={String(key)} className="flex justify-between border-b border-gray-100 pb-2">
                  <dt className="font-medium text-gray-600">{key}</dt>
                  <dd className="text-gray-900">{val}</dd>
                </div>
              ))}
            </dl>

            {/* Edit assignment fields */}
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
              /* View-only current assignment */
              <dl className="space-y-2 text-sm">
                {[
                  ['Teacher', selectedSession.teacherName ?? 'Unassigned'],
                  ['Room', selectedSession.roomNumber
                    ? `${selectedSession.roomNumber}${selectedSession.buildingName ? ` (${selectedSession.buildingName})` : ''}`
                    : 'Unassigned'],
                  ['Day', selectedSession.day ?? '—'],
                  ['Time', selectedSession.startTime
                    ? `${selectedSession.startTime} – ${selectedSession.endTime}`
                    : '—'],
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

      {/* Score Explanation Modal */}
      <Modal
        open={showExplanation}
        onClose={() => setShowExplanation(false)}
        title="Score Explanation"
        size="xl"
      >
        <pre className="text-xs bg-gray-50 p-4 rounded-md overflow-x-auto whitespace-pre-wrap">
          {explanation ?? 'No explanation available.'}
        </pre>
      </Modal>

      {/* Disruption Panel Modal */}
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
                Apply &amp; Re-solve ({disruptionPreview.impactedSessionCount} sessions)
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
              { value: 'ROOM_UNAVAILABLE',    label: 'Room Unavailable' },
              { value: 'TIMESLOT_BLOCKED',    label: 'Timeslot Blocked' },
              { value: 'SESSION_CANCELLED',   label: 'Session Cancelled' },
              { value: 'SPECIAL_EVENT',       label: 'Special Event' },
            ]}
          />

          {disruptionType !== 'SPECIAL_EVENT' && (
            <Select
              label={
                disruptionType === 'TEACHER_UNAVAILABLE' ? 'Affected Teacher' :
                disruptionType === 'ROOM_UNAVAILABLE'    ? 'Affected Room' :
                disruptionType === 'TIMESLOT_BLOCKED'    ? 'Blocked Timeslot' :
                'Cancelled Session'
              }
              value={disruptionEntityId}
              onChange={(e) => { setDisruptionEntityId(e.target.value); setDisruptionPreview(null) }}
              options={[{ value: '', label: '— Select —' }, ...disruptionEntityOptions]}
            />
          )}

          {(disruptionType === 'TEACHER_UNAVAILABLE' || disruptionType === 'ROOM_UNAVAILABLE') && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Date (optional — limits to that day)
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
                placeholder="e.g. University Day — all sessions cancelled"
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
