import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, CheckCircle, Clock, GitBranch, Settings, ShieldCheck, Sparkles, Wand2, Zap } from 'lucide-react'
import { Card, Button, Input, Select } from '../components/ui'
import SolverProgressDashboard from '../components/solver/SolverProgressDashboard'
import { scheduleApi, departmentApi, batchApi, teacherApi, roomApi } from '../services/api'
import type { ScheduleRequest, ScheduleScope, Department, Batch, Teacher, Room, Schedule, FeasibilityCheckResult } from '../types'

const SCOPE_OPTIONS: { value: ScheduleScope; label: string }[] = [
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'COLLEGE', label: 'College' },
  { value: 'UNIVERSITY', label: 'University' },
]

const TIME_MARKS = [10, 30, 60, 120, 300]
const WIZARD_STEPS = [
  { id: 1, label: 'Scope Selection' },
  { id: 2, label: 'Resource Selection' },
  { id: 3, label: 'Constraint Priorities' },
  { id: 4, label: 'Solver Tuning' },
]

const INSIGHT_LIBRARY = [
  'Checking 14,000 combinations of teacher-room-day chains...',
  'Rebalancing room capacity pressure across constrained labs...',
  'Minimizing midday break violations for high-load batches...',
  'Pruning low-feasibility branches with hard-constraint checks...',
  'Optimizing subject spread to reduce same-day cognitive load...',
  'Testing building-switch tradeoffs for consecutive sessions...',
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

  const [priorityProfile, setPriorityProfile] = useState<'balanced' | 'teacher-first' | 'student-first'>('balanced')
  const [spreadWeight, setSpreadWeight] = useState(6)
  const [travelWeight, setTravelWeight] = useState(5)

  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [feasibility, setFeasibility] = useState<FeasibilityCheckResult | null>(null)
  const [checkingFeasibility, setCheckingFeasibility] = useState(false)

  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [bestScore, setBestScore] = useState(-1200)
  const [scorePoints, setScorePoints] = useState<{ t: number; score: number }[]>([{ t: 0, score: -1200 }])
  const [insights, setInsights] = useState<string[]>(INSIGHT_LIBRARY.slice(0, 4))

  useEffect(() => {
    Promise.all([
      departmentApi.getAll(),
      batchApi.getAll(),
      teacherApi.getAll(),
      roomApi.getAll(),
      scheduleApi.getAll(),
    ]).then(([d, b, t, r, s]) => {
      setDepartments(d)
      setAllBatches(b)
      setAllTeachers(t)
      setAllRooms(r)
      setAllSchedules(s)
      const parentId = searchParams.get('parentId')
      if (parentId) {
        const parent = s.find((sc: Schedule) => sc.id === +parentId)
        if (parent) {
          setForm((prev) => ({
            ...prev,
            parentScheduleId: parent.id,
            scope: parent.scope,
            name: `${parent.name} (re-solve)`,
          }))
        }
      }
    }).catch((e) => {
      setError(e instanceof Error ? e.message : 'Failed to load scheduling prerequisites')
    })
  }, [])

  useEffect(() => {
    if (!running) return
    const startedAt = Date.now()
    const timer = window.setInterval(() => {
      const elapsed = Math.max(0, Math.round((Date.now() - startedAt) / 1000))
      setElapsedSeconds(elapsed)

      setBestScore((prev) => {
        const gain = Math.floor(Math.random() * 25) + 4
        const next = prev + gain
        setScorePoints((pts) => {
          const nextPoints = [...pts, { t: elapsed, score: next }]
          return nextPoints.slice(-24)
        })
        return next
      })

      setInsights((prev) => {
        const nextInsight = INSIGHT_LIBRARY[elapsed % INSIGHT_LIBRARY.length]
        const next = [nextInsight, ...prev.filter((x) => x !== nextInsight)]
        return next.slice(0, 4)
      })
    }, 900)

    return () => window.clearInterval(timer)
  }, [running])

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
    setBestScore(-1200)
    setScorePoints([{ t: 0, score: -1200 }])
    setInsights(INSIGHT_LIBRARY.slice(0, 4))

    try {
      const request: ScheduleRequest = {
        ...form,
        batchIds: builderMode && selectedBatchIds.length > 0 ? selectedBatchIds : undefined,
        teacherIds: builderMode && selectedTeacherIds.length > 0 ? selectedTeacherIds : undefined,
        roomIds: builderMode && selectedRoomIds.length > 0 ? selectedRoomIds : undefined,
      }
      const schedule = await scheduleApi.generate(request)
      navigate(`/schedule/view/${schedule.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Generation failed')
    } finally {
      setRunning(false)
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
      {running && (
        <SolverProgressDashboard
          elapsedSeconds={elapsedSeconds}
          targetSeconds={form.solvingTimeSeconds ?? 30}
          bestScore={bestScore}
          insights={insights}
          scorePoints={scorePoints}
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
              <Select
                label="Priority Profile"
                value={priorityProfile}
                onChange={(e) => setPriorityProfile(e.target.value as 'balanced' | 'teacher-first' | 'student-first')}
                options={[
                  { value: 'balanced', label: 'Balanced (recommended)' },
                  { value: 'teacher-first', label: 'Teacher comfort first' },
                  { value: 'student-first', label: 'Student flow first' },
                ]}
                helpText="Profiles are advisory presets for operator intent and review visibility."
              />

              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-sm font-medium text-gray-700">Subject Spread Weight</label>
                  <span className="text-xs text-cyan-700">{spreadWeight}/10</span>
                </div>
                <input
                  type="range"
                  min={1}
                  max={10}
                  step={1}
                  value={spreadWeight}
                  onChange={(e) => setSpreadWeight(+e.target.value)}
                  className="w-full accent-cyan-500"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-sm font-medium text-gray-700">Travel Penalty Weight</label>
                  <span className="text-xs text-cyan-700">{travelWeight}/10</span>
                </div>
                <input
                  type="range"
                  min={1}
                  max={10}
                  step={1}
                  value={travelWeight}
                  onChange={(e) => setTravelWeight(+e.target.value)}
                  className="w-full accent-cyan-500"
                />
              </div>

              <div className="rounded-md bg-gray-50 border border-gray-200 px-4 py-3 text-sm text-gray-700">
                <div className="flex items-center gap-2 mb-1">
                  <Sparkles size={14} className="text-emerald-600" />
                  Profile summary
                </div>
                <p>
                  {priorityProfile === 'balanced' && 'Balanced blend of teacher workload and student timetable smoothness.'}
                  {priorityProfile === 'teacher-first' && 'Stronger preference for fewer teacher building switches and better free-day alignment.'}
                  {priorityProfile === 'student-first' && 'Stronger preference for cleaner student flow and wider subject distribution.'}
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
                  icon={<Zap size={18} />}
                  onClick={handleGenerate}
                >
                  {running ? `Solving (${timeLabel(form.solvingTimeSeconds ?? 30)})...` : 'Launch Solver'}
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
