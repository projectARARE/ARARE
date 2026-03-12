import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2, Zap } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge, ConfirmDialog } from '../components/ui'
import type { Column, ContextMenuItem } from '../components/ui'
import { eventApi, scheduleApi, teacherApi, roomApi } from '../services/api'
import type { Event, EventRequest, EventType, Schedule, Teacher, Room } from '../types'
import { useToast } from '../contexts/ToastContext'

const EVENT_TYPES: EventType[] = [
  'EXAM', 'MAINTENANCE', 'FESTIVAL', 'TEACHER_LEAVE',
  'GUEST_LECTURE', 'SPORTS_DAY', 'SEMINAR', 'HOLIDAY', 'OTHER',
]

const EMPTY: EventRequest = {
  title: '', type: 'EXAM', startDate: '', endDate: '',
  description: '', affectedRoomIds: [], affectedTeacherIds: [], affectedTimeslotIds: [],
}

const typeVariant = (t: EventType): 'red' | 'green' | 'yellow' | 'blue' => {
  if (t === 'EXAM') return 'red'
  if (t === 'HOLIDAY' || t === 'FESTIVAL' || t === 'SPORTS_DAY') return 'green'
  if (t === 'MAINTENANCE') return 'yellow'
  return 'blue'
}

export default function Events() {
  const { toast } = useToast()
  const [items, setItems] = useState<Event[]>([])
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Event | null>(null)
  const [form, setForm] = useState<EventRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [applyTarget, setApplyTarget] = useState<{ eventId: number; scheduleId: string }>({ eventId: 0, scheduleId: '' })
  const [applyError, setApplyError] = useState<string | null>(null)
  const [applying, setApplying] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([eventApi.getAll(), scheduleApi.getAll(), teacherApi.getAll(), roomApi.getAll()])
      .then(([evs, scheds, t, r]) => { setItems(evs); setSchedules(scheds); setTeachers(t); setRooms(r) })
      .catch(() => toast.error('Failed to load events'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (e: Event) => {
    setEditing(e)
    setForm({
      title: e.title, type: e.type, startDate: e.startDate, endDate: e.endDate,
      description: e.description,
      affectedTeacherIds: e.affectedTeacherIds ?? [],
      affectedRoomIds: e.affectedRoomIds ?? [],
      affectedTimeslotIds: e.affectedTimeslotIds ?? [],
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.title.trim()) { toast.error('Title is required'); return }
    if (!form.startDate) { toast.error('Start date is required'); return }
    if (!form.endDate) { toast.error('End date is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await eventApi.update(editing.id, form)
        toast.success('Event updated')
      } else {
        await eventApi.create(form)
        toast.success('Event created')
      }
      setOpen(false)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirmId == null) return
    setDeleting(true)
    try {
      await eventApi.delete(confirmId)
      toast.success('Event deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
    } finally {
      setDeleting(false)
    }
  }

  const handleApply = async () => {
    if (!applyTarget.scheduleId) { setApplyError('Please select a schedule'); return }
    setApplying(true)
    setApplyError(null)
    try {
      await eventApi.applyToSchedule(applyTarget.eventId, +applyTarget.scheduleId)
      setApplyTarget({ eventId: 0, scheduleId: '' })
      toast.success('Event applied to schedule')
    } catch (e) {
      setApplyError(e instanceof Error ? e.message : 'Failed to apply event')
    } finally {
      setApplying(false)
    }
  }

  const toggleTeacher = (id: number) => {
    setForm((prev) => {
      const cur = prev.affectedTeacherIds ?? []
      return { ...prev, affectedTeacherIds: cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id] }
    })
  }

  const toggleRoom = (id: number) => {
    setForm((prev) => {
      const cur = prev.affectedRoomIds ?? []
      return { ...prev, affectedRoomIds: cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id] }
    })
  }

  const scheduleOptions = schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))
  const typeOptions = EVENT_TYPES.map((t) => ({ value: t, label: t.replace(/_/g, ' ') }))

  const getContextItems = (e: Event): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(e) },
    { label: 'Apply to Schedule', icon: <Zap size={13} />, onClick: () => { setApplyTarget({ eventId: e.id, scheduleId: '' }); setApplyError(null) } },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(e.id) },
  ]

  const columns: Column<Event>[] = [
    { key: 'title', header: 'Title', sortValue: (e) => e.title, render: (e) => <span className="font-medium">{e.title}</span> },
    { key: 'type', header: 'Type', render: (e) => <Badge label={e.type.replace(/_/g, ' ')} variant={typeVariant(e.type)} /> },
    { key: 'dates', header: 'Dates', sortValue: (e) => e.startDate, render: (e) => `${e.startDate} → ${e.endDate}` },
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
      key: 'actions', header: '', width: '180px',
      render: (e) => (
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" icon={<Zap size={14} />}
            onClick={() => { setApplyTarget({ eventId: e.id, scheduleId: '' }); setApplyError(null) }}>
            Apply
          </Button>
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(e)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(e.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  return (
    <>
      <Card title="Events" description="Manage disruptions and special events"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Event</Button>}
      >
        <Table
          columns={columns} data={items} loading={loading} keyExtractor={(e) => e.id}
          searchable searchKeys={[(e) => e.title, (e) => e.type]}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit Event' : 'Add Event'} size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Midterm Exams" />
          <Select label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as EventType })} options={typeOptions} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start Date" type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            <Input label="End Date" type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
          </div>
          <Input label="Description" value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Optional description…" />
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">Affected Teachers <span className="font-normal text-gray-500">(optional)</span></p>
            <div className="grid grid-cols-2 gap-2 max-h-32 overflow-y-auto border border-gray-200 rounded-md p-3">
              {teachers.map((t) => (
                <label key={t.id} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" checked={(form.affectedTeacherIds ?? []).includes(t.id)} onChange={() => toggleTeacher(t.id)} />
                  {t.name}
                </label>
              ))}
              {teachers.length === 0 && <p className="text-sm text-gray-400">No teachers configured.</p>}
            </div>
          </div>
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">Affected Rooms <span className="font-normal text-gray-500">(optional)</span></p>
            <div className="grid grid-cols-2 gap-2 max-h-32 overflow-y-auto border border-gray-200 rounded-md p-3">
              {rooms.map((r) => (
                <label key={r.id} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" checked={(form.affectedRoomIds ?? []).includes(r.id)} onChange={() => toggleRoom(r.id)} />
                  {r.roomNumber} {r.buildingName ? `(${r.buildingName})` : ''}
                </label>
              ))}
              {rooms.length === 0 && <p className="text-sm text-gray-400">No rooms configured.</p>}
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
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{applyError}</div>
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

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Event"
        message="This event will be permanently deleted."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
