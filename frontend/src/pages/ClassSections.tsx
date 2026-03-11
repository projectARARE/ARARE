import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { classSectionApi, batchApi } from '../services/api'
import type { ClassSection, ClassSectionRequest, Batch } from '../types'

const EMPTY: ClassSectionRequest = { label: '', batchId: 0, size: 30 }

export default function ClassSections() {
  const [items, setItems] = useState<ClassSection[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ClassSection | null>(null)
  const [form, setForm] = useState<ClassSectionRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

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
    setError(null)
    setOpen(true)
  }

  const openEdit = (s: ClassSection) => {
    setEditing(s)
    setForm({ label: s.label, batchId: s.batchId, size: s.size })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.label.trim()) { setError('Label is required'); return }
    if (!form.batchId) { setError('Please select a batch'); return }
    setSaving(true)
    try {
      if (editing) {
        await classSectionApi.update(editing.id, form)
      } else {
        await classSectionApi.create(form)
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
    if (!window.confirm('Delete this section?')) return
    try {
      await classSectionApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const batchOptions = batches.map((b) => ({
    value: b.id,
    label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
  }))

  const columns: Column<ClassSection>[] = [
    { key: 'label', header: 'Section', render: (s) => <span className="font-medium">{s.label}</span> },
    { key: 'batch', header: 'Batch', render: (s) => `Batch #${s.batchId}` },
    { key: 'size', header: 'Size', render: (s) => s.size },
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
      <Card title="Class Sections" description="Lab sub-groups within a batch"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Section</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(s) => s.id} />
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
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          <Input label="Section Label" value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} placeholder="A" helpText="Label for this lab sub-section (e.g. A, B, Lab-1)" />
          <Select label="Batch" value={form.batchId} onChange={(e) => setForm({ ...form, batchId: +e.target.value })} options={batchOptions} placeholder="Select batch…" />
          <Input label="Size" type="number" min={1} value={form.size} onChange={(e) => setForm({ ...form, size: +e.target.value })} />
        </div>
      </Modal>
    </>
  )
}
