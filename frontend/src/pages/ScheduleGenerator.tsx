import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, CheckCircle, Clock, GitBranch, Settings, ShieldCheck, Wand2, X, Zap } from 'lucide-react'
import { Card, Button, Input, Select } from '../components/ui'
import { scheduleApi, departmentApi, batchApi, teacherApi, roomApi } from '../services/api'
import { useSolveJobPoll } from '../hooks/useSolveJob'
import type { ScheduleRequest, ScheduleScope, Department, Batch, Teacher, Room, Schedule, FeasibilityCheckResult, SolveJobResponse } from '../types'

const SCOPE_OPTIONS: { value: ScheduleScope; label: string }[] = [
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'COLLEGE', label: 'College' },
  { value: 'UNIVERSITY', label: 'University' },
]

const TIME_MARKS = [10, 30, 60, 120, 300]
const WIZARD_STEPS = [
  { id: 1, label: 'Scope Selection' },
  { id: 2, label: 'Resource Selection' },
  { id: 3, label: 'Review' },
  { id: 4, label: 'Run & Validate' },
]

export default function ScheduleGenerator() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [departments, setDepartments] = useState<Department[]>([])
  const [allBatches, setAllBatches] = useState<Batch[]>([])
  const [allTeachers, setAllTeachers] = useState<Teacher[]>([])
  const [allRooms, setAllRooms] = useState<Room[]>([])
  const [allSchedules, setAllSchedules] = useState<Schedule[]>([])

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
      batchApi.getAll(),
      teacherApi.getAll(),
      roomApi.getAll(),
      scheduleApi.getAll(),
    ]).then(([d, b, t, r, s]) => {
      if (d.status === 'fulfilled') setDepartments(d.value)
      if (b.status === 'fulfilled') setAllBatches(b.value)
      if (t.status === 'fulfilled') setAllTeachers(t.value)
      if (r.status === 'fulfilled') setAllRooms(r.value)
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
              name: `${parent.name} (re-solve)`,
            }))
          }
        }
      }
      const failed = [d, b, t, r, s].filter((x) => x.status === 'rejected').length
      if (failed > 0) {
        setError(`Some prerequisites failed to load (${failed}/5)`)
      }
    })
  }, [])

  const visibleBatches = form.scope === 'DEPARTMENT' && form.departmentId
    ? allBatches.filter((b) => b.departmentId === form.departmentId)
    : allBatches
  const toggleId = (
    id: number,
    current: number[],
    setter: (ids: number[]) => void,
  ) => {
    setter(current.includes(id) ? current.filter((x) => x !== id) : [...current, id])
  }

  const handleGenerate = async () => {
    if (!form.name.trim()) { setError('Schedule name is required'); return }
    if (form.scope === 'DEPARTMENT' && !form.departmentId) {
      setError('Please select a department for department-scoped scheduling'); return
    }
    if (builderMode && selectedBatchIds.length === 0) {
      setError('Builder mode: please select at least one batch'); return
    }

    setRunning(true)
    setError(null)
    setElapsedSeconds(0)
    setActiveJob(null)

    try {
      const request: ScheduleRequest = {
        ...form,
        batchIds: builderMode && selectedBatchIds.length > 0 ? selectedBatchIds : undefined,
        teacherIds: builderMode && selectedTeacherIds.length > 0 ? selectedTeacherIds : undefined,
        roomIds: builderMode && selectedRoomIds.length > 0 ? selectedRoomIds : undefined,
      }
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
          const request: ScheduleRequest = {
            ...form,
            batchIds: builderMode && selectedBatchIds.length > 0 ? selectedBatchIds : undefined,
            teacherIds: builderMode && selectedTeacherIds.length > 0 ? selectedTeacherIds : undefined,
            roomIds: builderMode && selectedRoomIds.length > 0 ? selectedRoomIds : undefined,
          }
          const result = await scheduleApi.checkFeasibility(request)
          setFeasibility(result)
          if (wizardStep < 4) setWizardStep(4)
        } catch {
          // Keep original generation error visible if feasibility endpoint also fails.
        }
      }
    }
  }

  const handleJobFinished = (job: SolveJobResponse) => {
    setRunning(false)
    setJobStartedAt(null)
    if (job.status === 'SUCCEEDED') {
      if (job.scheduleId) navigate(`/schedule/view/${job.scheduleId}`)
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
      const req = {
        ...form,
        batchIds: builderMode && selectedBatchIds.length > 0 ? selectedBatchIds : undefined,
        teacherIds: builderMode && selectedTeacherIds.length > 0 ? selectedTeacherIds : undefined,
        roomIds: builderMode && selectedRoomIds.length > 0 ? selectedRoomIds : undefined,
      }
      const result = await scheduleApi.checkFeasibility(req)
      setFeasibility(result)
      if (wizardStep < 4) setWizardStep(4)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Feasibility check failed')
    } finally {
      setCheckingFeasibility(false)
    }
  }

  const deptOptions = departments.map((d) => ({ value: d.id, label: `${d.name} (${d.code})` }))
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
    if (!canNext || wizardStep >= 4) return
    setWizardStep((x) => Math.min(4, x + 1))
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
              <p className="text-xs uppercase tracking-[0.14em] text-gray-500">ARARE Engine Wizard</p>
              <h2 className="text-xl font-semibold">Schedule Command Center</h2>
            </div>
            <div className="inline-flex items-center gap-2 rounded-full border border-cyan-200 bg-cyan-50 px-3 py-1 text-xs text-cyan-700">
              <Wand2 size={13} />
              Premium workflow mode
            </div>
          </div>

          <ol className="grid md:grid-cols-4 gap-2">
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
                  setForm({ ...form, scope: e.target.value as ScheduleScope, departmentId: undefined })
                  setSelectedBatchIds([])
                }}
                options={SCOPE_OPTIONS}
              />
              {form.scope === 'DEPARTMENT' && (
                <Select
                  label="Department"
                  value={form.departmentId ?? ''}
                  onChange={(e) => {
                    setForm({ ...form, departmentId: +e.target.value || undefined })
                    setSelectedBatchIds([])
                  }}
                  options={deptOptions}
                  placeholder="Select department"
                  helpText="Only batches and subjects from this department will be scheduled"
                />
              )}
              {allSchedules.length > 0 && (
                <Select
                  label="Derive from existing schedule"
                  value={form.parentScheduleId ?? ''}
                  onChange={(e) => setForm({ ...form, parentScheduleId: +e.target.value || undefined })}
                  options={parentOptions}
                  helpText="Optional: re-solve from a prior schedule with lock inheritance"
                />
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
                    <div className="grid grid-cols-2 gap-2">
                      {allTeachers.map((t) => (
                        <label key={t.id} className="flex items-center gap-2 text-sm cursor-pointer text-gray-800">
                          <input
                            type="checkbox"
                            checked={selectedTeacherIds.includes(t.id)}
                            onChange={() => toggleId(t.id, selectedTeacherIds, setSelectedTeacherIds)}
                          />
                          {t.name}
                        </label>
                      ))}
                    </div>
                  </Card>

                  <Card title="Rooms" className="bg-white border-gray-200 text-gray-900">
                    <div className="grid grid-cols-2 gap-2">
                      {allRooms.map((r) => (
                        <label key={r.id} className="flex items-center gap-2 text-sm cursor-pointer text-gray-800">
                          <input
                            type="checkbox"
                            checked={selectedRoomIds.includes(r.id)}
                            onChange={() => toggleId(r.id, selectedRoomIds, setSelectedRoomIds)}
                          />
                          {r.roomNumber} [{r.type}]
                        </label>
                      ))}
                    </div>
                  </Card>
                </>
              )}
            </div>
          )}

           {wizardStep === 3 && (
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
                 </p>
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
            <Button variant="secondary" onClick={gotoPrevStep} disabled={wizardStep === 1} icon={<ArrowLeft size={14} />}>
              Back
            </Button>
            <Button onClick={gotoNextStep} disabled={wizardStep === 4 || !canNext} icon={<ArrowRight size={14} />}>
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
  const { job: freshJob, done, cancel } = useSolveJobPoll(job, onDone)

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
