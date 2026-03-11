import { useState, useEffect } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { Card, Button, Select, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { scheduleApi, eventApi } from '../services/api'
import type { Schedule, Event } from '../types'

export default function DisruptionHandling() {
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [events, setEvents] = useState<Event[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [applying, setApplying] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.all([scheduleApi.getAll(), eventApi.getAll()])
      .then(([s, e]) => {
        const active = s.filter((x) => x.status === 'ACTIVE' || x.status === 'PARTIAL')
        setSchedules(active)
        setEvents(e)
        if (active.length) setSelectedSchedule(String(active[0].id))
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleApply = async (eventId: number) => {
    if (!selectedSchedule) { setError('Select a target schedule'); return }
    setApplying(eventId)
    setError(null)
    try {
      await eventApi.applyToSchedule(eventId, +selectedSchedule)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to apply event')
    } finally {
      setApplying(null)
    }
  }

  const scheduleOptions = schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))

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
          emptyMessage="No events. Create events in the Events page first."
        />
      </Card>
    </div>
  )
}
