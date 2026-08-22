import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog, SearchableSelect, MultiSelect } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { teacherAssignmentApi, teacherApi, subjectApi, batchApi, classSectionApi } from '../services/api'
import type { TeacherAssignment, TeacherAssignmentRequest, Teacher, Subject, Batch, ClassSection } from '../types'
import { useToast } from '../contexts/ToastContext'

type ScopeMode = 'batch' | 'section'

const EMPTY: TeacherAssignmentRequest = {
  teacherId: 0,
  subjectId: 0,
  batchId: 0,
  weeklyHours: undefined,
  priority: 1,
  notes: '',
}

export default function TeacherAssignments() {
  const { toast } = useToast()
  const [items, setItems] = useState<TeacherAssignment[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [sections, setSections] = useState<ClassSection[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<TeacherAssignment | null>(null)
  const [form, setForm] = useState<TeacherAssignmentRequest & { sectionIds: number[] }>({ ...EMPTY, sectionIds: [] })
  const [scopeMode, setScopeMode] = useState<ScopeMode>('batch')
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.allSettled([
      teacherAssignmentApi.getAll(),
      teacherApi.getAll(),
      subjectApi.getAll(),
      batchApi.getAll(),
      classSectionApi.getAll(),
    ])
      .then(([a, t, s, b, cs]) => {
        if (a.status === 'fulfilled') setItems(a.value)
        if (t.status === 'fulfilled') setTeachers(t.value)
        if (s.status === 'fulfilled') setSubjects(s.value)
        if (b.status === 'fulfilled') setBatches(b.value)
        if (cs.status === 'fulfilled') setSections(cs.value)
        const failed = [a, t, s, b, cs].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some assignment data failed to refresh (${failed}/5)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setScopeMode('batch')
    setForm({ ...EMPTY, teacherId: teachers[0]?.id ?? 0, subjectId: subjects[0]?.id ?? 0, sectionIds: [] })
    setOpen(true)
  }

  const openEdit = (a: TeacherAssignment) => {
    setEditing(a)
    const isSection = a.sectionId != null
    setScopeMode(isSection ? 'section' : 'batch')
    setForm({
      teacherId: a.teacherId,
      subjectId: a.subjectId,
      batchId: a.batchId ?? (a.sectionId ? sections.find(s => s.id === a.sectionId)?.batchId ?? 0 : 0),
      sectionId: a.sectionId,
      sectionIds: a.sectionId ? [a.sectionId] : [],
      weeklyHours: a.weeklyHours,
      priority: a.priority,
      notes: a.notes ?? '',
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.teacherId) { toast.error('Please select a teacher'); return }
    if (!form.subjectId) { toast.error('Please select a subject'); return }
    if (scopeMode === 'section' && form.sectionIds.length === 0) { toast.error('Please select at least one section'); return }
    if (scopeMode === 'batch' && !form.batchId) { toast.error('Please select a batch'); return }
    
    const payload: TeacherAssignmentRequest = {
      teacherId: form.teacherId,
      subjectId: form.subjectId,
      weeklyHours: form.weeklyHours,
      priority: form.priority ?? 1,
      notes: form.notes?.trim() || undefined,
    }

    setSaving(true)
    try {
      if (editing) {
        if (scopeMode === 'section') payload.sectionId = form.sectionIds[0]
        else payload.batchId = form.batchId
        const updated = await teacherAssignmentApi.update(editing.id, payload)
        setItems((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
        toast.success('Assignment updated')
      } else {
        if (scopeMode === 'batch') {
          payload.batchId = form.batchId
          const created = await teacherAssignmentApi.create(payload)
          setItems((prev) => [created, ...prev])
        } else {
          const promises = form.sectionIds.map((sid) => 
            teacherAssignmentApi.create({ ...payload, sectionId: sid })
          )
          const createdList = await Promise.all(promises)
          setItems((prev) => [...createdList, ...prev])
        }
        toast.success('Assignment(s) created')
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
      await teacherAssignmentApi.delete(confirmId)
      setItems((prev) => prev.filter((a) => a.id !== confirmId))
      toast.success('Assignment deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const teacherOptions = teachers.map((t) => ({ value: t.id, label: t.name }))
  const subjectOptions = subjects.map((s) => ({ value: s.id, label: s.name }))
  const batchOptions = batches.map((b) => ({
    value: b.id,
    label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
  }))
  const sectionOptions = sections.map((s) => ({
    value: s.id,
    label: `${s.label} — ${s.batchName ?? `Batch #${s.batchId}`}`,
  }))

  const columns: Column<TeacherAssignment>[] = [
    {
      key: 'teacher', header: 'Teacher',
      sortValue: (a) => a.teacherName ?? '',
      render: (a) => <span className="font-medium">{a.teacherName ?? `Teacher #${a.teacherId}`}</span>,
    },
    {
      key: 'subject', header: 'Subject',
      sortValue: (a) => a.subjectName ?? '',
      render: (a) => <span>{a.subjectName ?? `Subject #${a.subjectId}`}</span>,
    },
    {
      key: 'scope', header: 'Scope',
      render: (a) =>
        a.sectionLabel
          ? <span>Section {a.sectionLabel}</span>
          : <span>Batch {a.batchLabel ?? `#${a.batchId}`}</span>,
    },
    { key: 'hours', header: 'Hours', render: (a) => a.weeklyHours ?? '—' },
    { key: 'priority', header: 'Priority', render: (a) => `P${a.priority}` },
    { key: 'notes', header: 'Notes', render: (a) => a.notes ? <span className="text-gray-500">{a.notes}</span> : '—' },
    {
      key: 'actions', header: '', width: '96px',
      render: (a) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(a)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(a.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (a: TeacherAssignment): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(a) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(a.id) },
  ]

  return (
    <>
      <Card
        title="Teacher Assignments"
        description="Term teaching allotments — who teaches which subject for a batch or lab section"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Assignment</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(a) => a.id}
          searchable
          exportable
          exportFilename="teacher-assignments"
          searchKeys={[(a) => a.teacherName ?? '', (a) => a.subjectName ?? '', (a) => a.batchLabel ?? '', (a) => a.sectionLabel ?? '']}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Assignment' : 'Add Assignment'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <SearchableSelect label="Teacher" value={form.teacherId || null} onChange={(v) => setForm({ ...form, teacherId: v == null ? 0 : +v })} options={teacherOptions} placeholder="Select teacher…" allowClear />
            <SearchableSelect label="Subject" value={form.subjectId || null} onChange={(v) => setForm({ ...form, subjectId: v == null ? 0 : +v })} options={subjectOptions} placeholder="Select subject…" allowClear />
          </div>

          <div className={`grid ${scopeMode === 'section' ? 'grid-cols-3' : 'grid-cols-2'} gap-4`}>
            <Select
              label="Scope"
              value={scopeMode}
              onChange={(e) => {
                const mode = e.target.value as ScopeMode
                setScopeMode(mode)
                setForm((f) => ({ ...f, batchId: 0, sectionIds: [] }))
              }}
              options={[
                { value: 'batch', label: 'Whole batch' },
                { value: 'section', label: 'Lab section' },
              ]}
            />
            {scopeMode === 'batch' ? (
              <SearchableSelect label="Batch" value={form.batchId || null} onChange={(v) => setForm({ ...form, batchId: v == null ? 0 : +v })} options={batchOptions} placeholder="Select batch…" allowClear />
            ) : (
              <>
                <SearchableSelect label="Batch" value={form.batchId || null} onChange={(v) => setForm({ ...form, batchId: v == null ? 0 : +v, sectionIds: [] })} options={batchOptions} placeholder="Select batch first…" allowClear />
                {form.batchId ? (
                  editing ? (
                    <SearchableSelect label="Section" value={form.sectionIds[0] ?? null} onChange={(v) => setForm({ ...form, sectionIds: v == null ? [] : [+v] })} options={sectionOptions.filter(s => sections.find(sec => sec.id === s.value)?.batchId === form.batchId)} placeholder="Select section…" allowClear />
                  ) : (
                    <MultiSelect
                      label="Sections"
                      options={sectionOptions.filter(s => sections.find(sec => sec.id === s.value)?.batchId === form.batchId)}
                      selected={form.sectionIds}
                      onChange={(next) => setForm({ ...form, sectionIds: next })}
                      placeholder="Select sections…"
                    />
                  )
                ) : null}
              </>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Input label="Weekly Hours" type="number" min={1} value={form.weeklyHours ?? ''} onChange={(e) => setForm({ ...form, weeklyHours: +e.target.value || undefined })} placeholder="Optional" />
            <Input label="Priority" type="number" min={1} max={5} value={form.priority ?? 1} onChange={(e) => setForm({ ...form, priority: +e.target.value })} placeholder="1" helpText="Higher priority pins the teacher first" />
          </div>

          <Input label="Notes" value={form.notes ?? ''} onChange={(e) => setForm({ ...form, notes: e.target.value })} placeholder="Optional notes" />
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Assignment"
        message="This will remove the teacher allotment. Sessions already generated will keep their assigned teachers until regenerated."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
