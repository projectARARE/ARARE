import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2, CheckCircle, Clock } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { academicTermApi } from '../services/api'
import type { AcademicTerm, AcademicTermRequest, AcademicTermStatus } from '../types'
import { useToast } from '../contexts/ToastContext'

const STATUS_OPTIONS: { value: AcademicTermStatus; label: string }[] = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'ARCHIVED', label: 'Archived' },
]

const STATUS_COLORS: Record<AcademicTermStatus, string> = {
  UPCOMING: 'text-blue-700 bg-blue-50 border-blue-200',
  ACTIVE: 'text-green-700 bg-green-50 border-green-200',
  CLOSED: 'text-gray-600 bg-gray-50 border-gray-200',
  ARCHIVED: 'text-gray-400 bg-gray-50 border-gray-100',
}

const EMPTY: AcademicTermRequest = {
  name: '',
  academicYear: '',
  startDate: '',
  endDate: '',
  examPeriodStart: '',
  examPeriodEnd: '',
  status: 'UPCOMING',
  description: '',
}

export default function AcademicTerms() {
  const { toast } = useToast()
  const [items, setItems] = useState<AcademicTerm[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<AcademicTerm | null>(null)
  const [form, setForm] = useState<AcademicTermRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    academicTermApi.getAll().then(setItems).catch((e) => toast.error(e instanceof Error ? e.message : 'Load failed')).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (t: AcademicTerm) => {
    setEditing(t)
    setForm({
      name: t.name,
      academicYear: t.academicYear ?? '',
      startDate: t.startDate,
      endDate: t.endDate,
      examPeriodStart: t.examPeriodStart ?? '',
      examPeriodEnd: t.examPeriodEnd ?? '',
      status: t.status,
      description: t.description ?? '',
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    if (!form.startDate) { toast.error('Start date is required'); return }
    if (!form.endDate) { toast.error('End date is required'); return }
    setSaving(true)
    try {
      const payload: AcademicTermRequest = {
        ...form,
        examPeriodStart: form.examPeriodStart || undefined,
        examPeriodEnd: form.examPeriodEnd || undefined,
        academicYear: form.academicYear || undefined,
        description: form.description || undefined,
      }
      if (editing) {
        await academicTermApi.update(editing.id, payload)
        toast.success('Academic term updated')
      } else {
        await academicTermApi.create(payload)
        toast.success('Academic term created')
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
      await academicTermApi.delete(confirmId)
      toast.success('Academic term deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const columns: Column<AcademicTerm>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (t) => t.name,
      render: (t) => (
        <div>
          <span className="font-medium">{t.name}</span>
          {t.academicYear && (
            <span className="ml-2 text-xs text-gray-400">{t.academicYear}</span>
          )}
        </div>
      ),
    },
    {
      key: 'status', header: 'Status',
      sortValue: (t) => t.status,
      render: (t) => (
        <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full border font-medium ${STATUS_COLORS[t.status]}`}>
          {t.status === 'ACTIVE' ? <CheckCircle size={10} /> : <Clock size={10} />}
          {t.status}
        </span>
      ),
    },
    {
      key: 'dates', header: 'Dates',
      render: (t) => (
        <span className="text-sm text-gray-600">
          {t.startDate} → {t.endDate}
        </span>
      ),
    },
    {
      key: 'exam', header: 'Exam Period',
      render: (t) => t.examPeriodStart
        ? <span className="text-sm text-gray-600">{t.examPeriodStart} → {t.examPeriodEnd}</span>
        : <span className="text-gray-400">—</span>,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (t) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(t)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700" onClick={() => setConfirmId(t.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (t: AcademicTerm): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(t) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(t.id) },
  ]

  return (
    <>
      <Card
        title="Academic Terms"
        description="Define semesters, trimesters, and academic years to version your schedules"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Term</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(t) => t.id}
          searchable
          searchKeys={[(t) => t.name, (t) => t.academicYear ?? '', (t) => t.status]}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Academic Term' : 'Add Academic Term'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input label="Name" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="Semester 1 2025–26" required />
          <Input label="Academic Year" value={form.academicYear ?? ''}
            onChange={(e) => setForm({ ...form, academicYear: e.target.value })}
            placeholder="2025-26" helpText="Optional grouping label" />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start Date" type="date" value={form.startDate}
              onChange={(e) => setForm({ ...form, startDate: e.target.value })} required />
            <Input label="End Date" type="date" value={form.endDate}
              onChange={(e) => setForm({ ...form, endDate: e.target.value })} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Exam Period Start" type="date" value={form.examPeriodStart ?? ''}
              onChange={(e) => setForm({ ...form, examPeriodStart: e.target.value })}
              helpText="Optional" />
            <Input label="Exam Period End" type="date" value={form.examPeriodEnd ?? ''}
              onChange={(e) => setForm({ ...form, examPeriodEnd: e.target.value })} />
          </div>
          <Select label="Status" value={form.status ?? 'UPCOMING'}
            onChange={(e) => setForm({ ...form, status: e.target.value as AcademicTermStatus })}
            options={STATUS_OPTIONS} />
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              value={form.description ?? ''}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={2}
              className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Optional notes about this term"
            />
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Academic Term"
        message="This will permanently delete the academic term. Existing schedules linked to it will not be affected."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
