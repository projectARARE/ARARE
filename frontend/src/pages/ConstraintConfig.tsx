import { Card } from '../components/ui'

interface ConstraintInfo {
  name: string
  level: 'Hard' | 'Medium' | 'Soft'
  description: string
}

const constraints: ConstraintInfo[] = [
  { name: 'Teacher conflict', level: 'Hard', description: 'A teacher cannot teach two sessions at the same timeslot.' },
  { name: 'Room conflict', level: 'Hard', description: 'A room cannot host two sessions simultaneously.' },
  { name: 'Batch conflict', level: 'Hard', description: 'A student batch cannot attend two sessions at the same time.' },
  { name: 'Room capacity violation', level: 'Hard', description: 'Room capacity must be at least equal to the student count.' },
  { name: 'Teacher not qualified', level: 'Hard', description: 'Teacher must be listed as qualified for the assigned subject.' },
  { name: 'Teacher unavailable', level: 'Hard', description: 'Teacher must be available at the assigned timeslot.' },
  { name: 'Room unavailable', level: 'Hard', description: 'Room must be available at the assigned timeslot.' },
  { name: 'Break slot violation', level: 'Hard', description: 'Sessions cannot be placed in BREAK or BLOCKED timeslots.' },
  { name: 'Lab must use sections', level: 'Hard', description: 'Lab sessions require a ClassSection; full batches are not allowed.' },
  { name: 'Teacher daily hours cap', level: 'Medium', description: 'Teacher contact hours per day must not exceed their daily maximum.' },
  { name: 'Teacher weekly hours cap', level: 'Medium', description: 'Total weekly teaching hours must not exceed the weekly maximum.' },
  { name: 'Teacher consecutive classes cap', level: 'Medium', description: 'Sessions per day should not exceed max consecutive classes.' },
  { name: 'Student idle gaps', level: 'Medium', description: 'Avoid gaps in student timetables within the same day.' },
  { name: 'Teacher idle gaps', level: 'Medium', description: 'Avoid gaps in teacher schedules within the same day.' },
  { name: 'Same teacher for same subject', level: 'Medium', description: 'A subject should be taught by the same teacher within a batch.' },
  { name: 'Department buildings', level: 'Medium', description: 'Sessions should be held in buildings used by the subject\'s department.' },
  { name: 'Teacher free day', level: 'Soft', description: 'Honor teacher\'s preferred day off.' },
  { name: 'Batch free day', level: 'Soft', description: 'Honor student batch\'s preferred day off.' },
  { name: 'Teacher building preference', level: 'Soft', description: 'Avoid routing teachers to buildings outside their preference.' },
  { name: 'Spread subject across week', level: 'Soft', description: 'Same subject should not appear more than once per day per batch.' },
  { name: 'Cognitive load cap', level: 'Soft', description: 'Subjects should not appear more than their max sessions per day.' },
]

const levelColor = (l: ConstraintInfo['level']) => {
  if (l === 'Hard') return 'bg-red-100 text-red-700'
  if (l === 'Medium') return 'bg-yellow-100 text-yellow-800'
  return 'bg-green-100 text-green-800'
}

export default function ConstraintConfig() {
  return (
    <div className="space-y-6">
      <div className="flex gap-4 text-sm">
        <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-red-500" /> Hard — solver never violates</div>
        <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-yellow-500" /> Medium — avoided after hard constraints</div>
        <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-green-500" /> Soft — best-effort optimisation</div>
      </div>

      {(['Hard', 'Medium', 'Soft'] as const).map((level) => (
        <Card key={level} title={`${level} Constraints`}>
          <ul className="divide-y divide-gray-100">
            {constraints.filter((c) => c.level === level).map((c) => (
              <li key={c.name} className="py-3 flex items-start gap-3">
                <span className={`mt-0.5 px-2 py-0.5 rounded-full text-xs font-medium ${levelColor(level)}`}>{level}</span>
                <div>
                  <p className="text-sm font-medium text-gray-900">{c.name}</p>
                  <p className="text-sm text-gray-500">{c.description}</p>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      ))}
    </div>
  )
}
