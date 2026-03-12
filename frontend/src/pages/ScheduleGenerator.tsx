import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Zap, Settings, Clock, GitBranch, ShieldCheck, AlertTriangle, XCircle, CheckCircle } from 'lucide-react'
import { Card, Button, Input, Select } from '../components/ui'
import { scheduleApi, departmentApi, batchApi, teacherApi, roomApi } from '../services/api'
import type { ScheduleRequest, ScheduleScope, Department, Batch, Teacher, Room, Schedule, FeasibilityCheckResult } from '../types'

const SCOPE_OPTIONS: { value: ScheduleScope; label: string }[] = [
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'COLLEGE', label: 'College' },
  { value: 'UNIVERSITY', label: 'University' },
]

const TIME_MARKS = [10, 30, 60, 120, 300]

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
  const [builderMode, setBuilderMode] = useState(false)
  const [selectedBatchIds, setSelectedBatchIds] = useState<number[]>([])
  const [selectedTeacherIds, setSelectedTeacherIds] = useState<number[]>([])
  const [selectedRoomIds, setSelectedRoomIds] = useState<number[]>([])
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [feasibility, setFeasibility] = useState<FeasibilityCheckResult | null>(null)
  const [checkingFeasibility, setCheckingFeasibility] = useState(false)

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
      // Pre-fill parent if navigated from history
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
    }).catch(() => {})
  }, [])

  // Filter batches by selected department in department scope
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
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Feasibility check failed')
    } finally {
      setCheckingFeasibility(false)
    }
  }

  const deptOptions = departments.map((d) => ({ value: d.id, label: `${d.name} (${d.code})` }))
  const parentOptions = [
    { value: '', label: '— None (generate from scratch) —' },
    ...allSchedules.map((s) => ({ value: s.id, label: `${s.name} [${s.score ?? s.status}]` })),
  ]

  const timeLabel = (s: number) =>
    s < 60 ? `${s}s` : `${s / 60}m`

  return (
    <div className="max-w-2xl space-y-4">
      <Card
        title="Generate Timetable"
        description="The solver will optimise teacher, room, and timeslot assignments."
      >
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
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
              placeholder="Select department…"
              helpText="Only batches and subjects from this department will be scheduled"
            />
          )}
          {(form.scope === 'COLLEGE' || form.scope === 'UNIVERSITY') && (
            <div className="rounded-md bg-blue-50 border border-blue-200 px-4 py-3 text-sm text-blue-800">
              All departments will be scheduled together. Make sure all data is configured.
            </div>
          )}

          {/* Parent schedule */}
          {allSchedules.length > 0 && (
            <Select
              label="Derive from existing schedule"
              value={form.parentScheduleId ?? ''}
              onChange={(e) => setForm({ ...form, parentScheduleId: +e.target.value || undefined })}
              options={parentOptions}
              helpText="Optional: re-solve starting from a previous schedule's locked sessions"
            />
          )}
          {form.parentScheduleId && (
            <div className="flex items-center gap-2 rounded-md bg-indigo-50 border border-indigo-200 px-4 py-3 text-sm text-indigo-800">
              <GitBranch size={14} />
              Locked sessions from the parent schedule will be preserved. Only unlocked sessions will be re-optimised.
            </div>
          )}

          {/* Solving time */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="flex items-center gap-1.5 text-sm font-medium text-gray-700">
                <Clock size={13} />
                Solving Time
              </label>
              <span className="text-sm font-semibold text-indigo-600">
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
              className="w-full accent-indigo-600"
            />
            <div className="flex justify-between text-xs text-gray-400 mt-1">
              {TIME_MARKS.map((m) => <span key={m}>{timeLabel(m)}</span>)}
            </div>
            <p className="text-xs text-gray-400 mt-1">
              Longer solving time generally produces better results. 30s is recommended for most cases.
            </p>
          </div>

          {/* Mode toggle */}
          <div className="flex items-center gap-3 pt-1">
            <span className="text-sm font-medium text-gray-700">Mode:</span>
            <button
              type="button"
              onClick={() => setBuilderMode(false)}
              className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                !builderMode
                  ? 'bg-indigo-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              Quick
            </button>
            <button
              type="button"
              onClick={() => setBuilderMode(true)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                builderMode
                  ? 'bg-indigo-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              <Settings size={13} />
              Builder
            </button>
          </div>

          {!builderMode && (
            <div className="rounded-md bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800">
              Quick mode schedules all configured batches, teachers, and rooms for the selected scope.
            </div>
          )}
        </div>
      </Card>

      {/* Builder mode panels */}
      {builderMode && (
        <>
          {/* Batches */}
          <Card title="Batches to Schedule" description="Select which batches to include (required)">
            {visibleBatches.length === 0 ? (
              <p className="text-sm text-gray-400">
                {form.scope === 'DEPARTMENT' && !form.departmentId
                  ? 'Select a department first.'
                  : 'No batches configured.'}
              </p>
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
                  <label key={b.id} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedBatchIds.includes(b.id)}
                      onChange={() => toggleId(b.id, selectedBatchIds, setSelectedBatchIds)}
                    />
                    {b.departmentName ? `${b.departmentName} ` : ''}Yr {b.year}–{b.section}
                    <span className="text-xs text-gray-400">({b.studentCount} students)</span>
                  </label>
                ))}
              </div>
            )}
          </Card>

          {/* Teachers */}
          <Card title="Teachers to Include" description="Leave all unchecked to include all teachers">
            {allTeachers.length === 0 ? (
              <p className="text-sm text-gray-400">No teachers configured.</p>
            ) : (
              <div className="grid grid-cols-2 gap-2">
                <label className="col-span-2 flex items-center gap-2 text-xs text-gray-500 mb-1 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={selectedTeacherIds.length === allTeachers.length}
                    onChange={(e) =>
                      setSelectedTeacherIds(e.target.checked ? allTeachers.map((t) => t.id) : [])
                    }
                  />
                  Select all
                </label>
                {allTeachers.map((t) => (
                  <label key={t.id} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedTeacherIds.includes(t.id)}
                      onChange={() => toggleId(t.id, selectedTeacherIds, setSelectedTeacherIds)}
                    />
                    {t.name}
                  </label>
                ))}
                {selectedTeacherIds.length === 0 && (
                  <p className="col-span-2 text-xs text-gray-400 mt-1">
                    All teachers will be used (no filter applied).
                  </p>
                )}
              </div>
            )}
          </Card>

          {/* Rooms */}
          <Card title="Rooms to Include" description="Leave all unchecked to include all rooms">
            {allRooms.length === 0 ? (
              <p className="text-sm text-gray-400">No rooms configured.</p>
            ) : (
              <div className="grid grid-cols-2 gap-2">
                <label className="col-span-2 flex items-center gap-2 text-xs text-gray-500 mb-1 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={selectedRoomIds.length === allRooms.length}
                    onChange={(e) =>
                      setSelectedRoomIds(e.target.checked ? allRooms.map((r) => r.id) : [])
                    }
                  />
                  Select all
                </label>
                {allRooms.map((r) => (
                  <label key={r.id} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedRoomIds.includes(r.id)}
                      onChange={() => toggleId(r.id, selectedRoomIds, setSelectedRoomIds)}
                    />
                    {r.roomNumber}
                    {r.buildingName ? ` (${r.buildingName})` : ''}
                    <span className="text-xs text-gray-400">[{r.type}]</span>
                  </label>
                ))}
                {selectedRoomIds.length === 0 && (
                  <p className="col-span-2 text-xs text-gray-400 mt-1">
                    All rooms will be used (no filter applied).
                  </p>
                )}
              </div>
            )}
          </Card>
        </>
      )}

      <Card>
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
            {running ? `Solving (up to ${timeLabel(form.solvingTimeSeconds ?? 30)})…` : 'Generate Schedule'}
          </Button>
        </div>
      </Card>

      {/* Feasibility Check Result */}
      {feasibility && (
        <Card>
          <div className="space-y-3">
            {/* Summary banner */}
            <div className={`flex items-center gap-3 rounded-lg px-4 py-3 ${
              feasibility.feasible
                ? feasibility.warningCount > 0
                  ? 'bg-amber-50 border border-amber-200'
                  : 'bg-green-50 border border-green-200'
                : 'bg-red-50 border border-red-200'
            }`}>
              {feasibility.feasible
                ? feasibility.warningCount > 0
                  ? <AlertTriangle size={18} className="text-amber-600 shrink-0" />
                  : <CheckCircle size={18} className="text-green-600 shrink-0" />
                : <XCircle size={18} className="text-red-600 shrink-0" />
              }
              <div className="text-sm">
                <span className={`font-semibold ${
                  feasibility.feasible
                    ? feasibility.warningCount > 0 ? 'text-amber-800' : 'text-green-800'
                    : 'text-red-800'
                }`}>
                  {feasibility.feasible ? (feasibility.warningCount > 0 ? 'Likely feasible with warnings' : 'Looks good!') : 'Infeasible — fix errors before generating'}
                </span>
                <span className="ml-2 text-gray-500 text-xs">
                  ~{feasibility.totalSessionsEstimate} sessions · {feasibility.availableTimeslots} timeslots
                  {feasibility.errorCount > 0 && ` · ${feasibility.errorCount} error${feasibility.errorCount !== 1 ? 's' : ''}`}
                  {feasibility.warningCount > 0 && ` · ${feasibility.warningCount} warning${feasibility.warningCount !== 1 ? 's' : ''}`}
                </span>
              </div>
            </div>

            {/* Issue list */}
            {feasibility.issues.length > 0 && (
              <div className="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden text-sm">
                {feasibility.issues.map((issue, i) => (
                  <div key={i} className={`flex gap-3 px-4 py-2.5 ${issue.severity === 'ERROR' ? 'bg-red-50' : 'bg-amber-50'}`}>
                    {issue.severity === 'ERROR'
                      ? <XCircle size={14} className="text-red-500 shrink-0 mt-0.5" />
                      : <AlertTriangle size={14} className="text-amber-500 shrink-0 mt-0.5" />
                    }
                    <div>
                      <span className={`text-xs font-semibold uppercase tracking-wide mr-2 ${issue.severity === 'ERROR' ? 'text-red-600' : 'text-amber-600'}`}>
                        {issue.category}
                      </span>
                      <span className="text-gray-700">{issue.message}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {feasibility.issues.length === 0 && (
              <p className="text-sm text-green-700 text-center py-2">
                No issues found. The solver should produce a feasible schedule.
              </p>
            )}
          </div>
        </Card>
      )}
    </div>
  )
}
