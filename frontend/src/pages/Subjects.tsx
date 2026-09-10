import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge, ConfirmDialog, SearchableSelect, FilterPanel } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { subjectApi, departmentApi, instituteApi } from '../services/api'
import type { Subject, SubjectRequest, Department, Institute, LabSubtype, RoomType } from '../types'
import { useToast } from '../contexts/ToastContext'
import { useBulkDelete } from '../hooks/useBulkDelete'

const LAB_SUBTYPES: LabSubtype[] = [
  'COMPUTER_LAB', 'ELECTRONICS_LAB', 'CHEMISTRY_LAB', 'PHYSICS_LAB',
  'MECHANICAL_LAB', 'CIVIL_LAB', 'NETWORK_LAB', 'GENERAL_LAB',
]

const ROOM_TYPE_OPTIONS: { value: RoomType; label: string }[] = [
  { value: 'LECTURE', label: 'Lecture Hall' },
  { value: 'LAB', label: 'Laboratory' },
]

const EMPTY: SubjectRequest = {
  name: '',
  code: '',
  departmentId: undefined,
  weeklyHours: 4,
  chunkHours: 1,
  roomTypeRequired: 'LECTURE',
  isLab: false,
  requiresTeacher: true,
  requiresRoom: true,
  minGapBetweenSessions: 0,
  maxSessionsPerDay: 1,
}

