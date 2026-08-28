import { useState, useEffect, useRef } from 'react'
import { AlertTriangle, RefreshCw, Zap } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Select, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { scheduleApi, eventApi } from '../services/api'
import { waitForJob } from '../hooks/useSolveJob'
import type { Schedule, Event } from '../types'
import { useToast } from '../contexts/ToastContext'

export default function DisruptionHandling() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const abortRef = useRef<AbortController | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    abortRef.current = controller
    return () => controller.abort()
  }, [])
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [events, setEvents] = useState<Event[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [applying, setApplying] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.allSettled([scheduleApi.getAll(), eventApi.getAll()])
      .then(([s, e]) => {
        if (s.status === 'fulfilled') {
          const active = s.value.filter((x) => x.status === 'ACTIVE' || x.status === 'PARTIAL')
          setSchedules(active)
          // Keep the user's selection when possible; only auto-pick when there
          // is none or the chosen schedule no longer exists.
          setSelectedSchedule((current) => {
            if (active.length && current && active.some((x) => String(x.id) === current)) {
              return current
            }
            return active.length ? String(active[0].id) : ''
          })
        }
        if (e.status === 'fulfilled') {
          setEvents(e.value)
        }
        if (s.status === 'rejected' || e.status === 'rejected') {
          setError('Some disruption data failed to refresh')
        }
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleApply = async (eventId: number) => {
    if (!selectedSchedule) { setError('Select a target schedule'); return }
    setApplying(eventId)
    setError(null)
    try {
      const job = await eventApi.applyToSchedule(eventId, +selectedSchedule)
      const finished = await waitForJob(job, abortRef.current?.signal)
      if (finished.status === 'FAILED') {
        setError(finished.errorMessage || 'Re-optimization failed')
        return
      }
      if (finished.status === 'CANCELLED') {
        setError('Re-optimization was cancelled')
        return
      }
      const schedule = await scheduleApi.getById(+selectedSchedule)
      if (schedule.status === 'INFEASIBLE') {
        toast.warning('Event applied, but the schedule is now infeasible')
      } else {
        toast.success('Event applied and schedule re-optimized')
      }
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to apply event')
    } finally {
      setApplying(null)
    }
  }

  const scheduleOptions = schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))

  const getContextItems = (e: Event): ContextMenuItem[] => {
    const canApply = !!selectedSchedule
    return [
      { label: 'Apply to current schedule', icon: <Zap size={13} />, onClick: () => handleApply(e.id), disabled: !canApply },
      { label: 'Manage events', icon: <RefreshCw size={13} />, divider: true, onClick: () => navigate('/events') },
    ]
  }

  const columns: Column<Event>[] = [
    { key: 'title', header: 'Event', render: (e) => <span className="font-medium">{e.title}</span> },
    { key: 'type', header: 'Type', render: (e) => <Badge label={e.type} variant="yellow" /> },
    { key: 'dates', header: 'Dates', render: (e) => `${e.startDate} → ${e.endDate}` },
    {
      key: 'actions', header: '', width: '120px',
      render: (e) => (
        <Button
          size="sm"
          variant="secondary"
          icon={<RefreshCw size={14} />}
          loading={applying === e.id}
          disabled={!selectedSchedule}
          onClick={() => handleApply(e.id)}
        >
          Apply &amp; Re-solve
        </Button>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      {error && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      <Card
        title="Disruption Handling"
        description="Apply an event to an active schedule and trigger partial re-optimization"
      >
        {schedules.length === 0 ? (
          <div className="text-center py-8">
            <AlertTriangle className="mx-auto text-yellow-500 mb-2" size={32} />
            <p className="text-gray-500">No active schedules found. Generate a schedule first.</p>
          </div>
        ) : (
          <div className="space-y-4">
            <Select
              label="Target Schedule"
              value={selectedSchedule}
              onChange={(e) => setSelectedSchedule(e.target.value)}
              options={scheduleOptions}
            />
            <p className="text-sm text-gray-500">
              Select an event below to apply it to the chosen schedule. The solver will re-optimize
              only the affected sessions.
            </p>
          </div>
        )}
      </Card>

      <Card title="Events" description="Available disruptions to apply">
        <Table
          columns={columns}
          data={events}
          loading={loading}
          keyExtractor={(e) => e.id}
          exportable
          exportFilename="events"
          onRowContextMenu={getContextItems}
          emptyMessage="No events. Create events in the Events page first."
        />
      </Card>
    </div>
  )
}
