import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { timeslotApi } from '../services/api'
import type { Timeslot, TimeslotRequest, SchoolDay, TimeslotType } from '../types'
import { useToast } from '../contexts/ToastContext'

const DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
const TYPES: TimeslotType[] = ['CLASS', 'BREAK', 'BLOCKED']

const EMPTY: TimeslotRequest = { day: 'MONDAY', startTime: '08:00', endTime: '09:00', type: 'CLASS' }

const typeVariant = (t: TimeslotType): 'green' | 'yellow' | 'red' =>
  t === 'CLASS' ? 'green' : t === 'BREAK' ? 'yellow' : 'red'

export default function Timeslots() {
  const { toast } = useToast()
  const [items, setItems] = useState<Timeslot[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Timeslot | null>(null)
  const [form, setForm] = useState<TimeslotRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    timeslotApi.getAll().then(setItems).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (t: Timeslot) => {
    setEditing(t)
    setForm({ day: t.day, startTime: t.startTime, endTime: t.endTime, type: t.type })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.startTime) { toast.error('Start time is required'); return }
    if (!form.endTime) { toast.error('End time is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await timeslotApi.update(editing.id, form)
        toast.success('Timeslot updated')
      } else {
        await timeslotApi.create(form)
        toast.success('Timeslot created')
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
      await timeslotApi.delete(confirmId)
      toast.success('Timeslot deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const dayOptions = DAYS.map((d) => ({ value: d, label: d }))
  const typeOptions = TYPES.map((t) => ({ value: t, label: t }))

  const columns: Column<Timeslot>[] = [
    {
      key: 'day', header: 'Day',
      sortValue: (t) => DAYS.indexOf(t.day).toString().padStart(2, '0'),
      render: (t) => t.day,
    },
    {
      key: 'start', header: 'Start',
      sortValue: (t) => t.startTime,
      render: (t) => t.startTime,
    },
    { key: 'end', header: 'End', render: (t) => t.endTime },
    { key: 'type', header: 'Type', render: (t) => <Badge label={t.type} variant={typeVariant(t.type)} /> },
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

  const getContextItems = (t: Timeslot): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(t) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(t.id) },
  ]

  return (
    <>
      <Card title="Timeslots" description="Define weekly timeslot grid"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Timeslot</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(t) => t.id}
          searchable
          searchKeys={[(t) => t.day, (t) => t.startTime, (t) => t.endTime, (t) => t.type]}
          onRowContextMenu={getContextItems}
        />
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
          <Select label="Day" value={form.day} onChange={(e) => setForm({ ...form, day: e.target.value as SchoolDay })} options={dayOptions} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start Time" type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
            <Input label="End Time" type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
          </div>
          <Select label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as TimeslotType })} options={typeOptions} />
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Timeslot"
        message="This will remove the timeslot and may affect session assignments. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
