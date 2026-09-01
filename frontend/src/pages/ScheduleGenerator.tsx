import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, CheckCircle, Clock, GitBranch, Plus, Settings, ShieldCheck, Trash2, X, Zap } from 'lucide-react'
import { Card, Button, Input, Select, SearchableSelect, MultiSelect } from '../components/ui'
import { scheduleApi, departmentApi, instituteApi, batchApi, teacherApi, roomApi, subjectApi } from '../services/api'
import { useSolveJobPoll } from '../hooks/useSolveJob'
import type { ScheduleRequest, ScheduleScope, Department, Batch, Teacher, Room, Schedule, Subject, FeasibilityCheckResult, SolveJobResponse, PreAllocationSpec, Institute } from '../types'

const SCOPE_OPTIONS: { value: ScheduleScope; label: string }[] = [
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'INSTITUTE', label: 'Institute' },
  { value: 'UNIVERSITY', label: 'University' },
]

const TIME_MARKS = [10, 30, 60, 120, 300]
const WIZARD_STEPS = [
  { id: 1, label: 'Scope Selection', optional: false },
  { id: 2, label: 'Resource Selection', optional: false },
  { id: 3, label: 'Pre-assign Teachers', optional: true },
  { id: 4, label: 'Review', optional: false },
  { id: 5, label: 'Run & Validate', optional: false },
]

