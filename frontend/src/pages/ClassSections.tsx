import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { classSectionApi, batchApi } from '../services/api'
import type { ClassSection, ClassSectionRequest, Batch } from '../types'
import { useToast } from '../contexts/ToastContext'

const EMPTY: ClassSectionRequest = { label: '', batchId: 0, size: 30 }

export default function ClassSections() {
  const { toast } = useToast()
  const [items, setItems] = useState<ClassSection[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ClassSection | null>(null)
  const [form, setForm] = useState<ClassSectionRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([classSectionApi.getAll(), batchApi.getAll()])
      .then(([s, b]) => { setItems(s); setBatches(b) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, batchId: batches[0]?.id ?? 0 })
    setOpen(true)
  }

  const openEdit = (s: ClassSection) => {
    setEditing(s)
    setForm({ label: s.label, batchId: s.batchId, size: s.size })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.label.trim()) { toast.error('Label is required'); return }
    if (!form.batchId) { toast.error('Please select a batch'); return }
    setSaving(true)
    try {
      if (editing) {
        await classSectionApi.update(editing.id, form)
        toast.success('Section updated')
      } else {
        await classSectionApi.create(form)
        toast.success('Section created')
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
      await classSectionApi.delete(confirmId)
      toast.success('Section deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const batchOptions = batches.map((b) => ({
    value: b.id,
    label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
  }))

  const columns: Column<ClassSection>[] = [
    {
      key: 'label', header: 'Section',
      sortValue: (s) => s.label,
      render: (s) => <span className="font-medium">{s.label}</span>,
    },
    {
      key: 'batch', header: 'Batch',
      sortValue: (s) => s.batchName ?? '',
      render: (s) => s.batchName ?? `Batch #${s.batchId}`,
    },
    { key: 'size', header: 'Size', render: (s) => s.size },
    {
      key: 'actions', header: '', width: '96px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(s)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(s.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (s: ClassSection): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(s) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(s.id) },
  ]

  return (
    <>
      <Card title="Class Sections" description="Lab sub-groups within a batch"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Section</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(s) => s.id}
          searchable
          searchKeys={[(s) => s.label, (s) => s.batchName ?? '']}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit Section' : 'Add Section'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input label="Section Label" value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} placeholder="A" helpText="Label for this lab sub-section (e.g. A, B, Lab-1)" />
          <Select label="Batch" value={form.batchId} onChange={(e) => setForm({ ...form, batchId: +e.target.value })} options={batchOptions} placeholder="Select batch…" />
          <Input label="Size" type="number" min={1} value={form.size} onChange={(e) => setForm({ ...form, size: +e.target.value })} />
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Section"
        message="This will remove the class section and clear related session assignments. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
