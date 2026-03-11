import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { timeslotApi } from '../services/api'
import type { Timeslot, TimeslotRequest, SchoolDay, TimeslotType } from '../types'

const DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
const TYPES: TimeslotType[] = ['CLASS', 'BREAK', 'BLOCKED']

const EMPTY: TimeslotRequest = { day: 'MONDAY', startTime: '08:00', endTime: '09:00', type: 'CLASS' }

const typeVariant = (t: TimeslotType): 'green' | 'yellow' | 'red' =>
  t === 'CLASS' ? 'green' : t === 'BREAK' ? 'yellow' : 'red'

export default function Timeslots() {
  const [items, setItems] = useState<Timeslot[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Timeslot | null>(null)
  const [form, setForm] = useState<TimeslotRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    timeslotApi.getAll().then(setItems).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setError(null); setOpen(true) }
  const openEdit = (t: Timeslot) => {
    setEditing(t)
    setForm({ day: t.day, startTime: t.startTime, endTime: t.endTime, type: t.type })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.startTime) { setError('Start time is required'); return }
    if (!form.endTime) { setError('End time is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await timeslotApi.update(editing.id, form)
      } else {
        await timeslotApi.create(form)
      }
      setOpen(false)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm('Delete this timeslot?')) return
    try {
      await timeslotApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const dayOptions = DAYS.map((d) => ({ value: d, label: d }))
  const typeOptions = TYPES.map((t) => ({ value: t, label: t }))

  const columns: Column<Timeslot>[] = [
    { key: 'day', header: 'Day', render: (t) => t.day },
    { key: 'start', header: 'Start', render: (t) => t.startTime },
    { key: 'end', header: 'End', render: (t) => t.endTime },
    { key: 'type', header: 'Type', render: (t) => <Badge label={t.type} variant={typeVariant(t.type)} /> },
    {
      key: 'actions', header: '', width: '96px',
      render: (t) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(t)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(t.id)}>Delete</Button>
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
      <Card title="Timeslots" description="Define weekly timeslot grid"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Timeslot</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(t) => t.id} />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit Timeslot' : 'Add Timeslot'}
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
          <Select label="Day" value={form.day} onChange={(e) => setForm({ ...form, day: e.target.value as SchoolDay })} options={dayOptions} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start Time" type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
            <Input label="End Time" type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
          </div>
          <Select label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as TimeslotType })} options={typeOptions} />
        </div>
      </Modal>
    </>
  )
}
