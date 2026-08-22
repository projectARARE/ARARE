import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2, Layers } from 'lucide-react'
import { Card, Button, Modal, Input, Table, ConfirmDialog, MultiSelect, SearchableSelect } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { classSectionApi, batchApi, subjectApi, instituteApi, departmentApi } from '../services/api'
import type { ClassSection, ClassSectionRequest, Batch, Subject, Institute, Department } from '../types'
import { useToast } from '../contexts/ToastContext'

const EMPTY: ClassSectionRequest = { label: '', batchId: 0, size: 30, subjectIds: [] }

export default function ClassSections() {
  const { toast } = useToast()
  const [items, setItems] = useState<ClassSection[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [instituteFilter, setInstituteFilter] = useState<number | null>(null)
  const [departmentFilter, setDepartmentFilter] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ClassSection | null>(null)
  const [form, setForm] = useState<ClassSectionRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [bulkForm, setBulkForm] = useState({ batchId: 0, prefix: '', count: 10, size: 30 })
  const [bulkSaving, setBulkSaving] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.allSettled([classSectionApi.getAll(), batchApi.getAll(), subjectApi.getAll(), instituteApi.getAll(), departmentApi.getAll()])
      .then(([s, b, su, i, d]) => {
        if (s.status === 'fulfilled') setItems(s.value)
        if (b.status === 'fulfilled') setBatches(b.value)
        if (su.status === 'fulfilled') setSubjects(su.value)
        if (i.status === 'fulfilled') setInstitutes(i.value)
        if (d.status === 'fulfilled') setDepartments(d.value)
        const failed = [s, b, su, i, d].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some section data failed to refresh (${failed}/5)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, batchId: filteredBatches[0]?.id ?? 0 })
    setOpen(true)
  }

  const openEdit = (s: ClassSection) => {
    setEditing(s)
    setForm({ label: s.label, batchId: s.batchId, size: s.size, subjectIds: s.subjectIds ?? [] })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.label.trim()) { toast.error('Label is required'); return }
    if (!form.batchId) { toast.error('Please select a batch'); return }
    setSaving(true)
    try {
      if (editing) {
        const updated = await classSectionApi.update(editing.id, form)
        setItems((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
        toast.success('Section updated')
      } else {
        const created = await classSectionApi.create(form)
        setItems((prev) => [created, ...prev])
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
      setItems((prev) => prev.filter((x) => x.id !== confirmId))
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

  const openBulk = () => {
    setBulkForm({ batchId: filteredBatches[0]?.id ?? 0, prefix: '', count: 10, size: 30 })
    setBulkOpen(true)
  }

  const handleBulkSave = async () => {
    if (!bulkForm.batchId) { toast.error('Please select a batch'); return }
    if (!bulkForm.prefix.trim()) { toast.error('Section prefix is required (e.g. CSE)'); return }
    setBulkSaving(true)
    try {
      const created = await classSectionApi.createMany({
        batchId: bulkForm.batchId,
        prefix: bulkForm.prefix.trim(),
        count: bulkForm.count,
        size: bulkForm.size,
      })
      setItems((prev) => [...created, ...prev])
      toast.success(`Generated ${created.length} section${created.length === 1 ? '' : 's'}`)
      setBulkOpen(false)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setBulkSaving(false)
    }
  }

  const filteredBatches = batches.filter((b) => {
    if (instituteFilter && b.instituteId !== instituteFilter) return false
    if (departmentFilter && b.departmentId !== departmentFilter) return false
    return true
  })

  const batchOptions = filteredBatches.map((b) => ({
    value: b.id,
    label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
  }))

  const visibleItems = items.filter((s) => {
    const batch = batches.find((b) => b.id === s.batchId)
    if (!batch) return true
    if (instituteFilter && batch.instituteId !== instituteFilter) return false
    if (departmentFilter && batch.departmentId !== departmentFilter) return false
    return true
  })

  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))
  const departmentOptions = departments
    .filter((d) => !instituteFilter || d.instituteId === instituteFilter)
    .map((d) => ({ value: d.id, label: d.name }))

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
      key: 'curriculum', header: 'Curriculum',
      render: (s) =>
        s.subjectNames && s.subjectNames.length > 0
          ? <span className="text-gray-700">{s.subjectNames.join(', ')}</span>
          : <span className="text-gray-400">Inherit from batch</span>,
    },
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
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" icon={<Layers size={16} />} onClick={openBulk}>Generate Sections</Button>
            <Button icon={<Plus size={16} />} onClick={openAdd}>Add Section</Button>
          </div>
        }
      >
        <Table
          columns={columns}
          data={visibleItems}
          loading={loading}
          keyExtractor={(s) => s.id}
          searchable
          exportable
          exportFilename="class-sections"
          searchKeys={[(s) => s.label, (s) => s.batchName ?? '']}
          onRowContextMenu={getContextItems}
        />
        <div className="mt-3 flex items-center justify-between gap-3 flex-wrap">
          <p className="text-xs text-gray-500">{visibleItems.length} section{visibleItems.length === 1 ? '' : 's'}</p>
          <div className="flex items-center gap-3">
            {institutes.length > 0 && (
              <SearchableSelect
                label="Institute filter"
                value={instituteFilter}
                onChange={(v) => { setInstituteFilter(v == null ? null : +v); setDepartmentFilter(null) }}
                options={instituteOptions}
                placeholder="All institutes"
                allowClear
                className="w-64"
              />
            )}
            {instituteFilter != null && departmentOptions.length > 0 && (
              <SearchableSelect
                label="Department filter"
                value={departmentFilter}
                onChange={(v) => setDepartmentFilter(v == null ? null : +v)}
                options={departmentOptions}
                placeholder="All departments"
                allowClear
                className="w-64"
              />
            )}
          </div>
        </div>
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
          <SearchableSelect label="Batch" value={form.batchId || null} onChange={(v) => setForm({ ...form, batchId: v == null ? 0 : +v })} options={batchOptions} placeholder="Select batch…" allowClear />
          <Input label="Size" type="number" min={1} value={form.size} onChange={(e) => setForm({ ...form, size: +e.target.value })} />
          <MultiSelect
            label="Curriculum Override"
            options={subjects.map((s) => ({ value: s.id, label: s.name }))}
            selected={form.subjectIds ?? []}
            onChange={(ids) => setForm({ ...form, subjectIds: ids })}
            placeholder="Search and select subjects…"
          />
        </div>
      </Modal>

      <Modal open={bulkOpen} onClose={() => setBulkOpen(false)} title="Generate Sections"
        footer={
          <>
            <Button variant="secondary" onClick={() => setBulkOpen(false)}>Cancel</Button>
            <Button loading={bulkSaving} onClick={handleBulkSave}>Generate</Button>
          </>
        }
      >
        <div className="space-y-4">
          <SearchableSelect label="Batch" value={bulkForm.batchId || null} onChange={(v) => setBulkForm({ ...bulkForm, batchId: v == null ? 0 : +v })} options={batchOptions} placeholder="Select batch…" allowClear />
          <Input label="Section Prefix" value={bulkForm.prefix} onChange={(e) => setBulkForm({ ...bulkForm, prefix: e.target.value })} placeholder="CSE" helpText="Sections will be named PREFIX1, PREFIX2, ... (e.g. CSE1, CSE2)" />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Count" type="number" min={1} max={100} value={bulkForm.count} onChange={(e) => setBulkForm({ ...bulkForm, count: Math.max(1, Math.min(100, +e.target.value || 1)) })} helpText="How many sections to generate" />
            <Input label="Size" type="number" min={1} value={bulkForm.size} onChange={(e) => setBulkForm({ ...bulkForm, size: Math.max(1, +e.target.value || 1) })} helpText="Students per section" />
          </div>
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
