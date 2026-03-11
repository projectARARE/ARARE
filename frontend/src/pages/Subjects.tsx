import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { subjectApi, departmentApi } from '../services/api'
import type { Subject, SubjectRequest, Department, LabSubtype } from '../types'

const LAB_SUBTYPES: LabSubtype[] = [
  'COMPUTER_LAB', 'ELECTRONICS_LAB', 'CHEMISTRY_LAB', 'PHYSICS_LAB',
  'MECHANICAL_LAB', 'CIVIL_LAB', 'NETWORK_LAB', 'GENERAL_LAB',
]

const EMPTY: SubjectRequest = {
  name: '',
  code: '',
  departmentId: 0,
  weeklyHours: 4,
  chunkHours: 1,
  isLab: false,
  requiresTeacher: true,
  requiresRoom: true,
  maxSessionsPerDay: 1,
}

export default function Subjects() {
  const [items, setItems] = useState<Subject[]>([])
  const [depts, setDepts] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Subject | null>(null)
  const [form, setForm] = useState<SubjectRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.all([subjectApi.getAll(), departmentApi.getAll()])
      .then(([subs, d]) => { setItems(subs); setDepts(d) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, departmentId: depts[0]?.id ?? 0 })
    setError(null)
    setOpen(true)
  }

  const openEdit = (s: Subject) => {
    setEditing(s)
    setForm({
      name: s.name,
      code: s.code,
      departmentId: s.departmentId,
      weeklyHours: s.weeklyHours,
      chunkHours: s.chunkHours,
      roomTypeRequired: s.roomTypeRequired,
      labSubtypeRequired: s.labSubtypeRequired,
      isLab: s.isLab,
      requiresTeacher: s.requiresTeacher,
      requiresRoom: s.requiresRoom,
      minGapBetweenSessions: s.minGapBetweenSessions,
      maxSessionsPerDay: s.maxSessionsPerDay,
    })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { setError('Name is required'); return }
    if (!form.code.trim()) { setError('Code is required'); return }
    if (!form.departmentId) { setError('Please select a department'); return }
    setSaving(true)
    try {
      const data: SubjectRequest = {
        ...form,
        labSubtypeRequired: form.isLab ? form.labSubtypeRequired : undefined,
      }
      if (editing) {
        await subjectApi.update(editing.id, data)
      } else {
        await subjectApi.create(data)
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
    if (!window.confirm('Delete this subject?')) return
    try {
      await subjectApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const deptOptions = depts.map((d) => ({ value: d.id, label: d.name }))
  const subtypeOptions = LAB_SUBTYPES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))

  const columns: Column<Subject>[] = [
    {
      key: 'name', header: 'Subject',
      render: (s) => (
        <div>
          <p className="font-medium">{s.name}</p>
          <p className="text-xs text-gray-500">{s.code}</p>
        </div>
      ),
    },
    { key: 'dept', header: 'Department', render: (s) => s.departmentName ?? `#${s.departmentId}` },
    { key: 'hours', header: 'Weekly / Chunk', render: (s) => `${s.weeklyHours}h / ${s.chunkHours}h` },
    {
      key: 'lab', header: 'Type',
      render: (s) => s.isLab ? <Badge label="Lab" variant="purple" /> : <Badge label="Lecture" variant="blue" />,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(s)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(s.id)}>Delete</Button>
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
      <Card title="Subjects" description="Manage courses and lab sessions"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Subject</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(s) => s.id} />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit Subject' : 'Add Subject'} size="lg"
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
            <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Data Structures" />
            <Input label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="CS301" />
            <Select label="Department" value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: +e.target.value })} options={deptOptions} placeholder="Select department…" />
            <Input label="Weekly Hours" type="number" min={1} value={form.weeklyHours} onChange={(e) => setForm({ ...form, weeklyHours: +e.target.value })} />
            <Input label="Chunk Hours (per session)" type="number" min={1} value={form.chunkHours} onChange={(e) => setForm({ ...form, chunkHours: +e.target.value })} />
            <Input label="Max Sessions / Day" type="number" min={1} value={form.maxSessionsPerDay} onChange={(e) => setForm({ ...form, maxSessionsPerDay: +e.target.value })} />
          </div>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 text-sm cursor-pointer">
              <input type="checkbox" checked={form.isLab} onChange={(e) => setForm({ ...form, isLab: e.target.checked, labSubtypeRequired: undefined })} />
              Lab Subject
            </label>
            <label className="flex items-center gap-2 text-sm cursor-pointer">
              <input type="checkbox" checked={form.requiresTeacher} onChange={(e) => setForm({ ...form, requiresTeacher: e.target.checked })} />
              Requires Teacher
            </label>
            <label className="flex items-center gap-2 text-sm cursor-pointer">
              <input type="checkbox" checked={form.requiresRoom} onChange={(e) => setForm({ ...form, requiresRoom: e.target.checked })} />
              Requires Room
            </label>
          </div>
          {form.isLab && (
            <Select label="Lab Subtype" value={form.labSubtypeRequired ?? ''} onChange={(e) => setForm({ ...form, labSubtypeRequired: e.target.value as LabSubtype || undefined })} options={subtypeOptions} placeholder="Select lab subtype…" />
          )}
        </div>
      </Modal>
    </>
  )
}
