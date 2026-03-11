import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { batchApi, departmentApi } from '../services/api'
import type { Batch, BatchRequest, Department, SchoolDay } from '../types'

const DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const EMPTY: BatchRequest = {
  departmentId: 0,
  year: 1,
  section: 'A',
  studentCount: 60,
}

export default function Batches() {
  const [items, setItems] = useState<Batch[]>([])
  const [depts, setDepts] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Batch | null>(null)
  const [form, setForm] = useState<BatchRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.all([batchApi.getAll(), departmentApi.getAll()])
      .then(([b, d]) => { setItems(b); setDepts(d) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, departmentId: depts[0]?.id ?? 0 })
    setError(null)
    setOpen(true)
  }

  const openEdit = (b: Batch) => {
    setEditing(b)
    setForm({
      departmentId: b.departmentId,
      year: b.year,
      section: b.section,
      studentCount: b.studentCount,
      workingDays: b.workingDays,
      preferredFreeDay: b.preferredFreeDay,
    })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.departmentId) { setError('Please select a department'); return }
    if (!form.section.trim()) { setError('Section is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await batchApi.update(editing.id, form)
      } else {
        await batchApi.create(form)
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
    if (!window.confirm('Delete this batch?')) return
    try {
      await batchApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const deptOptions = depts.map((d) => ({ value: d.id, label: d.name }))
  const dayOptions = [
    { value: '', label: 'No preference' },
    ...DAYS.map((d) => ({ value: d, label: d })),
  ]

  const columns: Column<Batch>[] = [
    {
      key: 'batch', header: 'Batch',
      render: (b) => (
        <div>
          <p className="font-medium">Year {b.year} – {b.section}</p>
          <p className="text-xs text-gray-500">{b.departmentName ?? `Dept #${b.departmentId}`}</p>
        </div>
      ),
    },
    { key: 'year', header: 'Year', render: (b) => `Year ${b.year}` },
    { key: 'students', header: 'Students', render: (b) => b.studentCount },
    { key: 'freeDay', header: 'Free Day', render: (b) => b.preferredFreeDay ?? '—' },
    {
      key: 'actions', header: '', width: '96px',
      render: (b) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(b)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(b.id)}>Delete</Button>
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
      <Card
        title="Batches"
        description="Manage student batches (classes)"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Batch</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(b) => b.id} />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Batch' : 'Add Batch'}
        size="lg"
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
          <div className="grid grid-cols-2 gap-4">
            <Select label="Department" value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: +e.target.value })} options={deptOptions} placeholder="Select department…" />
            <Input label="Year" type="number" min={1} max={5} value={form.year} onChange={(e) => setForm({ ...form, year: +e.target.value })} placeholder="1" />
            <Input label="Section" value={form.section} onChange={(e) => setForm({ ...form, section: e.target.value })} placeholder="A" />
            <Input label="Student Count" type="number" min={1} value={form.studentCount} onChange={(e) => setForm({ ...form, studentCount: +e.target.value })} />
            <Select label="Preferred Free Day" value={form.preferredFreeDay ?? ''} onChange={(e) => setForm({ ...form, preferredFreeDay: (e.target.value as SchoolDay) || undefined })} options={dayOptions} />
          </div>
        </div>
      </Modal>
    </>
  )
}
