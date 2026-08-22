import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog, MultiSelect, SearchableSelect } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { batchApi, departmentApi, roomApi, subjectApi, instituteApi } from '../services/api'
import type { Batch, BatchRequest, Department, Room, SchoolDay, Subject, Institute } from '../types'
import { useToast } from '../contexts/ToastContext'

const DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const EMPTY: BatchRequest = {
  departmentId: 0,
  year: 1,
  section: 'A',
  studentCount: 60,
  subjectIds: [],
}

export default function Batches() {
  const { toast } = useToast()
  const [items, setItems] = useState<Batch[]>([])
  const [depts, setDepts] = useState<Department[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [instituteFilter, setInstituteFilter] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Batch | null>(null)
  const [form, setForm] = useState<BatchRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.allSettled([batchApi.getAll(), departmentApi.getAll(), roomApi.getAll(), subjectApi.getAll(), instituteApi.getAll()])
      .then(([b, d, r, s, i]) => {
        if (b.status === 'fulfilled') setItems(b.value)
        if (d.status === 'fulfilled') setDepts(d.value)
        if (r.status === 'fulfilled') setRooms(r.value)
        if (s.status === 'fulfilled') setSubjects(s.value)
        if (i.status === 'fulfilled') setInstitutes(i.value)
        const failed = [b, d, r, s, i].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some batch data failed to refresh (${failed}/5)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    const firstDept = depts.find((d) => !instituteFilter || d.instituteId === instituteFilter) ?? depts[0]
    setForm({ ...EMPTY, departmentId: firstDept?.id ?? 0 })
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
      homeRoomId: b.homeRoomId,
      subjectIds: b.subjectIds ?? [],
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.departmentId) { toast.error('Please select a department'); return }
    if (!form.section.trim()) { toast.error('Section is required'); return }
    setSaving(true)
    try {
      if (editing) {
        const updated = await batchApi.update(editing.id, form)
        setItems((prev) => prev.map((b) => (b.id === updated.id ? updated : b)))
        toast.success('Batch updated')
      } else {
        const created = await batchApi.create(form)
        setItems((prev) => [created, ...prev])
        toast.success('Batch created')
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
      await batchApi.delete(confirmId)
      setItems((prev) => prev.filter((b) => b.id !== confirmId))
      toast.success('Batch deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const deptOptions = depts
    .filter((d) => !instituteFilter || d.instituteId === instituteFilter)
    .map((d) => ({ value: d.id, label: `${d.name}${d.instituteName ? ` (${d.instituteName})` : ''}` }))
  const homeRoomOptions = [
    { value: '', label: '- No home room -' },
    ...rooms.map((r) => ({ value: r.id, label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''} [${r.type}]` })),
  ]
  const dayOptions = [
    { value: '', label: 'No preference' },
    ...DAYS.map((d) => ({ value: d, label: d })),
  ]
  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))
  const visibleItems = instituteFilter
    ? items.filter((b) => b.instituteId === instituteFilter)
    : items

  const columns: Column<Batch>[] = [
    {
      key: 'batch', header: 'Batch',
      sortValue: (b) => `${b.departmentName ?? ''} ${b.year} ${b.section}`,
      render: (b) => (
        <div>
          <p className="font-medium">Year {b.year} – {b.section}</p>
          <p className="text-xs text-gray-500">{b.departmentName ?? `Dept #${b.departmentId}`}</p>
        </div>
      ),
    },
    { key: 'year', header: 'Year', render: (b) => `Year ${b.year}` },
    { key: 'students', header: 'Students', render: (b) => b.studentCount },
    { key: 'homeRoom', header: 'Home Room', render: (b) => b.homeRoomNumber ?? '—' },
    { key: 'freeDay', header: 'Free Day', render: (b) => b.preferredFreeDay ?? '—' },
    {
      key: 'curriculum', header: 'Curriculum',
      render: (b) =>
        b.subjectNames && b.subjectNames.length > 0
          ? <span className="text-gray-700">{b.subjectNames.join(', ')}</span>
          : <span className="text-gray-400">—</span>,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (b) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(b)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(b.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (b: Batch): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(b) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(b.id) },
  ]

  return (
    <>
      <Card
        title="Batches"
        description="Manage student batches (classes)"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Batch</Button>}
      >
        <Table
          columns={columns}
          data={visibleItems}
          loading={loading}
          keyExtractor={(b) => b.id}
          searchable
          exportable
          exportFilename="batches"
          searchKeys={[(b) => b.section, (b) => b.departmentName ?? '', (b) => `Year ${b.year}`]}
          onRowContextMenu={getContextItems}
        />
        <div className="mt-3 flex items-center justify-between gap-3">
          <p className="text-xs text-gray-500">{visibleItems.length} batch{visibleItems.length === 1 ? '' : 'es'}</p>
          {institutes.length > 0 && (
            <SearchableSelect
              label="Institute filter"
              value={instituteFilter}
              onChange={(v) => setInstituteFilter(v == null ? null : +v)}
              options={instituteOptions}
              placeholder="All institutes"
              allowClear
              className="w-72"
            />
          )}
        </div>
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
          <div className="grid grid-cols-2 gap-4">
            <SearchableSelect label="Department" value={form.departmentId || null} onChange={(v) => setForm({ ...form, departmentId: v == null ? 0 : +v })} options={deptOptions} placeholder="Select department…" allowClear />
            <Input label="Year" type="number" min={1} max={5} value={form.year} onChange={(e) => setForm({ ...form, year: +e.target.value })} placeholder="1" />
            <Input label="Section" value={form.section} onChange={(e) => setForm({ ...form, section: e.target.value })} placeholder="A" />
            <Input label="Student Count" type="number" min={1} value={form.studentCount} onChange={(e) => setForm({ ...form, studentCount: +e.target.value })} />
            <SearchableSelect label="Home Room" value={form.homeRoomId ?? null} onChange={(v) => setForm({ ...form, homeRoomId: v == null ? undefined : +v })} options={homeRoomOptions} placeholder="Select home room…" helpText="Optional: a room the batch always occupies for lectures" allowClear />
            <Select label="Preferred Free Day" value={form.preferredFreeDay ?? ''} onChange={(e) => setForm({ ...form, preferredFreeDay: (e.target.value as SchoolDay) || undefined })} options={dayOptions} />
          </div>

          <MultiSelect
            label="Curriculum"
            options={subjects.map((s) => ({ value: s.id, label: s.name }))}
            selected={form.subjectIds ?? []}
            onChange={(ids) => setForm({ ...form, subjectIds: ids })}
            placeholder="Search and select subjects…"
          />

          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">
              Working Days <span className="font-normal text-gray-500">(uncheck to exclude specific days)</span>
            </p>
            <div className="flex flex-wrap gap-3">
              {DAYS.map((day) => (
                <label key={day} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={(form.workingDays ?? DAYS).includes(day)}
                    onChange={() => {
                      const current = form.workingDays ?? DAYS
                      setForm({ ...form, workingDays: current.includes(day) ? current.filter((d) => d !== day) : [...current, day] })
                    }}
                  />
                  {day.charAt(0) + day.slice(1).toLowerCase()}
                </label>
              ))}
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Batch"
        message="This will remove the batch and all associated class sections and sessions. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