export default function Subjects() {
  const { toast } = useToast()
  const [items, setItems] = useState<Subject[]>([])
  const [depts, setDepts] = useState<Department[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Subject | null>(null)
  const [form, setForm] = useState<SubjectRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [instituteFilter, setInstituteFilter] = useState<number | null>(null)
  const [departmentFilter, setDepartmentFilter] = useState<number | null>(null)
  const [levelFilter, setLevelFilter] = useState<string | null>(null)
  const [roomTypeFilter, setRoomTypeFilter] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.allSettled([subjectApi.getAll(), departmentApi.getAll(), instituteApi.getAll()])
      .then(([subs, d, i]) => {
        if (subs.status === 'fulfilled') setItems(subs.value)
        if (d.status === 'fulfilled') setDepts(d.value)
        if (i.status === 'fulfilled') setInstitutes(i.value)
        const failed = [subs, d, i].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some subject data failed to refresh (${failed}/3)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, departmentId: depts[0]?.id })
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
      isLab: s.isLab || s.roomTypeRequired === 'LAB',
      requiresTeacher: s.requiresTeacher,
      requiresRoom: s.requiresRoom,
      minGapBetweenSessions: s.minGapBetweenSessions,
      maxSessionsPerDay: s.maxSessionsPerDay,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    if (!form.code.trim()) { toast.error('Code is required'); return }
    setSaving(true)
    try {
      const data: SubjectRequest = {
        ...form,
        labSubtypeRequired: form.isLab ? form.labSubtypeRequired : undefined,
        roomTypeRequired: form.isLab ? 'LAB' : (form.roomTypeRequired ?? 'LECTURE'),
      }
      if (editing) {
        const updated = await subjectApi.update(editing.id, data)
        setItems((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
        toast.success('Subject updated')
      } else {
        const created = await subjectApi.create(data)
        setItems((prev) => [created, ...prev])
        toast.success('Subject created')
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
      await subjectApi.delete(confirmId)
      setItems((prev) => prev.filter((s) => s.id !== confirmId))
      toast.success('Subject deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const deptOptions = [
    { value: 0, label: '— Institute-wide (no department) —' },
    ...depts.map((d) => ({ value: d.id, label: d.name })),
  ]
  const subtypeOptions = LAB_SUBTYPES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))

  const deptFilterOptions = depts
    .filter((d) => !instituteFilter || d.instituteId === instituteFilter)
    .map((d) => ({ value: d.id, label: d.name }))
  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))

  const levelFilterOptions = [
    { value: 'LAB', label: 'Lab' },
    { value: 'THEORY', label: 'Theory' },
  ]

  const filteredItems = items.filter((s) => {
    if (instituteFilter != null && s.instituteId !== instituteFilter) return false
    if (departmentFilter != null && s.departmentId !== departmentFilter) return false
    if (levelFilter === 'LAB' && !s.isLab) return false
    if (levelFilter === 'THEORY' && s.isLab) return false
    if (roomTypeFilter != null && s.roomTypeRequired !== roomTypeFilter) return false
    return true
  })

  const columns: Column<Subject>[] = [
    {
      key: 'name', header: 'Subject',
      sortValue: (s) => s.name,
      render: (s) => (
        <div>
          <p className="font-medium">{s.name}</p>
          <p className="text-xs text-gray-500">{s.code}</p>
        </div>
      ),
    },
    {
      key: 'dept', header: 'Department',
      sortValue: (s) => s.departmentName ?? '',
      render: (s) => s.departmentName ?? (s.departmentId ? `#${s.departmentId}` : <Badge label="Institute-wide" variant="green" />),
    },
    {
      key: 'institute', header: 'Institute',
      sortValue: (s) => institutes.find((i) => i.id === s.instituteId)?.name ?? '',
      render: (s) => {
        const inst = institutes.find((i) => i.id === s.instituteId)
        return inst ? <span className="text-gray-600">{inst.name}</span> : <span className="text-gray-400">—</span>
      },
    },
    { key: 'hours', header: 'Weekly / Chunk', render: (s) => `${s.weeklyHours} slots / ${s.chunkHours} slots` },
    {
      key: 'lab', header: 'Type',
      render: (s) => s.isLab ? <Badge label="Lab" variant="purple" /> : <Badge label="Lecture" variant="blue" />,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} className="px-1.5" onClick={() => openEdit(s)} title="Edit" aria-label="Edit" />
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700 px-1.5" onClick={() => setConfirmId(s.id)} title="Delete" aria-label="Delete" />
        </div>
      ),
    },
  ]

  const getContextItems = (s: Subject): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(s) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(s.id) },
  ]

  const bulk = useBulkDelete<Subject>({
    getKey: (x) => x.id,
    deleteFn: subjectApi.delete,
    reload: load,
    noun: 'subject',
  })

  return (
    <>
      <Card title="Subjects" description="Manage courses and lab sessions"
        actions={<div className="flex items-center gap-2">{bulk.Bar}<Button icon={<Plus size={16} />} onClick={openAdd}>Add Subject</Button></div>}
      >
        <FilterPanel activeCount={(instituteFilter != null ? 1 : 0) + (departmentFilter != null ? 1 : 0) + (levelFilter != null ? 1 : 0) + (roomTypeFilter != null ? 1 : 0)} persistKey="arare.subjects.filters.open">
          <div className="flex items-center gap-3">
            {institutes.length > 0 && (
              <SearchableSelect
                label="Institute"
                value={instituteFilter}
                onChange={(v) => { setInstituteFilter(v == null ? null : +v); setDepartmentFilter(null) }}
                options={instituteOptions}
                placeholder="All institutes"
                allowClear
                className="w-72"
              />
            )}
            {depts.length > 0 && (
              <SearchableSelect
                label="Department"
                value={departmentFilter}
                onChange={(v) => setDepartmentFilter(v == null ? null : +v)}
                options={deptFilterOptions}
                placeholder="All departments"
                allowClear
                className="w-72"
              />
            )}
            <Select
              label="Level"
              value={levelFilter ?? ''}
              onChange={(e) => setLevelFilter(e.target.value || null)}
              options={[{ value: '', label: 'All' }, ...levelFilterOptions]}
            />
            <Select
              label="Room type"
              value={roomTypeFilter ?? ''}
              onChange={(e) => setRoomTypeFilter(e.target.value || null)}
              options={[{ value: '', label: 'All room types' }, ...ROOM_TYPE_OPTIONS]}
            />
          </div>
        </FilterPanel>
        <Table
          columns={columns}
          data={filteredItems}
          loading={loading}
          keyExtractor={(s) => s.id}
          searchable
          exportable
          exportFilename="subjects"
          searchKeys={[(s) => s.name, (s) => s.code, (s) => s.departmentName ?? '']}
          onRowContextMenu={getContextItems}
          selectable={bulk.selectable}
          onSelectionChange={bulk.onSelectionChange}
          clearSignal={bulk.clearSignal}
          densityStorageKey="arare.subjects.density"
        />
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
          <div className="grid grid-cols-2 gap-4">
            <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Data Structures" />
            <Input label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="CS301" />
            <SearchableSelect
              label="Department"
              value={form.departmentId ?? null}
              onChange={(v) => setForm({ ...form, departmentId: v == null ? undefined : +v })}
              options={deptOptions}
              helpText="Institute-wide subjects can be offered to any batch via Subject Offerings"
              allowClear
            />
            <Select
              label="Room Type Required"
              value={form.isLab ? 'LAB' : (form.roomTypeRequired ?? 'LECTURE')}
              onChange={(e) => {
                const nextRoomType = e.target.value as RoomType
                setForm({
                  ...form,
                  roomTypeRequired: nextRoomType,
                  isLab: nextRoomType === 'LAB',
                  labSubtypeRequired: nextRoomType === 'LAB' ? form.labSubtypeRequired : undefined,
                })
              }}
              options={ROOM_TYPE_OPTIONS}
              helpText="Type of room this subject needs"
            />
            <Input label="Weekly Slot Units" type="number" min={1} value={form.weeklyHours} onChange={(e) => setForm({ ...form, weeklyHours: +e.target.value })} />
            <Input label="Chunk Size (slots per session)" type="number" min={1} value={form.chunkHours} onChange={(e) => setForm({ ...form, chunkHours: +e.target.value })} />
            <Input label="Max Sessions / Day" type="number" min={1} value={form.maxSessionsPerDay} onChange={(e) => setForm({ ...form, maxSessionsPerDay: +e.target.value })} />
            <Input
              label="Min Gap Between Sessions (slots)"
              type="number"
              min={0}
              value={form.minGapBetweenSessions ?? 0}
              onChange={(e) => setForm({ ...form, minGapBetweenSessions: +e.target.value })}
              helpText="Minimum timeslots between two sessions of this subject"
            />
          </div>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 text-sm cursor-pointer">
              <input
                type="checkbox"
                checked={form.isLab}
                onChange={(e) => setForm({
                  ...form,
                  isLab: e.target.checked,
                  roomTypeRequired: e.target.checked ? 'LAB' : 'LECTURE',
                  labSubtypeRequired: undefined,
                })}
              />
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

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Subject"
        message="This will remove the subject and clear related session assignments. This cannot be undone."
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
