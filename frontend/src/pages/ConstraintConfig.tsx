import { useMemo, useState } from 'react'
import { Card, Button } from '../components/ui'

type Level = 'Hard' | 'Medium' | 'Soft'

interface TunableConstraint {
  key: string
  name: string
  level: Level
  description: string
  value: number
}

const DEFAULTS: TunableConstraint[] = [
  { key: 'teacher-conflict', name: 'Teacher Conflict', level: 'Hard', description: 'Prevent teacher overlap.', value: 10 },
  { key: 'room-conflict', name: 'Room Conflict', level: 'Hard', description: 'Prevent room overlap.', value: 10 },
  { key: 'batch-conflict', name: 'Batch Conflict', level: 'Hard', description: 'Prevent batch overlap.', value: 10 },
  { key: 'capacity', name: 'Room Capacity', level: 'Hard', description: 'Avoid undersized room assignment.', value: 9 },
  { key: 'teacher-daily', name: 'Teacher Daily Hours', level: 'Medium', description: 'Reduce daily overload.', value: 6 },
  { key: 'teacher-weekly', name: 'Teacher Weekly Hours', level: 'Medium', description: 'Reduce weekly overload.', value: 6 },
  { key: 'teacher-consecutive', name: 'Consecutive Classes', level: 'Medium', description: 'Limit long back-to-back runs.', value: 7 },
  { key: 'student-gap', name: 'Student Idle Gaps', level: 'Medium', description: 'Minimize timetable holes.', value: 7 },
  { key: 'teacher-gap', name: 'Teacher Idle Gaps', level: 'Medium', description: 'Minimize teacher idle windows.', value: 6 },
  { key: 'subject-spread', name: 'Subject Spread', level: 'Soft', description: 'Distribute subject sessions across week.', value: 6 },
  { key: 'teacher-building', name: 'Teacher Building Preference', level: 'Soft', description: 'Respect preferred buildings.', value: 5 },
  { key: 'room-stability', name: 'Room Stability', level: 'Soft', description: 'Keep same room for repeated sessions.', value: 5 },
]

const levelChip = (level: Level) =>
  level === 'Hard'
    ? 'bg-rose-100 text-rose-700'
    : level === 'Medium'
      ? 'bg-amber-100 text-amber-700'
      : 'bg-emerald-100 text-emerald-700'

export default function ConstraintConfig() {
  const [constraints, setConstraints] = useState<TunableConstraint[]>(DEFAULTS)
  const [saved, setSaved] = useState(false)

  const summary = useMemo(() => {
    const hard = constraints.filter((c) => c.level === 'Hard').reduce((a, b) => a + b.value, 0)
    const medium = constraints.filter((c) => c.level === 'Medium').reduce((a, b) => a + b.value, 0)
    const soft = constraints.filter((c) => c.level === 'Soft').reduce((a, b) => a + b.value, 0)
    return { hard, medium, soft }
  }, [constraints])

  const updateValue = (key: string, next: number) => {
    setConstraints((prev) => prev.map((c) => (c.key === key ? { ...c, value: next } : c)))
  }

  return (
    <div className="space-y-4">
      <Card title="Constraint Tuning Panel" description="Adjust optimization emphasis before generation.">
        <div className="grid md:grid-cols-3 gap-3 text-sm">
          <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">Hard total: <strong>{summary.hard}</strong></div>
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">Medium total: <strong>{summary.medium}</strong></div>
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2">Soft total: <strong>{summary.soft}</strong></div>
        </div>
      </Card>

      {(['Hard', 'Medium', 'Soft'] as const).map((level) => (
        <Card key={level} title={`${level} Weights`}>
          <div className="space-y-3">
            {constraints.filter((c) => c.level === level).map((constraint) => (
              <div key={constraint.key} className="rounded-lg border border-slate-200 px-4 py-3">
                <div className="flex items-center justify-between gap-2 mb-2">
                  <div>
                    <p className="text-sm font-semibold text-slate-900">{constraint.name}</p>
                    <p className="text-xs text-slate-500">{constraint.description}</p>
                  </div>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${levelChip(level)}`}>{level}</span>
                </div>
                <div className="flex items-center gap-3">
                  <input
                    type="range"
                    min={1}
                    max={10}
                    step={1}
                    value={constraint.value}
                    onChange={(e) => updateValue(constraint.key, +e.target.value)}
                    className="w-full accent-indigo-600"
                  />
                  <span className="w-8 text-right text-sm font-semibold text-indigo-700">{constraint.value}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      ))}

      <Card>
        <div className="flex items-center justify-end gap-2">
          <Button variant="secondary" onClick={() => setConstraints(DEFAULTS)}>Reset</Button>
          <Button onClick={() => { setSaved(true); window.setTimeout(() => setSaved(false), 2500) }}>
            Save Tuning Profile
          </Button>
        </div>
        {saved && <p className="text-sm text-emerald-700 mt-2">Tuning profile saved locally for this session.</p>}
      </Card>
    </div>
  )
}
