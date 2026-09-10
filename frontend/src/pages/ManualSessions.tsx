import { useState, useEffect, useMemo } from 'react'
import { Plus, Trash2, Pencil, Lock, Unlock } from 'lucide-react'
import { Card, Button, Table, Modal, ConfirmDialog, Input, Badge, SearchableSelect, QualifiedTeacherSelect, QualifiedRoomSelect, FilterPanel } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { sessionApi, scheduleApi, subjectApi, teacherApi, roomApi, batchApi, classSectionApi, timeslotApi } from '../services/api'
import type { ClassSession, Schedule, Subject, Teacher, Room, Batch, ClassSection, Timeslot, SessionCreateRequest, SessionAssignmentRequest } from '../types'
import { useToast } from '../contexts/ToastContext'

const STATUS_VARIANT: Record<string, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  DRAFT: 'gray',
  ACTIVE: 'green',
  ARCHIVED: 'blue',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
}

export default function ManualSessions() {
  const { toast } = useToast()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [scheduleId, setScheduleId] = useState<number>(0)
  const [sessions, setSessions] = useState<ClassSession[]>([])
  const [subjects, setSubjects] = useState<Subject[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [sections, setSections] = useState<ClassSection[]>([])
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingSessions, setLoadingSessions] = useState(false)

  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ClassSession | null>(null)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [subjectFilter, setSubjectFilter] = useState<number | null>(null)
  const [teacherFilter, setTeacherFilter] = useState<number | null>(null)
  const [departmentFilter, setDepartmentFilter] = useState<number | null>(null)
  const [batchFilter, setBatchFilter] = useState<number | null>(null)
  const [sectionFilter, setSectionFilter] = useState<number | null>(null)

  const [createBatchId, setCreateBatchId] = useState(0)
  const [createSectionId, setCreateSectionId] = useState(0)
  const [subjectId, setSubjectId] = useState(0)
  const [teacherId, setTeacherId] = useState(0)
  const [roomId, setRoomId] = useState(0)
  const [timeslotId, setTimeslotId] = useState(0)
  const [duration, setDuration] = useState(1)
  const [locked, setLocked] = useState(false)

  const loadBase = () => {
    Promise.allSettled([
      scheduleApi.getAll(),
      subjectApi.getAll(),
      teacherApi.getAll(),
      roomApi.getAll(),
      batchApi.getAll(),
      classSectionApi.getAll(),
      timeslotApi.getAll(),
    ])
      .then(([s, su, t, r, b, cs, ts]) => {
        if (s.status === 'fulfilled') {
          setSchedules(s.value)
          setScheduleId((prev) => (prev ? prev : (s.value[0]?.id ?? 0)))
        }
        if (su.status === 'fulfilled') setSubjects(su.value)
        if (t.status === 'fulfilled') setTeachers(t.value)
        if (r.status === 'fulfilled') setRooms(r.value)
        if (b.status === 'fulfilled') setBatches(b.value)
        if (cs.status === 'fulfilled') setSections(cs.value)
        if (ts.status === 'fulfilled') setTimeslots(ts.value)
        const failed = [s, su, t, r, b, cs, ts].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some data failed to refresh (${failed}/7)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadBase() }, [])

  useEffect(() => {
    if (!scheduleId) return
    setLoadingSessions(true)
    scheduleApi.getSessions(scheduleId)
      .then(setSessions)
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to load sessions'))
      .finally(() => setLoadingSessions(false))
  }, [scheduleId])

  const schedule = schedules.find((s) => s.id === scheduleId)
  const scheduleReadonly = schedule?.status === 'ARCHIVED'

  const openAdd = () => {
    if (!scheduleId) { toast.error('Create or select a schedule first'); return }
    setEditing(null)
    setCreateBatchId(0)
    setCreateSectionId(0)
    setSubjectId(subjects[0]?.id ?? 0)
    setTeacherId(0)
    setRoomId(0)
    setTimeslotId(0)
    setDuration(1)
    setLocked(false)
    setOpen(true)
  }

  const openEdit = (session: ClassSession) => {
    setEditing(session)
    setSubjectId(session.subjectId ?? 0)
    setCreateBatchId(session.batchId ?? 0)
    setCreateSectionId(session.sectionId ?? 0)
    setTeacherId(session.teacherId ?? 0)
    setRoomId(session.roomId ?? 0)
    setTimeslotId(session.timeslotId ?? 0)
    setDuration(session.duration)
    setLocked(session.isLocked)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!scheduleId) { toast.error('Select a schedule first'); return }
    if (!subjectId) { toast.error('Select a subject'); return }
    if (!createBatchId && !createSectionId) { toast.error('Select a batch or a section'); return }

    const subject = subjects.find((s) => s.id === subjectId)
    const batchId = createSectionId ? sections.find((s) => s.id === createSectionId)?.batchId : createBatchId
    const batch = batches.find((b) => b.id === batchId)
    if (subject?.departmentId != null && batch?.departmentId != null && subject.departmentId !== batch.departmentId) {
      toast.error(`Subject belongs to ${subject.departmentName ?? `dept #${subject.departmentId}`}, batch to ${batch.departmentName ?? `dept #${batch.departmentId}`} — pick matching department/${batch.departmentName ?? `dept #${batch.departmentId}`}`)
      return
    }

    if (editing) {
      const assignment: SessionAssignmentRequest = {
        teacherId: teacherId || null,
        roomId: roomId || null,
        timeslotId: timeslotId || null,
        locked,
      }
      if (assignment.teacherId == null) assignment.clearTeacher = true
      if (assignment.roomId == null) assignment.clearRoom = true
      if (assignment.timeslotId == null) assignment.clearTimeslot = true
      setSaving(true)
      try {
        await sessionApi.updateAssignment(editing.id, assignment)
        toast.success('Session updated')
        setOpen(false)
        reloadSessions()
      } catch (e) {
        toast.error(e instanceof Error ? e.message : 'Update failed')
      } finally {
        setSaving(false)
      }
      return
    }

    const payload: SessionCreateRequest = {
      scheduleId,
      subjectId,
      teacherId: teacherId || null,
      roomId: roomId || null,
      timeslotId: timeslotId || null,
      duration,
      locked,
    }
    if (createSectionId) payload.sectionId = createSectionId
    else payload.batchId = createBatchId

    setSaving(true)
    try {
      await sessionApi.create(payload)
      toast.success('Session added')
      setOpen(false)
      reloadSessions()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Create failed')
    } finally {
      setSaving(false)
    }
  }

  const reloadSessions = () => {
    if (!scheduleId) return
    scheduleApi.getSessions(scheduleId)
      .then(setSessions)
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to reload sessions'))
  }

  const handleDelete = async () => {
    if (confirmId == null) return
    setDeleting(true)
    try {
      await sessionApi.delete(confirmId)
      setSessions((prev) => prev.filter((s) => s.id !== confirmId))
      toast.success('Session removed')
      setConfirmId(null)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const toggleLock = async (session: ClassSession) => {
    try {
      const updated = await sessionApi.updateAssignment(session.id, {
        locked: !session.isLocked,
      })
      setSessions((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
      toast.success(updated.isLocked ? 'Session locked' : 'Session unlocked')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to toggle lock')
    }
  }

  const derivedDeptId = useMemo(() => {
    const subj = subjects.find((s) => s.id === subjectId)
    if (subj?.departmentId != null) return subj.departmentId
    const bid = createSectionId ? sections.find((s) => s.id === createSectionId)?.batchId : createBatchId
    const batch = batches.find((b) => b.id === bid)
    return batch?.departmentId ?? null
  }, [subjectId, createBatchId, createSectionId, subjects, batches, sections])

  const subjectOptions = subjects
    .filter((s) => derivedDeptId == null || s.departmentId == null || s.departmentId === derivedDeptId)
    .map((s) => ({ value: s.id, label: `${s.code} - ${s.name}` }))
  const batchOptions = batches
    .filter((b) => derivedDeptId == null || b.departmentId === derivedDeptId)
    .map((b) => ({
      value: b.id,
      label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
    }))
  const sectionOptions = sections
    .filter((s) => !createBatchId || s.batchId === createBatchId)
    .map((s) => ({
      value: s.id,
      label: `${s.label} — ${s.batchName ?? `Batch #${s.batchId}`}`,
    }))
  const timeslotOptions = timeslots
    .filter((ts) => ts.type === 'CLASS')
    .map((ts) => ({ value: ts.id, label: `${ts.day} ${ts.startTime}-${ts.endTime}` }))

  const columns: Column<ClassSession>[] = [
    {
      key: 'subject', header: 'Subject',
      sortValue: (s) => s.subjectName ?? '',
      render: (s) => (
        <div>
          <span className="font-medium">{s.subjectName ?? `Subject #${s.subjectId}`}</span>
          {s.isLab && <Badge label="Lab" variant="purple" />}
        </div>
      ),
    },
    {
      key: 'audience', header: 'Audience',
      render: (s) => <span className="text-sm text-gray-600">{s.batchLabel ?? '—'}</span>,
    },
    {
      key: 'timeslot', header: 'Day & Time',
      render: (s) => s.day
        ? <span className="text-sm">{s.day} {s.startTime}-{s.endTime}</span>
        : <span className="text-amber-600 text-sm">Unassigned</span>,
    },
    {
      key: 'teacher', header: 'Teacher',
      render: (s) => s.teacherName ? <span className="text-sm">{s.teacherName}</span> : <span className="text-gray-400">—</span>,
    },
    {
      key: 'room', header: 'Room',
      render: (s) => s.roomNumber
        ? <span className="text-sm">{s.roomNumber}{s.buildingName ? ` · ${s.buildingName}` : ''}</span>
        : <span className="text-gray-400">—</span>,
    },
    { key: 'duration', header: 'Hrs', render: (s) => <span className="text-sm">{s.duration}</span> },
    {
      key: 'lock', header: '',
      render: (s) => (
        <Button variant="ghost" size="sm" icon={s.isLocked ? <Lock size={13} /> : <Unlock size={13} />}
          onClick={() => toggleLock(s)} disabled={scheduleReadonly} title={s.isLocked ? 'Unlock' : 'Lock'} />
      ),
    },
    {
      key: 'actions', header: '', width: '110px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} className="px-1.5" onClick={() => openEdit(s)} disabled={scheduleReadonly} title="Edit" aria-label="Edit" />
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700 px-1.5" disabled={scheduleReadonly} onClick={() => setConfirmId(s.id)} title="Delete" aria-label="Delete" />
        </div>
      ),
    },
  ]

  const getContextItems = (s: ClassSession): ContextMenuItem[] => {
    if (scheduleReadonly) return []
    return [
      { label: 'Edit assignment', icon: <Pencil size={13} />, onClick: () => openEdit(s) },
      { label: s.isLocked ? 'Unlock' : 'Lock', icon: s.isLocked ? <Unlock size={13} /> : <Lock size={13} />, onClick: () => toggleLock(s) },
      { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(s.id) },
    ]
  }

  const sessionCounts = useMemo(() => {
    const placed = sessions.filter((s) => s.timeslotId != null).length
    return { total: sessions.length, placed, unplaced: sessions.length - placed }
  }, [sessions])

  const filteredSessions = sessions.filter((s) => {
    if (subjectFilter != null && s.subjectId !== subjectFilter) return false
    if (teacherFilter != null && s.teacherId !== teacherFilter) return false
    if (departmentFilter != null) {
      const batch = s.batchId ? batches.find((b) => b.id === s.batchId) : undefined
      if (batch?.departmentId !== departmentFilter) return false
    }
    if (batchFilter != null && s.batchId !== batchFilter) return false
    if (sectionFilter != null && s.sectionId !== sectionFilter) return false
    return true
  })

  const filterSubjectOptions = subjects.map((s) => ({ value: s.id, label: `${s.code} - ${s.name}` }))
  const filterTeacherOptions = teachers.map((t) => ({ value: t.id, label: t.name }))

  const filterDepartmentOptions = (() => {
    const deptIds = new Set<number>()
    for (const b of batches) {
      if (b.departmentId) deptIds.add(b.departmentId)
    }
    return Array.from(deptIds).map((id) => {
      const b = batches.find((x) => x.departmentId === id)
      return { value: id, label: b?.departmentName ?? `Dept #${id}` }
    })
  })()

  const filterBatchOptions = batches
    .filter((b) => departmentFilter == null || b.departmentId === departmentFilter)
    .map((b) => ({
      value: b.id,
      label: `${b.departmentName ?? ''} Yr ${b.year} – ${b.section}`.trim(),
    }))

  const filterSectionOptions = (() => {
    const batchIds = departmentFilter != null
      ? new Set(batches.filter((b) => b.departmentId === departmentFilter).map((b) => b.id))
      : null
    const seen = new Map<number, string>()
    for (const s of sessions) {
      const sid = s.sectionId
      if (sid == null) continue
      if (batchIds != null && s.batchId != null && !batchIds.has(s.batchId)) continue
      if (batchFilter != null && s.batchId !== batchFilter) continue
      if (!seen.has(sid)) {
        const sec = sections.find((x) => x.id === sid)
        seen.set(sid, sec?.label ?? `Section #${sid}`)
      }
    }
    return Array.from(seen.entries()).map(([value, label]) => ({ value, label }))
  })()

  return (
    <>
      <Card
        title="Manual Sessions"
        description="Ad-hoc lectures and labs added by hand. Pick a schedule, then add, edit, or remove sessions."
        actions={
          <Button icon={<Plus size={16} />} onClick={openAdd} disabled={scheduleReadonly}>
            Add Session
          </Button>
        }
      >
        <div className="flex items-center gap-4 mb-4 flex-wrap">
          <div className="w-80">
            <SearchableSelect
              label="Schedule"
              value={scheduleId || null}
              onChange={(v) => setScheduleId(v == null ? 0 : +v)}
              options={schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))}
            />
          </div>
          {schedule && (
            <Badge label={schedule.status} variant={STATUS_VARIANT[schedule.status] ?? 'gray'} dot />
          )}
          {scheduleReadonly && (
            <span className="text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2 py-1 rounded">
              Archived schedules are read-only
            </span>
          )}
          {!scheduleReadonly && (
            <span className="text-xs text-gray-500 ml-auto">
              {sessionCounts.placed}/{sessionCounts.total} placed · {sessionCounts.unplaced} unassigned
            </span>
          )}
        </div>
        <FilterPanel activeCount={(subjectFilter != null ? 1 : 0) + (teacherFilter != null ? 1 : 0) + (departmentFilter != null ? 1 : 0) + (batchFilter != null ? 1 : 0) + (sectionFilter != null ? 1 : 0)} persistKey="arare.manualSessions.filters.open">
          <div className="flex items-center gap-3">
            {filterDepartmentOptions.length > 0 && (
              <SearchableSelect
                label="Department"
                value={departmentFilter}
                onChange={(v) => { setDepartmentFilter(v == null ? null : +v); setBatchFilter(null); setSectionFilter(null) }}
                options={filterDepartmentOptions}
                placeholder="All departments"
                allowClear
                className="w-72"
              />
            )}
            {filterBatchOptions.length > 0 && (
              <SearchableSelect
                label="Batch"
                value={batchFilter}
                onChange={(v) => { setBatchFilter(v == null ? null : +v); setSectionFilter(null) }}
                options={filterBatchOptions}
                placeholder="All batches"
                allowClear
                className="w-72"
              />
            )}
            {filterSectionOptions.length > 0 && (
              <SearchableSelect
                label="Section"
                value={sectionFilter}
                onChange={(v) => setSectionFilter(v == null ? null : +v)}
                options={filterSectionOptions}
                placeholder="All sections"
                allowClear
                className="w-72"
              />
            )}
            {subjects.length > 0 && (
              <SearchableSelect
                label="Subject"
                value={subjectFilter}
                onChange={(v) => setSubjectFilter(v == null ? null : +v)}
                options={filterSubjectOptions}
                placeholder="All subjects"
                allowClear
                className="w-72"
              />
            )}
            {teachers.length > 0 && (
              <SearchableSelect
                label="Teacher"
                value={teacherFilter}
                onChange={(v) => setTeacherFilter(v == null ? null : +v)}
                options={filterTeacherOptions}
                placeholder="All teachers"
                allowClear
                className="w-72"
              />
            )}
          </div>
        </FilterPanel>
        <Table
          columns={columns}
          data={filteredSessions}
          loading={loading || loadingSessions}
          keyExtractor={(s) => s.id}
          searchable
          exportable
          exportFilename="manual-sessions"
          searchKeys={[(s) => s.subjectName ?? '', (s) => s.batchLabel ?? '', (s) => s.teacherName ?? '', (s) => s.roomNumber ?? '']}
          onRowContextMenu={getContextItems}
          emptyMessage={scheduleId ? 'No sessions in this schedule yet. Click "Add Session" to create one.' : 'Create a schedule first, then add manual sessions.'}
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? `Edit Session — ${editing.subjectName ?? `#${editing.id}`}` : 'Add Manual Session'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>{editing ? 'Save' : 'Add Session'}</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid md:grid-cols-2 gap-4">
            <SearchableSelect label="Subject" value={subjectId || null} onChange={(v) => setSubjectId(v == null ? 0 : +v)} options={subjectOptions} placeholder="Select subject" disabled={!!editing} />
            <QualifiedTeacherSelect teachers={teachers} subjectId={subjectId} value={teacherId || null} onChange={(v) => setTeacherId(v == null ? 0 : +v)} label="Teacher" placeholder="Select teacher" allowClear />
          </div>
          <div className="grid md:grid-cols-2 gap-4">
            <SearchableSelect label="Batch" value={createBatchId || null} onChange={(v) => { setCreateBatchId(v == null ? 0 : +v); setCreateSectionId(0) }} options={batchOptions} placeholder="Pick a batch or a section" allowClear disabled={!!editing} />
            <SearchableSelect label="Section" value={createSectionId || null} onChange={(v) => setCreateSectionId(v == null ? 0 : +v)} options={sectionOptions} placeholder={createBatchId ? 'Optional section' : 'Select a batch first'} disabled={!createBatchId || !!editing} allowClear />
          </div>
          <div className="grid md:grid-cols-3 gap-4">
            <SearchableSelect label="Day & Time" value={timeslotId || null} onChange={(v) => setTimeslotId(v == null ? 0 : +v)} options={timeslotOptions} placeholder="— Unassigned —" allowClear />
            <QualifiedRoomSelect rooms={rooms} subject={subjects.find((s) => s.id === subjectId) ?? null} minCapacity={batches.find((b) => b.id === createBatchId)?.studentCount ?? null} value={roomId || null} onChange={(v) => setRoomId(v == null ? 0 : v)} label="Room" allowClear />
            <Input label="Duration (hours)" type="number" min={1} max={4} value={duration} disabled={!!editing} onChange={(e) => setDuration(Math.max(1, +e.target.value || 1))} />
          </div>
          <label className="flex items-center gap-2 text-sm cursor-pointer">
            <input type="checkbox" checked={locked} onChange={(e) => setLocked(e.target.checked)} />
            <span className="flex items-center gap-1">
              {locked ? <Lock size={13} /> : <Unlock size={13} />}
              Lock this session (solver will not move it)
            </span>
          </label>
          {editing && (
            <p className="text-xs text-gray-500">Editing re-validates against the same hard constraints the solver enforces.</p>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Session"
        message="This removes the session from the schedule permanently. Regenerate to restore it from the curriculum."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}