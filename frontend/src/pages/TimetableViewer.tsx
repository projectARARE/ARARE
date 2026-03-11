import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { Info, RefreshCw } from 'lucide-react'
import { Card, Button, Select, Badge, Modal } from '../components/ui'
import TimetableGrid from '../components/timetable/TimetableGrid'
import { scheduleApi, timeslotApi, batchApi, teacherApi } from '../services/api'
import type { Schedule, ClassSession, Timeslot, Batch, Teacher } from '../types'

type ViewMode = 'batch' | 'teacher'

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

  const [schedule, setSchedule] = useState<Schedule | null>(null)
  const [sessions, setSessions] = useState<ClassSession[]>([])
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [loading, setLoading] = useState(true)
  const [viewMode, setViewMode] = useState<ViewMode>('batch')
  const [filterId, setFilterId] = useState<number | undefined>()
  const [selectedSession, setSelectedSession] = useState<ClassSession | null>(null)
  const [explanation, setExplanation] = useState<string | null>(null)
  const [showExplanation, setShowExplanation] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.all([
      scheduleApi.getById(scheduleId),
      scheduleApi.getSessions(scheduleId),
      timeslotApi.getAll(),
      batchApi.getAll(),
      teacherApi.getAll(),
    ])
      .then(([sched, sess, ts, b, t]) => {
        setSchedule(sched)
        setSessions(sess)
        setTimeslots(ts)
        setBatches(b)
        setTeachers(t)
        if (b.length > 0) setFilterId(b[0].id)
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load schedule'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [scheduleId])

  const loadExplanation = async () => {
    try {
      const text = await scheduleApi.getExplanation(scheduleId)
      setExplanation(text)
      setShowExplanation(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load explanation')
    }
  }

  const viewOptions = [
    { value: 'batch', label: 'By Batch' },
    { value: 'teacher', label: 'By Teacher' },
  ]

  const entityOptions: { value: number; label: string }[] =
    viewMode === 'batch'
      ? batches.map((b) => ({ value: b.id, label: `Yr ${b.year} – ${b.section}` }))
      : teachers.map((t) => ({ value: t.id, label: t.name }))

  if (loading) return <div className="animate-pulse h-96 bg-gray-100 rounded-lg" />

  return (
    <div className="space-y-4">
      {error && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <Card>
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 className="text-lg font-semibold">{schedule?.name}</h2>
            <div className="flex items-center gap-2 mt-1">
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
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Button variant="secondary" size="sm" icon={<Info size={14} />} onClick={loadExplanation}>
              Explain Score
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
            onChange={(e) => { setViewMode(e.target.value as ViewMode); setFilterId(undefined) }}
            options={viewOptions}
          />
          {entityOptions.length > 0 && (
            <Select
              value={filterId ?? ''}
              onChange={(e) => setFilterId(+e.target.value)}
              options={entityOptions}
            />
          )}
        </div>
        <TimetableGrid
          sessions={sessions}
          timeslots={timeslots}
          filterBatchId={viewMode === 'batch' ? filterId : undefined}
          filterTeacherId={viewMode === 'teacher' ? filterId : undefined}
          onSessionClick={setSelectedSession}
        />
      </Card>

      <Modal
        open={selectedSession !== null}
        onClose={() => setSelectedSession(null)}
        title="Session Details"
      >
        {selectedSession && (
          <dl className="space-y-3 text-sm">
            {[
              ['Subject', selectedSession.subjectName ?? '—'],
              ['Teacher', selectedSession.teacherName ?? 'Unassigned'],
              ['Room', selectedSession.roomNumber
                ? `${selectedSession.roomNumber}${selectedSession.buildingName ? ` (${selectedSession.buildingName})` : ''}`
                : 'Unassigned'],
              ['Day', selectedSession.day ?? '—'],
              ['Time', selectedSession.startTime
                ? `${selectedSession.startTime} – ${selectedSession.endTime}`
                : '—'],
              ['Duration', `${selectedSession.duration}h`],
              ['Batch', selectedSession.batchLabel ?? '—'],
              ['Locked', selectedSession.isLocked ? 'Yes' : 'No'],
            ].map(([key, val]) => (
              <div key={String(key)} className="flex justify-between border-b border-gray-100 pb-2">
                <dt className="font-medium text-gray-600">{key}</dt>
                <dd className="text-gray-900">{val}</dd>
              </div>
            ))}
          </dl>
        )}
      </Modal>

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
    </div>
  )
}
