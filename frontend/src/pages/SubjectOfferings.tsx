import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog, Toggle, SearchableSelect, MultiSelect, FilterPanel } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { subjectOfferingApi, subjectApi, batchApi, classSectionApi, instituteApi, departmentApi } from '../services/api'
import type { SubjectOffering, SubjectOfferingRequest, Subject, Batch, ClassSection, Institute, Department } from '../types'
import { useToast } from '../contexts/ToastContext'
import { useBulkDelete } from '../hooks/useBulkDelete'

type ScopeMode = 'batch' | 'section'

const EMPTY: SubjectOfferingRequest = {
  subjectId: 0,
  batchId: 0,
  weeklyHours: undefined,
  elective: false,
}

export default function SubjectOfferings() {
  const { toast } = useToast()
  const [items, setItems] = useState<SubjectOffering[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [sections, setSections] = useState<ClassSection[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [instituteFilter, setInstituteFilter] = useState<number | null>(null)
  const [departmentFilter, setDepartmentFilter] = useState<number | null>(null)
  const [batchFilter, setBatchFilter] = useState<number | null>(null)
  const [sectionFilter, setSectionFilter] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<SubjectOffering | null>(null)
  const [form, setForm] = useState<SubjectOfferingRequest & { sectionIds: number[] }>({ ...EMPTY, sectionIds: [] })
  const [formDeptId, setFormDeptId] = useState<number | null>(null)
  const [scopeMode, setScopeMode] = useState<ScopeMode>('batch')
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.allSettled([
      subjectOfferingApi.getAll(),
      subjectApi.getAll(),
      batchApi.getAll(),
      classSectionApi.getAll(),
      instituteApi.getAll(),
      departmentApi.getAll(),
    ])
      .then(([o, s, b, cs, i, d]) => {
        if (o.status === 'fulfilled') setItems(o.value)
        if (s.status === 'fulfilled') setSubjects(s.value)
        if (b.status === 'fulfilled') setBatches(b.value)
        if (cs.status === 'fulfilled') setSections(cs.value)
        if (i.status === 'fulfilled') setInstitutes(i.value)
        if (d.status === 'fulfilled') setDepartments(d.value)
        const failed = [o, s, b, cs, i, d].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some offering data failed to refresh (${failed}/6)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setScopeMode('batch')
    const firstSubject = subjects[0]
    const firstBatch = batches.find((b) => (!firstSubject?.departmentId || b.departmentId === firstSubject.departmentId) && (!instituteFilter || b.instituteId === instituteFilter)) ?? batches.find((b) => !instituteFilter || b.instituteId === instituteFilter) ?? batches[0]
    setForm({ ...EMPTY, subjectId: firstSubject?.id ?? 0, batchId: firstBatch?.id ?? 0, sectionIds: [] })
    setFormDeptId(firstSubject?.departmentId ?? null)
    setOpen(true)
  }

  const openEdit = (o: SubjectOffering) => {
    setEditing(o)
    const isSection = o.sectionId != null
    setScopeMode(isSection ? 'section' : 'batch')
    setForm({
      subjectId: o.subjectId,
      batchId: o.batchId ?? (o.sectionId ? sections.find(s => s.id === o.sectionId)?.batchId ?? 0 : 0),
      sectionId: o.sectionId,
      sectionIds: o.sectionId ? [o.sectionId] : [],
      weeklyHours: o.weeklyHours,
      elective: o.elective,
    })
    const subject = subjects.find((s) => s.id === o.subjectId)
    const batch = batches.find((b) => b.id === (o.batchId ?? (o.sectionId ? sections.find(s => s.id === o.sectionId)?.batchId : 0)))
    setFormDeptId(subject?.departmentId ?? batch?.departmentId ?? null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.subjectId) { toast.error('Please select a subject'); return }
    if (scopeMode === 'section' && form.sectionIds.length === 0) { toast.error('Please select at least one section'); return }
    if (scopeMode === 'batch' && !form.batchId) { toast.error('Please select a batch'); return }
    
    const subject = subjects.find((s) => s.id === form.subjectId)
    const batch = batches.find((b) => b.id === form.batchId)
    if (subject?.departmentId != null && batch?.departmentId != null && subject.departmentId !== batch.departmentId) {
      toast.error(`Subject belongs to ${subject.departmentName ?? `dept #${subject.departmentId}`}, batch to ${batch.departmentName ?? `dept #${batch.departmentId}`} — pick matching department/${batch.departmentName ?? `dept #${batch.departmentId}`}`)
      return
    }
    
    const payload: SubjectOfferingRequest = {
      subjectId: form.subjectId,
      weeklyHours: form.weeklyHours,
      elective: form.elective ?? false,
    }

    setSaving(true)
    try {
      if (editing) {
        if (scopeMode === 'section') payload.sectionId = form.sectionIds[0]
        else payload.batchId = form.batchId
        const updated = await subjectOfferingApi.update(editing.id, payload)
        setItems((prev) => prev.map((o) => (o.id === updated.id ? updated : o)))
        toast.success('Offering updated')
      } else {
        if (scopeMode === 'batch') {
          payload.batchId = form.batchId
          const created = await subjectOfferingApi.create(payload)
          setItems((prev) => [created, ...prev])
        } else {
          const promises = form.sectionIds.map((sid) => 
            subjectOfferingApi.create({ ...payload, sectionId: sid })
          )
          const createdList = await Promise.all(promises)
          setItems((prev) => [...createdList, ...prev])
        }
        toast.success('Offering(s) created')
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
      await subjectOfferingApi.delete(confirmId)
      setItems((prev) => prev.filter((o) => o.id !== confirmId))
      toast.success('Offering deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const deptFilterOptions = departments
    .filter((d) => !instituteFilter || d.instituteId === instituteFilter)
    .map((d) => ({ value: d.id, label: d.name }))

  const filteredBatches = batches
    .filter((b) => !instituteFilter || b.instituteId === instituteFilter)
    .filter((b) => !departmentFilter || b.departmentId === departmentFilter)

  const batchFilterOptions = filteredBatches.map((b) => ({
    value: b.id,
    label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
  }))

  const filterSectionOptions = (() => {
    const batchIds = new Set(filteredBatches.map((b) => b.id))
    const seen = new Map<number, string>()
    for (const o of items) {
      const sid = o.sectionId
      if (sid == null) continue
      const bid = o.batchId ?? batches.find((b) => b.id === sections.find((s) => s.id === sid)?.batchId)?.id
      if (bid != null && !batchIds.has(bid)) continue
      if (batchFilter != null && o.batchId !== batchFilter && o.batchId == null) {
        const sec = sections.find((s) => s.id === sid)
        if (sec?.batchId !== batchFilter) continue
      } else if (batchFilter != null && o.batchId !== batchFilter) continue
      if (!seen.has(sid)) seen.set(sid, o.sectionLabel ?? sections.find((s) => s.id === sid)?.label ?? `#${sid}`)
    }
    return Array.from(seen.entries()).map(([value, label]) => ({ value, label }))
  })()

  const formSubjectOptions = subjects
    .filter((s) => formDeptId == null || s.departmentId == null || s.departmentId === formDeptId)
    .map((s) => ({
      value: s.id,
      label: `${s.name}${s.departmentName ? ` — ${s.departmentName}` : ' — Institute-wide'}`,
    }))

  const formBatchOptions = batches
    .filter((b) => formDeptId == null || b.departmentId === formDeptId)
    .map((b) => ({
      value: b.id,
      label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
    }))

  const sectionOptions = sections
    .filter((s) => {
      if (!instituteFilter) return true
      const batch = batches.find((b) => b.id === s.batchId)
      return batch?.instituteId === instituteFilter
    })
    .filter((s) => {
      if (!departmentFilter) return true
      const batch = batches.find((b) => b.id === s.batchId)
      return batch?.departmentId === departmentFilter
    })
    .map((s) => ({
      value: s.id,
      label: `${s.label} — ${s.batchName ?? `Batch #${s.batchId}`}`,
    }))

  const visibleItems = items.filter((o) => {
    if (instituteFilter != null) {
      if (o.batchId) {
        const batch = batches.find((b) => b.id === o.batchId)
        if (batch?.instituteId !== instituteFilter) return false
      } else if (o.sectionId) {
        const section = sections.find((s) => s.id === o.sectionId)
        const batch = section ? batches.find((b) => b.id === section.batchId) : undefined
        if (batch?.instituteId !== instituteFilter) return false
      }
    }
    if (departmentFilter != null) {
      if (o.batchId) {
        const batch = batches.find((b) => b.id === o.batchId)
        if (batch?.departmentId !== departmentFilter) return false
      } else if (o.sectionId) {
        const section = sections.find((s) => s.id === o.sectionId)
        const batch = section ? batches.find((b) => b.id === section.batchId) : undefined
        if (batch?.departmentId !== departmentFilter) return false
      }
    }
    if (batchFilter != null && o.batchId !== batchFilter) return false
    if (sectionFilter != null && o.sectionId !== sectionFilter) return false
    return true
  })
  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))

  const columns: Column<SubjectOffering>[] = [
    {
      key: 'subject', header: 'Subject',
      sortValue: (o) => o.subjectName ?? '',
      render: (o) => (
        <span className="font-medium inline-flex items-center gap-2">
          {o.subjectName ?? `Subject #${o.subjectId}`}
          {o.elective && (
            <span className="inline-flex items-center rounded bg-amber-100 text-amber-700 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide">Elective</span>
          )}
        </span>
      ),
    },
    {
      key: 'scope', header: 'Offered To',
      sortValue: (o) => o.sectionLabel ?? o.batchLabel ?? '',
      render: (o) =>
        o.sectionLabel
          ? <span>Section {o.sectionLabel}</span>
          : <span>Batch {o.batchLabel ?? `#${o.batchId}`}</span>,
    },
    {
      key: 'institute', header: 'Institute',
      sortValue: (o) => {
        const bid = o.batchId ?? (o.sectionId ? sections.find((s) => s.id === o.sectionId)?.batchId : undefined)
        const batch = bid ? batches.find((b) => b.id === bid) : undefined
        return batch?.departmentName ?? ''
      },
      render: (o) => {
        const bid = o.batchId ?? (o.sectionId ? sections.find((s) => s.id === o.sectionId)?.batchId : undefined)
        const batch = bid ? batches.find((b) => b.id === bid) : undefined
        const dept = batch ? departments.find((d) => d.id === batch.departmentId) : undefined
        return dept?.instituteName ? <span className="text-gray-600">{dept.instituteName}</span> : <span className="text-gray-400">—</span>
      },
    },
    {
      key: 'hours', header: 'Weekly Hours',
      sortValue: (o) => o.weeklyHours ?? 0,
      render: (o) => o.weeklyHours ? <span>{o.weeklyHours}</span> : <span className="text-gray-400">catalogue</span>,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (o) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} className="px-1.5" onClick={() => openEdit(o)} title="Edit" aria-label="Edit" />
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700 px-1.5" onClick={() => setConfirmId(o.id)} title="Delete" aria-label="Delete" />
        </div>
      ),
    },
  ]

  const getContextItems = (o: SubjectOffering): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(o) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(o.id) },
  ]

  const bulk = useBulkDelete<SubjectOffering>({
    getKey: (x) => x.id,
    deleteFn: subjectOfferingApi.delete,
    reload: load,
    noun: 'subject offering',
  })

  return (
    <>
      <Card
        title="Subject Offerings"
        description="What is actually taught this term — one subject offered to many batches/sections (electives, shared, institute-wide)"
        actions={<div className="flex items-center gap-2">{bulk.Bar}<Button icon={<Plus size={16} />} onClick={openAdd}>Add Offering</Button></div>}
      >
        {institutes.length > 0 && (
          <FilterPanel activeCount={(instituteFilter != null ? 1 : 0) + (departmentFilter != null ? 1 : 0) + (batchFilter != null ? 1 : 0) + (sectionFilter != null ? 1 : 0)} persistKey="arare.subjectOfferings.filters.open">
            <div className="flex items-center gap-3">
              <SearchableSelect
                label="Institute filter"
                value={instituteFilter}
                onChange={(v) => { setInstituteFilter(v == null ? null : +v); setDepartmentFilter(null); setBatchFilter(null); setSectionFilter(null) }}
                options={instituteOptions}
                placeholder="All institutes"
                allowClear
                className="w-72"
              />
              {deptFilterOptions.length > 0 && (
                <SearchableSelect
                  label="Department filter"
                  value={departmentFilter}
                  onChange={(v) => { setDepartmentFilter(v == null ? null : +v); setBatchFilter(null); setSectionFilter(null) }}
                  options={deptFilterOptions}
                  placeholder="All departments"
                  allowClear
                  className="w-72"
                />
              )}
              {batchFilterOptions.length > 0 && (
                <SearchableSelect
                  label="Batch filter"
                  value={batchFilter}
                  onChange={(v) => { setBatchFilter(v == null ? null : +v); setSectionFilter(null) }}
                  options={batchFilterOptions}
                  placeholder="All batches"
                  allowClear
                  className="w-72"
                />
              )}
              {filterSectionOptions.length > 0 && (
                <SearchableSelect
                  label="Section filter"
                  value={sectionFilter}
                  onChange={(v) => setSectionFilter(v == null ? null : +v)}
                  options={filterSectionOptions}
                  placeholder="All sections"
                  allowClear
                  className="w-72"
                />
              )}
            </div>
          </FilterPanel>
        )}
        <Table
          columns={columns}
          data={visibleItems}
          loading={loading}
          keyExtractor={(o) => o.id}
          searchable
          exportable
          exportFilename="subject-offerings"
          searchKeys={[(o) => o.subjectName ?? '', (o) => o.batchLabel ?? '', (o) => o.sectionLabel ?? '']}
          onRowContextMenu={getContextItems}
          selectable={bulk.selectable}
          onSelectionChange={bulk.onSelectionChange}
          clearSignal={bulk.clearSignal}
          densityStorageKey="arare.offerings.density"
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Offering' : 'Add Offering'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <SearchableSelect label="Subject" value={form.subjectId || null} onChange={(v) => { const id = v == null ? 0 : +v; setForm({ ...form, subjectId: id }); const s = subjects.find((x) => x.id === id); setFormDeptId(s?.departmentId ?? null) }} options={formSubjectOptions} placeholder="Select subject…" helpText="Institute-wide subjects (no department) can be offered to any batch" allowClear />

          <div className={`grid ${scopeMode === 'section' ? 'grid-cols-3' : 'grid-cols-2'} gap-4`}>
            <Select
              label="Offered To"
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
              <SearchableSelect label="Batch" value={form.batchId || null} onChange={(v) => { const id = v == null ? 0 : +v; setForm({ ...form, batchId: id }); const b = batches.find((x) => x.id === id); setFormDeptId(b?.departmentId ?? null) }} options={formBatchOptions} placeholder="Select batch…" allowClear />
            ) : (
              <>
                <SearchableSelect label="Batch" value={form.batchId || null} onChange={(v) => { const id = v == null ? 0 : +v; setForm({ ...form, batchId: id, sectionIds: [] }); const b = batches.find((x) => x.id === id); setFormDeptId(b?.departmentId ?? null) }} options={formBatchOptions} placeholder="Select batch first…" allowClear />
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

          <div className="grid grid-cols-2 gap-4 items-end">
            <Input label="Weekly Hours" type="number" min={1} value={form.weeklyHours ?? ''} onChange={(e) => setForm({ ...form, weeklyHours: +e.target.value || undefined })} placeholder="Use catalogue hours" helpText="Override the subject's weekly contact load for this offering" />
            <div className="pb-1">
              <Toggle label="Elective" checked={form.elective ?? false} onChange={(v) => setForm({ ...form, elective: v })} helpText="Mark as a choice-based / optional subject" />
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Offering"
        message="This removes the subject from this batch/section's curriculum for the term. Existing generated sessions are kept until regenerated."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
      {bulk.Dialog}
    </>
  )
}