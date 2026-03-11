import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Zap } from 'lucide-react'
import { Card, Button, Input, Select } from '../components/ui'
import { scheduleApi } from '../services/api'
import type { ScheduleRequest, ScheduleScope } from '../types'

const SCOPE_OPTIONS: { value: ScheduleScope; label: string }[] = [
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'COLLEGE', label: 'College' },
  { value: 'UNIVERSITY', label: 'University' },
]

export default function ScheduleGenerator() {
  const navigate = useNavigate()
  const [form, setForm] = useState<ScheduleRequest>({
    name: `Schedule ${new Date().toLocaleDateString()}`,
    scope: 'DEPARTMENT',
  })
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleGenerate = async () => {
    if (!form.name.trim()) { setError('Schedule name is required'); return }
    setRunning(true)
    setError(null)
    try {
      const schedule = await scheduleApi.generate(form)
      navigate(`/schedule/view/${schedule.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Generation failed')
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="max-w-lg">
      <Card
        title="Generate Timetable"
        description="The solver will optimise teacher, room, and timeslot assignments. This may take up to 30 seconds."
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
            value={form.scope ?? 'DEPARTMENT'}
            onChange={(e) => setForm({ ...form, scope: e.target.value as ScheduleScope })}
            options={SCOPE_OPTIONS}
          />
          <div className="rounded-md bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800">
            Ensure all buildings, rooms, teachers, subjects, batches, and timeslots are configured before generating.
          </div>
          <div className="flex justify-end">
            <Button
              size="lg"
              loading={running}
              icon={<Zap size={18} />}
              onClick={handleGenerate}
            >
              {running ? 'Solving…' : 'Generate Schedule'}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
