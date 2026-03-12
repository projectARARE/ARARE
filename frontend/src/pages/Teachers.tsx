import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { teacherApi, subjectApi, timeslotApi, buildingApi } from '../services/api'
import type { Teacher, TeacherRequest, Subject, Timeslot, Building, SchoolDay } from '../types'
import { useToast } from '../contexts/ToastContext'

const DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const EMPTY: TeacherRequest = {
  name: '',
  subjectIds: [],
  availableTimeslotIds: [],
  preferredBuildingIds: [],
  maxDailyHours: 6,
  maxWeeklyHours: 20,
  maxConsecutiveClasses: 3,
  movementPenalty: 1,
}

export default function Teachers() {
  const { toast } = useToast()
  const [items, setItems] = useState<Teacher[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [buildings, setBuildings] = useState<Building[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Teacher | null>(null)
  const [form, setForm] = useState<TeacherRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([teacherApi.getAll(), subjectApi.getAll(), timeslotApi.getAll(), buildingApi.getAll()])
      .then(([t, s, ts, b]) => { setItems(t); setSubjects(s); setTimeslots(ts); setBuildings(b) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (t: Teacher) => {
    setEditing(t)
    setForm({
      name: t.name,
      subjectIds: t.subjectIds ?? [],
      availableTimeslotIds: t.availableTimeslotIds ?? [],
      preferredBuildingIds: t.preferredBuildingIds ?? [],
      maxDailyHours: t.maxDailyHours,
      maxWeeklyHours: t.maxWeeklyHours,
      maxConsecutiveClasses: t.maxConsecutiveClasses,
      movementPenalty: t.movementPenalty,
      preferredFreeDay: t.preferredFreeDay,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await teacherApi.update(editing.id, form)
        toast.success('Teacher updated')
      } else {
        await teacherApi.create(form)
        toast.success('Teacher created')
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
      await teacherApi.delete(confirmId)
      toast.success('Teacher deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const toggleId = (key: 'subjectIds' | 'availableTimeslotIds' | 'preferredBuildingIds', id: number) => {
    setForm((prev) => {
      const current = prev[key] ?? []
      return {
        ...prev,
        [key]: current.includes(id) ? current.filter((x) => x !== id) : [...current, id],
      }
    })
  }

  const dayOptions = [
    { value: '', label: 'No preference' },
    ...DAYS.map((d) => ({ value: d, label: d })),
  ]

  const timeslotsByDay = DAYS.reduce<Record<SchoolDay, Timeslot[]>>((acc, day) => {
    acc[day] = timeslots.filter((t) => t.day === day && t.type === 'CLASS')
    return acc
  }, {} as Record<SchoolDay, Timeslot[]>)

  const columns: Column<Teacher>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (t) => t.name,
      render: (t) => <span className="font-medium">{t.name}</span>,
    },
    {
      key: 'subjects', header: 'Subjects',
      render: (t) => <span className="text-sm text-gray-600">{(t.subjectNames ?? []).join(', ') || '—'}</span>,
    },
    { key: 'hours', header: 'Max Hours', render: (t) => `${t.maxDailyHours}d / ${t.maxWeeklyHours}w` },
    { key: 'consecutive', header: 'Max Consec.', render: (t) => t.maxConsecutiveClasses },
    { key: 'freeDay', header: 'Free Day', render: (t) => t.preferredFreeDay ?? '—' },
    {
      key: 'actions', header: '', width: '96px',
      render: (t) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(t)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(t.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (t: Teacher): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(t) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(t.id) },
  ]

  return (
    <>
      <Card title="Teachers" description="Manage teaching staff"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Teacher</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(t) => t.id}
          searchable
          searchKeys={[(t) => t.name, (t) => (t.subjectNames ?? []).join(' ')]}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit Teacher' : 'Add Teacher'} size="xl"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-5">
          <Input label="Full Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Dr. Jane Smith" />

          <div className="grid grid-cols-3 gap-4">
            <Input label="Max Daily Hours" type="number" min={1} value={form.maxDailyHours} onChange={(e) => setForm({ ...form, maxDailyHours: +e.target.value })} />
            <Input label="Max Weekly Hours" type="number" min={1} value={form.maxWeeklyHours} onChange={(e) => setForm({ ...form, maxWeeklyHours: +e.target.value })} />
            <Input label="Max Consecutive" type="number" min={1} value={form.maxConsecutiveClasses} onChange={(e) => setForm({ ...form, maxConsecutiveClasses: +e.target.value })} />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Input label="Movement Penalty" type="number" min={0} value={form.movementPenalty ?? 1} onChange={(e) => setForm({ ...form, movementPenalty: +e.target.value })} helpText="Higher = solver avoids building switches more" />
            <Select label="Preferred Free Day" value={form.preferredFreeDay ?? ''} onChange={(e) => setForm({ ...form, preferredFreeDay: e.target.value as SchoolDay || undefined })} options={dayOptions} />
          </div>

          {/* Qualified Subjects */}
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">Qualified Subjects</p>
            <div className="grid grid-cols-2 gap-2 max-h-36 overflow-y-auto border border-gray-200 rounded-md p-3">
              {subjects.map((s) => (
                <label key={s.id} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" checked={(form.subjectIds ?? []).includes(s.id)} onChange={() => toggleId('subjectIds', s.id)} />
                  {s.name} ({s.code})
                </label>
              ))}
              {subjects.length === 0 && <p className="text-sm text-gray-400">No subjects configured yet.</p>}
            </div>
          </div>

          {/* Availability */}
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-1">Availability <span className="font-normal text-gray-500">(leave all unchecked = always available)</span></p>
            <p className="text-xs text-gray-500 mb-2">Only select timeslots when this teacher has a restricted/part-time schedule</p>
            <div className="border border-gray-200 rounded-md p-3 max-h-52 overflow-y-auto space-y-3">
              {DAYS.map((day) => {
                const slots = timeslotsByDay[day]
                if (!slots.length) return null
                return (
                  <div key={day}>
                    <p className="text-xs font-semibold text-gray-500 uppercase mb-1">{day}</p>
                    <div className="flex flex-wrap gap-2">
                      {slots.map((ts) => (
                        <label key={ts.id} className="flex items-center gap-1 text-xs cursor-pointer bg-gray-50 border border-gray-200 rounded px-2 py-1">
                          <input
                            type="checkbox"
                            checked={(form.availableTimeslotIds ?? []).includes(ts.id)}
                            onChange={() => toggleId('availableTimeslotIds', ts.id)}
                          />
                          {ts.startTime}–{ts.endTime}
                        </label>
                      ))}
                    </div>
                  </div>
                )
              })}
              {timeslots.filter((t) => t.type === 'CLASS').length === 0 && (
                <p className="text-sm text-gray-400">No CLASS timeslots configured yet.</p>
              )}
            </div>
          </div>

          {/* Preferred Buildings */}
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">Preferred Buildings <span className="font-normal text-gray-500">(soft constraint)</span></p>
            <div className="flex flex-wrap gap-2">
              {buildings.map((b) => (
                <label key={b.id} className="flex items-center gap-1 text-sm cursor-pointer bg-gray-50 border border-gray-200 rounded px-3 py-1">
                  <input
                    type="checkbox"
                    checked={(form.preferredBuildingIds ?? []).includes(b.id)}
                    onChange={() => toggleId('preferredBuildingIds', b.id)}
                  />
                  {b.name}
                </label>
              ))}
              {buildings.length === 0 && <p className="text-sm text-gray-400">No buildings configured yet.</p>}
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Teacher"
        message="This will remove the teacher and clear related session assignments. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
