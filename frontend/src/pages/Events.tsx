import { useState, useEffect } from 'react'
import { Plus, Trash2, Zap } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { eventApi, scheduleApi, teacherApi, roomApi } from '../services/api'
import type { Event, EventRequest, EventType, Schedule, Teacher, Room } from '../types'

const EVENT_TYPES: EventType[] = [
  'EXAM', 'MAINTENANCE', 'FESTIVAL', 'TEACHER_LEAVE',
  'GUEST_LECTURE', 'SPORTS_DAY', 'SEMINAR', 'HOLIDAY', 'OTHER',
]

const EMPTY: EventRequest = {
  title: '',
  type: 'EXAM',
  startDate: '',
  endDate: '',
  description: '',
  affectedRoomIds: [],
  affectedTeacherIds: [],
  affectedTimeslotIds: [],
}

const typeVariant = (t: EventType): 'red' | 'green' | 'yellow' | 'blue' => {
  if (t === 'EXAM') return 'red'
  if (t === 'HOLIDAY' || t === 'FESTIVAL' || t === 'SPORTS_DAY') return 'green'
  if (t === 'MAINTENANCE') return 'yellow'
  return 'blue'
}

export default function Events() {
  const [items, setItems] = useState<Event[]>([])
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<EventRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [applyTarget, setApplyTarget] = useState<{ eventId: number; scheduleId: string }>({ eventId: 0, scheduleId: '' })
  const [applyError, setApplyError] = useState<string | null>(null)
  const [applying, setApplying] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([eventApi.getAll(), scheduleApi.getAll(), teacherApi.getAll(), roomApi.getAll()])
      .then(([evs, scheds, t, r]) => { setItems(evs); setSchedules(scheds); setTeachers(t); setRooms(r) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleSave = async () => {
    if (!form.title.trim()) { setError('Title is required'); return }
    if (!form.startDate) { setError('Start date is required'); return }
    if (!form.endDate) { setError('End date is required'); return }
    setSaving(true)
    try {
      await eventApi.create(form)
      setOpen(false)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm('Delete this event?')) return
    try {
      await eventApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const handleApply = async () => {
    if (!applyTarget.scheduleId) { setApplyError('Please select a schedule'); return }
    setApplying(true)
    setApplyError(null)
    try {
      await eventApi.applyToSchedule(applyTarget.eventId, +applyTarget.scheduleId)
      setApplyTarget({ eventId: 0, scheduleId: '' })
    } catch (e) {
      setApplyError(e instanceof Error ? e.message : 'Failed to apply event')
    } finally {
      setApplying(false)
    }
  }

  const toggleTeacher = (id: number) => {
    setForm((prev) => {
      const current = prev.affectedTeacherIds ?? []
      return { ...prev, affectedTeacherIds: current.includes(id) ? current.filter((x) => x !== id) : [...current, id] }
    })
  }

  const toggleRoom = (id: number) => {
    setForm((prev) => {
      const current = prev.affectedRoomIds ?? []
      return { ...prev, affectedRoomIds: current.includes(id) ? current.filter((x) => x !== id) : [...current, id] }
    })
  }

  const scheduleOptions = schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))
  const typeOptions = EVENT_TYPES.map((t) => ({ value: t, label: t.replace(/_/g, ' ') }))

  const columns: Column<Event>[] = [
    { key: 'title', header: 'Title', render: (e) => <span className="font-medium">{e.title}</span> },
    { key: 'type', header: 'Type', render: (e) => <Badge label={e.type.replace(/_/g, ' ')} variant={typeVariant(e.type)} /> },
    { key: 'dates', header: 'Dates', render: (e) => `${e.startDate} → ${e.endDate}` },
    {
      key: 'affected', header: 'Affected',
      render: (e) => (
        <span className="text-xs text-gray-500">
          {[
            e.affectedTeacherIds.length ? `${e.affectedTeacherIds.length} teacher(s)` : '',
            e.affectedRoomIds.length ? `${e.affectedRoomIds.length} room(s)` : '',
          ].filter(Boolean).join(', ') || '—'}
        </span>
      ),
    },
    { key: 'description', header: 'Description', render: (e) => e.description ?? '—' },
    {
      key: 'actions', header: '', width: '140px',
      render: (e) => (
        <div className="flex gap-2">
          <Button
            variant="ghost" size="sm" icon={<Zap size={14} />}
            onClick={() => { setApplyTarget({ eventId: e.id, scheduleId: '' }); setApplyError(null) }}
          >
            Apply
          </Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(e.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  return (
    <>
      {error && !open && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      <Card title="Events" description="Manage disruptions and special events"
        actions={<Button icon={<Plus size={16} />} onClick={() => { setForm(EMPTY); setError(null); setOpen(true) }}>Add Event</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(e) => e.id} />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title="Add Event" size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          <Input label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Midterm Exams" />
          <Select label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as EventType })} options={typeOptions} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start Date" type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            <Input label="End Date" type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
          </div>
          <Input label="Description" value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Optional description…" />

          {/* Affected Teachers */}
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">
              Affected Teachers <span className="font-normal text-gray-500">(optional)</span>
            </p>
            <div className="grid grid-cols-2 gap-2 max-h-32 overflow-y-auto border border-gray-200 rounded-md p-3">
              {teachers.map((t) => (
                <label key={t.id} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" checked={(form.affectedTeacherIds ?? []).includes(t.id)} onChange={() => toggleTeacher(t.id)} />
                  {t.name}
                </label>
              ))}
              {teachers.length === 0 && <p className="text-sm text-gray-400">No teachers configured yet.</p>}
            </div>
          </div>

          {/* Affected Rooms */}
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">
              Affected Rooms <span className="font-normal text-gray-500">(optional)</span>
            </p>
            <div className="grid grid-cols-2 gap-2 max-h-32 overflow-y-auto border border-gray-200 rounded-md p-3">
              {rooms.map((r) => (
                <label key={r.id} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" checked={(form.affectedRoomIds ?? []).includes(r.id)} onChange={() => toggleRoom(r.id)} />
                  {r.roomNumber} {r.buildingName ? `(${r.buildingName})` : ''}
                </label>
              ))}
              {rooms.length === 0 && <p className="text-sm text-gray-400">No rooms configured yet.</p>}
            </div>
          </div>
        </div>
      </Modal>

      <Modal
        open={applyTarget.eventId !== 0}
        onClose={() => { setApplyTarget({ eventId: 0, scheduleId: '' }); setApplyError(null) }}
        title="Apply Event to Schedule"
        footer={
          <>
            <Button variant="secondary" onClick={() => { setApplyTarget({ eventId: 0, scheduleId: '' }); setApplyError(null) }}>Cancel</Button>
            <Button loading={applying} onClick={handleApply}>Apply &amp; Re-optimize</Button>
          </>
        }
      >
        <div className="space-y-4">
          {applyError && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {applyError}
            </div>
          )}
          <Select
            label="Target Schedule"
            value={applyTarget.scheduleId}
            onChange={(e) => setApplyTarget({ ...applyTarget, scheduleId: e.target.value })}
            options={scheduleOptions}
            placeholder="Select a schedule…"
          />
        </div>
      </Modal>
    </>
  )
}