export default function ScheduleGenerator() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [departments, setDepartments] = useState<Department[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [allBatches, setAllBatches] = useState<Batch[]>([])
  const [allTeachers, setAllTeachers] = useState<Teacher[]>([])
  const [allRooms, setAllRooms] = useState<Room[]>([])
  const [allSubjects, setAllSubjects] = useState<Subject[]>([])
  const [allSchedules, setAllSchedules] = useState<Schedule[]>([])

  const [preAllocations, setPreAllocations] = useState<PreAllocationSpec[]>([])
  const [draft, setDraft] = useState<PreAllocationSpec>({ batchId: 0, subjectId: 0, teacherId: 0 })

  const [form, setForm] = useState<ScheduleRequest>({
    name: `Schedule ${new Date().toLocaleDateString()}`,
    scope: 'DEPARTMENT',
    solvingTimeSeconds: 30,
  })

  const [wizardStep, setWizardStep] = useState(1)
  const [builderMode, setBuilderMode] = useState(false)
  const [selectedBatchIds, setSelectedBatchIds] = useState<number[]>([])
  const [selectedTeacherIds, setSelectedTeacherIds] = useState<number[]>([])
   const [selectedRoomIds, setSelectedRoomIds] = useState<number[]>([])

  const [running, setRunning] = useState(false)
  const [activeJob, setActiveJob] = useState<SolveJobResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [feasibility, setFeasibility] = useState<FeasibilityCheckResult | null>(null)
  const [checkingFeasibility, setCheckingFeasibility] = useState(false)

  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [jobStartedAt, setJobStartedAt] = useState<number | null>(null)

  useEffect(() => {
    if (jobStartedAt === null) return
    const timer = window.setInterval(() => {
      setElapsedSeconds(Math.max(0, Math.round((Date.now() - jobStartedAt) / 1000)))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [jobStartedAt])

  useEffect(() => {
    Promise.allSettled([
      departmentApi.getAll(),
      instituteApi.getAll(),
      batchApi.getAll(),
      teacherApi.getAll(),
      roomApi.getAll(),
      subjectApi.getAll(),
      scheduleApi.getAll(),
    ]).then(([d, inst, b, t, r, su, s]) => {
      if (d.status === 'fulfilled') setDepartments(d.value)
      if (inst.status === 'fulfilled') setInstitutes(inst.value)
      if (b.status === 'fulfilled') setAllBatches(b.value)
      if (t.status === 'fulfilled') setAllTeachers(t.value)
      if (r.status === 'fulfilled') setAllRooms(r.value)
      if (su.status === 'fulfilled') setAllSubjects(su.value)
      if (s.status === 'fulfilled') {
        setAllSchedules(s.value)
        const parentId = searchParams.get('parentId')
        if (parentId) {
          const parent = s.value.find((sc: Schedule) => sc.id === +parentId)
          if (parent) {
            setForm((prev) => ({
              ...prev,
              parentScheduleId: parent.id,
              scope: parent.scope,
              instituteId: parent.instituteId,
              name: `${parent.name} (re-solve)`,
            }))
          }
        }
      }
      const failed = [d, inst, b, t, r, su, s].filter((x) => x.status === 'rejected').length
      if (failed > 0) {
        setError(`Some prerequisites failed to load (${failed}/7)`)
      }
    })
  }, [])

  const visibleBatches = form.scope === 'DEPARTMENT' && form.departmentId
    ? allBatches.filter((b) => b.departmentId === form.departmentId)
    : form.scope === 'INSTITUTE' && form.instituteId
      ? allBatches.filter((b) => b.instituteId === form.instituteId)
      : allBatches
  const visibleSubjects = form.scope === 'DEPARTMENT' && form.departmentId
    ? allSubjects.filter((s) => !s.departmentId || s.departmentId === form.departmentId)
    : form.scope === 'INSTITUTE' && form.instituteId
      ? allSubjects.filter((s) => !s.departmentId || s.instituteId === form.instituteId)
      : allSubjects
  const preAllocBatches = builderMode && selectedBatchIds.length > 0
    ? visibleBatches.filter((b) => selectedBatchIds.includes(b.id))
    : visibleBatches
  const toggleId = (
    id: number,
    current: number[],
    setter: (ids: number[]) => void,
  ) => {
    setter(current.includes(id) ? current.filter((x) => x !== id) : [...current, id])
  }

  const buildRequest = (): ScheduleRequest => ({
    ...form,
    batchIds: builderMode && selectedBatchIds.length > 0 ? selectedBatchIds : undefined,
    teacherIds: builderMode && selectedTeacherIds.length > 0 ? selectedTeacherIds : undefined,
    roomIds: builderMode && selectedRoomIds.length > 0 ? selectedRoomIds : undefined,
    preAllocations: preAllocations.length > 0 ? preAllocations : undefined,
  })

  const addPreAllocation = () => {
    if (!draft.batchId || !draft.subjectId || !draft.teacherId) {
      setError('Pre-assignments need a batch, a subject, and a teacher')
      return
    }
    setPreAllocations((prev) => [...prev, { ...draft }])
    setDraft({ batchId: 0, subjectId: 0, teacherId: 0 })
    setError(null)
  }

  const removePreAllocation = (index: number) => {
    setPreAllocations((prev) => prev.filter((_, i) => i !== index))
  }

  const handleGenerate = async () => {
    if (!form.name.trim()) { setError('Schedule name is required'); return }
    if (form.scope === 'DEPARTMENT' && !form.departmentId) {
      setError('Please select a department for department-scoped scheduling'); return
    }
    if (form.scope === 'INSTITUTE' && institutes.length > 1 && !form.instituteId) {
      setError('Please select an institute for institute-scoped scheduling'); return
    }
    if (builderMode && selectedBatchIds.length === 0) {
      setError('Builder mode: please select at least one batch'); return
    }

    setRunning(true)
    setError(null)
    setElapsedSeconds(0)
    setActiveJob(null)

    try {
      const request = buildRequest()
      const job = await scheduleApi.generate(request)
      if (job.id == null) {
        // No-op response (nothing to solve): the schedule already exists.
        setRunning(false)
        if (job.scheduleId) navigate(`/schedule/view/${job.scheduleId}`)
        return
      }
      setJobStartedAt(Date.now())
      setActiveJob(job)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Generation failed'
      setError(msg)
      setRunning(false)

      // If generation is infeasible, immediately fetch structured diagnostics for the UI.
      if (/infeasible|unprocessable|hard score/i.test(msg)) {
        try {
          const request = buildRequest()
          const result = await scheduleApi.checkFeasibility(request)
          setFeasibility(result)
          if (wizardStep < 4) setWizardStep(4)
        } catch {
          // Keep original generation error visible if feasibility endpoint also fails.
        }
      }
    }
  }

  const handleJobFinished = async (job: SolveJobResponse) => {
    setRunning(false)
    setJobStartedAt(null)
    if (job.status === 'SUCCEEDED') {
      if (job.scheduleId) {
        // Infeasible solves now SUCCEED with the partial result persisted.
        // Surface that clearly instead of silently dropping the user onto a
        // read-only INFEASIBLE schedule.
        try {
          const schedule = await scheduleApi.getById(job.scheduleId)
          if (schedule.status === 'INFEASIBLE') {
            setActiveJob(null)
            setError('Generated schedule is INFEASIBLE (hard conflicts remain). The partial result was saved — open it from History to inspect what blocked generation.')
            return
          }
        } catch {
          // Fall through to the normal navigation if the status check fails.
        }
        navigate(`/schedule/view/${job.scheduleId}`)
      }
    } else if (job.status === 'FAILED') {
      setError(job.errorMessage || 'Solver failed — check the schedule data and try again.')
    } else {
      setError('Generation cancelled.')
    }
  }

  const handleCheckFeasibility = async () => {
    setCheckingFeasibility(true)
    setFeasibility(null)
    try {
      const result = await scheduleApi.checkFeasibility(buildRequest())
      setFeasibility(result)
      if (wizardStep < 4) setWizardStep(4)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Feasibility check failed')
    } finally {
      setCheckingFeasibility(false)
    }
  }

  const deptOptions = departments.map((d) => ({ value: d.id, label: `${d.name} (${d.code})` }))
  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))
  const parentOptions = [
    { value: '', label: '- None (generate from scratch) -' },
    ...allSchedules.map((s) => ({ value: s.id, label: `${s.name} [${s.score ?? s.status}]` })),
  ]

  const timeLabel = (s: number) =>
    s < 60 ? `${s}s` : `${s / 60}m`

  const canNext =
    wizardStep === 1
      ? (form.scope !== 'DEPARTMENT' || Boolean(form.departmentId))
      : wizardStep === 2
        ? (!builderMode || selectedBatchIds.length > 0)
        : true

  const gotoNextStep = () => {
    if (!canNext || wizardStep >= 5) return
    setWizardStep((x) => Math.min(5, x + 1))
  }

  const gotoPrevStep = () => {
    setWizardStep((x) => Math.max(1, x - 1))
  }

  return (
    <div className="space-y-4">
      {activeJob && (
        <SolveProgress
          job={activeJob}
          elapsedSeconds={elapsedSeconds}
          onDone={handleJobFinished}
        />
      )}

      <Card className="card border-gray-200 text-gray-900">
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div>
              <p className="text-xs uppercase tracking-[0.14em] text-gray-500">Timetable Generation</p>
              <h2 className="text-xl font-semibold">Schedule Generator</h2>
            </div>
          </div>

          <ol className="grid md:grid-cols-5 gap-2">
            {WIZARD_STEPS.map((step) => (
              <li
                key={step.id}
                className={`rounded-lg border px-3 py-2 text-xs ${
                  wizardStep === step.id
                    ? 'border-cyan-300 bg-cyan-50 text-cyan-800'
                    : wizardStep > step.id
                      ? 'border-emerald-300 bg-emerald-50 text-emerald-800'
                      : 'border-gray-200 bg-gray-50 text-gray-500'
                }`}
              >
                <span className="font-semibold">{step.id}. </span>{step.label}
                {step.optional && (
                  <span className="ml-1.5 rounded-full bg-gray-200 px-1.5 py-0.5 text-[10px] font-medium text-gray-500 uppercase">
                    Optional
                  </span>
                )}
              </li>
            ))}
          </ol>

          {error && (
            <div className="rounded-lg bg-rose-50 border border-rose-200 px-4 py-3 text-sm text-rose-700">
              {error}
            </div>
          )}

          {wizardStep === 1 && (
            <div className="space-y-4">
              <Input
                label="Schedule Name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
              <Select
                label="Scope"
                value={form.scope}
                onChange={(e) => {
                  const nextScope = e.target.value as ScheduleScope
                  const next: ScheduleRequest = {
                    ...form,
                    scope: nextScope,
                    departmentId: undefined,
                    instituteId: nextScope === 'INSTITUTE' && institutes.length === 1 ? institutes[0].id : undefined,
                  }
                  setForm(next)
                  setSelectedBatchIds([])
                }}
                options={SCOPE_OPTIONS}
              />
              {form.scope === 'DEPARTMENT' && (
                <Select
                  label="Department"
                  value={form.departmentId ?? ''}
                    onChange={(e) => {
                      const deptId = +e.target.value || undefined
                      const dept = departments.find((d) => d.id === deptId)
                      setForm({ ...form, departmentId: deptId, instituteId: dept?.instituteId })
                      setSelectedBatchIds([])
                    }}
                  options={deptOptions}
                  placeholder="Select department"
                  helpText="Only batches and subjects from this department will be scheduled"
                />
              )}
              {form.scope === 'INSTITUTE' && institutes.length > 1 && (
                <Select
                  label="Institute"
                  value={form.instituteId ?? ''}
                  onChange={(e) => {
                    setForm({ ...form, instituteId: +e.target.value || undefined })
                    setSelectedBatchIds([])
                  }}
                  options={instituteOptions}
                  placeholder="Select institute"
                  helpText="Only batches and subjects from this institute will be scheduled"
                />
              )}
              {allSchedules.length > 0 && (
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <label className="block text-sm font-medium text-gray-700">
                      Derive from existing schedule
                    </label>
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-gray-500">
                      Optional
                    </span>
                  </div>
                  <Select
                    value={form.parentScheduleId ?? ''}
                    onChange={(e) => setForm({ ...form, parentScheduleId: +e.target.value || undefined })}
                    options={parentOptions}
                    helpText="Re-solve from a prior schedule with lock inheritance"
                  />
                </div>
              )}
              {form.parentScheduleId && (
                <div className="flex items-center gap-2 rounded-md bg-indigo-50 border border-indigo-200 px-4 py-3 text-sm text-indigo-700">
                  <GitBranch size={14} />
                  Locked sessions from parent schedule remain protected.
                </div>
              )}
            </div>
          )}

          {wizardStep === 2 && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-gray-700">Mode:</span>
                <button
                  type="button"
                  onClick={() => setBuilderMode(false)}
                  className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                    !builderMode
                      ? 'bg-cyan-500 text-slate-900'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  Quick
                </button>
                <button
                  type="button"
                  onClick={() => setBuilderMode(true)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                    builderMode
                      ? 'bg-cyan-500 text-slate-900'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  <Settings size={13} />
                  Builder
                </button>
              </div>

              {!builderMode && (
                <div className="rounded-md bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-700">
                  Quick mode includes all configured resources for the selected scope.
                </div>
              )}

              {builderMode && (
                <>
                  <Card title="Batches" className="bg-white border-gray-200 text-gray-900">
                    {visibleBatches.length === 0 ? (
                      <p className="text-sm text-gray-500">No batches available for this scope.</p>
                    ) : (
                      <div className="grid grid-cols-2 gap-2">
                        <label className="col-span-2 flex items-center gap-2 text-xs text-gray-500 mb-1 cursor-pointer select-none">
                          <input
                            type="checkbox"
                            checked={selectedBatchIds.length === visibleBatches.length && visibleBatches.length > 0}
                            onChange={(e) =>
                              setSelectedBatchIds(e.target.checked ? visibleBatches.map((b) => b.id) : [])
                            }
                          />
                          Select all
                        </label>
                        {visibleBatches.map((b) => (
                          <label key={b.id} className="flex items-center gap-2 text-sm cursor-pointer text-gray-800">
                            <input
                              type="checkbox"
                              checked={selectedBatchIds.includes(b.id)}
                              onChange={() => toggleId(b.id, selectedBatchIds, setSelectedBatchIds)}
                            />
                            {b.departmentName ? `${b.departmentName} ` : ''}Yr {b.year}-{b.section}
                          </label>
                        ))}
                      </div>
                    )}
                  </Card>

                  <Card title="Teachers" className="bg-white border-gray-200 text-gray-900">
                    <p className="text-xs text-gray-500 mb-2">
                      Pick which teachers participate. {allTeachers.length} available — search to narrow.
                    </p>
                    <MultiSelect
                      label=""
                      options={allTeachers.map((t) => ({ value: t.id, label: t.name }))}
                      selected={selectedTeacherIds}
                      onChange={setSelectedTeacherIds}
                      maxHeight={280}
                    />
                  </Card>

                  <Card title="Rooms" className="bg-white border-gray-200 text-gray-900">
                    <p className="text-xs text-gray-500 mb-2">
                      Pick which rooms participate. {allRooms.length} available — search to narrow.
                    </p>
                    <MultiSelect
                      label=""
                      options={allRooms.map((r) => ({
                        value: r.id,
                        label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''} [${r.type}]`,
                      }))}
                      selected={selectedRoomIds}
                      onChange={setSelectedRoomIds}
                      maxHeight={280}
                    />
                  </Card>
                </>
              )}

              <Card
                title={
                  <span className="flex items-center gap-2">
                    Blocked Days
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-gray-500">
                      Optional
                    </span>
                  </span>
                }
                className="bg-white border-gray-200 text-gray-900"
              >
                <p className="text-xs text-gray-500 mb-2">
                  Whole days this timetable must not use (e.g. a Saturday that is closed). Nothing is blocked
                  by default — the solver treats every other day as schedulable. Per-batch rest days can be set
                  in the Batches page instead.
                </p>
                <div className="flex flex-wrap gap-2">
                  {(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const).map((day) => {
                    const blocked = (form.blockedDays ?? []).includes(day)
                    return (
                      <button
                        key={day}
                        type="button"
                        onClick={() => {
                          const current = form.blockedDays ?? []
                          setForm({
                            ...form,
                            blockedDays: blocked
                              ? current.filter((d) => d !== day)
                              : [...current, day],
                          })
                        }}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition-colors ${
                          blocked
                            ? 'bg-rose-100 text-rose-700 border-rose-300'
                            : 'bg-white text-gray-600 border-gray-300 hover:border-gray-400'
                        }`}
                      >
                        <span className={`inline-block w-2 h-2 rounded-full ${blocked ? 'bg-rose-500' : 'bg-transparent'}`} />
                        {day.slice(0, 3)}
                      </button>
                    )
                  })}
                </div>
              </Card>
            </div>
          )}

           {wizardStep === 3 && (
             <div className="space-y-4">
               <div className="flex items-center justify-between gap-3 flex-wrap rounded-md bg-cyan-50 border border-cyan-200 px-4 py-3 text-sm text-cyan-800">
                 <div>
                   <span className="font-semibold">Optional step.</span>{' '}
                   Pin specific teachers (and optionally rooms or exact slots) to batch + subject pairs
                   before the solver runs. Everything else remains fully scheduled by the solver.
                 </div>
                 <span className="rounded-full bg-cyan-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-cyan-700">
                   Optional
                 </span>
               </div>

               <div className="grid md:grid-cols-2 gap-4">
                 <SearchableSelect
                   label="Batch"
                   value={draft.batchId || null}
                   onChange={(v) => setDraft({ ...draft, batchId: v == null ? 0 : +v })}
                   options={preAllocBatches.map((b) => ({
                     value: b.id,
                     label: `${b.departmentName ? `${b.departmentName} ` : ''}Yr ${b.year}-${b.section}`,
                   }))}
                   placeholder="Select batch"
                   allowClear
                 />
                 <SearchableSelect
                   label="Subject"
                   value={draft.subjectId || null}
                   onChange={(v) => setDraft({ ...draft, subjectId: v == null ? 0 : +v })}
                   options={visibleSubjects.map((s) => ({ value: s.id, label: `${s.code} - ${s.name}` }))}
                   placeholder="Select subject"
                   allowClear
                 />
                 <SearchableSelect
                   label="Teacher (pinned)"
                   value={draft.teacherId || null}
                   onChange={(v) => setDraft({ ...draft, teacherId: v == null ? 0 : +v })}
                   options={allTeachers.map((t) => ({ value: t.id, label: t.name }))}
                   placeholder="Select teacher"
                   allowClear
                 />
                 <SearchableSelect
                   label="Room (optional)"
                   value={draft.roomId ?? null}
                   onChange={(v) => setDraft({ ...draft, roomId: v == null ? undefined : +v })}
                   options={allRooms.map((r) => ({
                     value: r.id,
                     label: `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''} [${r.type}]`,
                   }))}
                   placeholder="Any room"
                   allowClear
                 />
               </div>

               <Button variant="secondary" icon={<Plus size={15} />} onClick={addPreAllocation}>
                 Add Pre-assignment
               </Button>

               {preAllocations.length > 0 && (
                 <Card title={`Pre-assignments (${preAllocations.length})`} className="bg-white border-gray-200 text-gray-900">
                   <div className="divide-y divide-gray-100 text-sm">
                     {preAllocations.map((p, i) => {
                       const batch = allBatches.find((b) => b.id === p.batchId)
                       const subject = allSubjects.find((s) => s.id === p.subjectId)
                       const teacher = allTeachers.find((t) => t.id === p.teacherId)
                       const room = p.roomId ? allRooms.find((r) => r.id === p.roomId) : undefined
                       return (
                         <div key={i} className="flex items-center justify-between gap-3 py-2">
                           <div>
                             <p className="font-medium">
                               {subject ? `${subject.code} - ${subject.name}` : `Subject #${p.subjectId}`}
                             </p>
                             <p className="text-xs text-gray-500">
                               {batch ? `Yr ${batch.year}-${batch.section}` : `Batch #${p.batchId}`}
                               {' · '}{teacher ? teacher.name : `Teacher #${p.teacherId}`}
                               {room ? ` · ${room.roomNumber}` : ' · any room'}
                             </p>
                           </div>
                           <button
                             type="button"
                             onClick={() => removePreAllocation(i)}
                             className="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-rose-600 hover:bg-rose-50"
                           >
                             <Trash2 size={13} />
                             Remove
                           </button>
                         </div>
                       )
                     })}
                   </div>
                 </Card>
               )}

               {preAllocations.length === 0 && (
                 <p className="text-xs text-gray-500">No pre-assignments yet — the solver will assign all teachers itself.</p>
               )}
             </div>
           )}

{wizardStep === 5 && (
             <div className="space-y-4">
               <div className="rounded-md bg-gray-50 border border-gray-200 px-4 py-3 text-sm text-gray-700">
                 <p className="mb-1">
                   <span className="font-semibold text-gray-900">{form.name}</span>
                   {' '}· {form.scope.charAt(0) + form.scope.slice(1).toLowerCase()} scope
                   {form.scope === 'DEPARTMENT' && form.departmentId
                     ? ` (${departments.find((d) => d.id === form.departmentId)?.name ?? 'department'})`
                     : ''}
                   {form.parentScheduleId ? ' · derived from a prior schedule' : ''}
                 </p>
<p className="text-xs text-gray-500 mt-1">
                    {builderMode
                      ? `${selectedBatchIds.length} batches, ${selectedTeacherIds.length} teachers, ${selectedRoomIds.length} rooms selected · `
                      : 'All configured resources for the selected scope · '}
                    solving time {timeLabel(form.solvingTimeSeconds ?? 30)}
                    {preAllocations.length > 0 && (
                      <span> · {preAllocations.length} teacher pre-assignment{preAllocations.length !== 1 ? 's' : ''}</span>
                    )}
                    {(form.blockedDays?.length ?? 0) > 0 && (
                      <span> · blocked: {form.blockedDays!.map((d) => d.slice(0, 3)).join(', ')}</span>
                    )}
                  </p>
                </div>

                <div className="flex items-center gap-3 flex-wrap">
                  <Button
                    variant="secondary"
                    size="lg"
                    loading={checkingFeasibility}
                    disabled={running}
                    icon={<ShieldCheck size={16} />}
                    onClick={handleCheckFeasibility}
                  >
                    Check Feasibility
                  </Button>
                  <Button
                    size="lg"
                    loading={running}
                    disabled={running}
                    icon={<Zap size={18} />}
                    onClick={handleGenerate}
                  >
                    Launch Solver
                  </Button>
                </div>
              </div>
           )}

          {wizardStep === 4 && (
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="flex items-center gap-1.5 text-sm font-medium text-gray-700">
                    <Clock size={13} />
                    Solving Time
                  </label>
                  <span className="text-sm font-semibold text-cyan-700">
                    {timeLabel(form.solvingTimeSeconds ?? 30)}
                  </span>
                </div>
                <input
                  type="range"
                  min={10}
                  max={300}
                  step={10}
                  value={form.solvingTimeSeconds ?? 30}
                  onChange={(e) => setForm({ ...form, solvingTimeSeconds: +e.target.value })}
                  className="w-full accent-cyan-500"
                />
                <div className="flex justify-between text-xs text-gray-500 mt-1">
                  {TIME_MARKS.map((m) => <span key={m}>{timeLabel(m)}</span>)}
                </div>
              </div>

              <div className="flex items-center justify-between gap-4 flex-wrap">
                <Button
                  variant="secondary"
                  loading={checkingFeasibility}
                  icon={<ShieldCheck size={16} />}
                  onClick={handleCheckFeasibility}
                >
                  Check Feasibility
                </Button>
                <Button
                  size="lg"
                  loading={running}
                  disabled={running}
                  icon={<Zap size={18} />}
                  onClick={handleGenerate}
                >
                  Launch Solver
                </Button>
              </div>
            </div>
          )}

          <div className="flex items-center justify-between pt-2 border-t border-gray-200">
            <div className="flex items-center gap-2">
              <Button variant="secondary" onClick={gotoPrevStep} disabled={wizardStep === 1} icon={<ArrowLeft size={14} />}>
                Back
              </Button>
              {WIZARD_STEPS.find((s) => s.id === wizardStep)?.optional && (
                <Button
                  variant="ghost"
                  onClick={() => setWizardStep((x) => Math.min(5, x + 1))}
                  icon={<ArrowRight size={14} />}
                >
                  Skip step
                </Button>
              )}
            </div>
            <Button onClick={gotoNextStep} disabled={wizardStep === 5 || !canNext} icon={<ArrowRight size={14} />}>
              Next
            </Button>
          </div>
        </div>
      </Card>

      {feasibility && (
        <Card className="card border-gray-200 text-gray-900">
          <div className="space-y-3">
            <div className={`flex items-center gap-3 rounded-lg px-4 py-3 ${
              feasibility.feasible
                ? feasibility.warningCount > 0
                  ? 'bg-amber-50 border border-amber-200'
                  : 'bg-emerald-50 border border-emerald-200'
                : 'bg-rose-50 border border-rose-200'
            }`}>
              <CheckCircle size={18} className="shrink-0" />
              <div className="text-sm">
                <span className="font-semibold">
                  {feasibility.feasible ? (feasibility.warningCount > 0 ? 'Likely feasible with warnings' : 'Looks good') : 'Infeasible - fix errors'}
                </span>
                <span className="ml-2 text-xs text-gray-600">
                  ~{feasibility.totalSessionsEstimate} sessions - {feasibility.availableTimeslots} slots
                </span>
              </div>
            </div>

            {feasibility.issues.length > 0 && (
              <div className="divide-y divide-gray-200 border border-gray-200 rounded-lg overflow-hidden text-sm">
                {feasibility.issues.map((issue, i) => (
                  <div key={i} className={`flex gap-3 px-4 py-2.5 ${issue.severity === 'ERROR' ? 'bg-rose-50' : 'bg-amber-50'}`}>
                    <div>
                      <span className={`text-xs font-semibold uppercase tracking-wide mr-2 ${issue.severity === 'ERROR' ? 'text-rose-700' : 'text-amber-700'}`}>
                        {issue.category}
                      </span>
                      <span className="text-gray-700">{issue.message}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>
      )}
    </div>
  )
}

function SolveProgress({
  job,
  elapsedSeconds,
  onDone,
}: {
  job: SolveJobResponse
  elapsedSeconds: number
  onDone: (job: SolveJobResponse) => void
}) {
  const { job: freshJob, done, error: pollError, cancel } = useSolveJobPoll(job, onDone)

  const cancelled = freshJob.status === 'CANCELLED'
  const failed = freshJob.status === 'FAILED'

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 space-y-2 text-slate-900">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 text-cyan-700">
          <Clock size={16} />
          <span className="text-sm font-medium">
            {done
              ? cancelled
                ? 'Solve cancelled'
                : failed
                  ? 'Solve failed'
                  : 'Solve complete'
              : `Solving… ${Math.floor(elapsedSeconds)}s elapsed`}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {freshJob.bestScore && (
            <span className="text-xs text-slate-500 font-mono" title="Live best score">
              best score {freshJob.bestScore}
            </span>
          )}
          {!done && job.id != null && (
            <button
              type="button"
              onClick={cancel}
              className="flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
            >
              <X size={12} />
              Cancel
            </button>
          )}
        </div>
      </div>
      <div className="h-2 rounded-full bg-slate-200 overflow-hidden">
        <div
          className={`h-full transition-all duration-700 ${
            done
              ? cancelled
                ? 'bg-slate-400'
                : failed
                  ? 'bg-rose-400'
                  : 'bg-emerald-400'
              : 'bg-gradient-to-r from-emerald-400 via-cyan-400 to-blue-400 animate-pulse'
          }`}
          style={{ width: done ? '100%' : '70%' }}
        />
      </div>
      {done && pollError && (
        <p className="text-xs text-rose-600">{pollError}</p>
      )}
      {done && (
        <p className="text-xs text-slate-500">
          {cancelled
            ? 'The solve was stopped before it completed.'
            : failed
              ? freshJob.errorMessage || 'No feasible solution could be produced.'
              : freshJob.elapsedMillis != null
                ? `Finished in ${Math.round(freshJob.elapsedMillis / 1000)}s${freshJob.score ? ` with score ${freshJob.score}` : ''} — opening schedule…`
                : 'Opening schedule…'}
        </p>
      )}
    </section>
  )
}
