import { useState, useEffect } from 'react'
import { Card, Button, Input } from '../components/ui'
import { universityConfigApi } from '../services/api'
import type { UniversityConfig, SchoolDay } from '../types'

const ALL_DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const DEFAULT_CONFIG: UniversityConfig = {
  active: true,
  daysPerWeek: 5,
  timeslotsPerDay: 8,
  maxClassesPerDay: 6,
  breakSlotIndices: [3],
  workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
}

export default function UniversityConfigPage() {
  const [form, setForm] = useState<UniversityConfig>(DEFAULT_CONFIG)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    universityConfigApi.get()
      .then((cfg) => { if (cfg) setForm(cfg) })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const toggleDay = (day: SchoolDay) => {
    setForm((prev) => ({
      ...prev,
      workingDays: prev.workingDays.includes(day)
        ? prev.workingDays.filter((d) => d !== day)
        : [...prev.workingDays, day],
    }))
  }

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      await universityConfigApi.save(form)
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="animate-pulse h-64 bg-gray-100 rounded-lg" />

  return (
    <div className="max-w-2xl space-y-6">
      {error && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      {saved && (
        <div className="rounded-lg bg-green-50 border border-green-200 px-4 py-3 text-sm text-green-700">
          Configuration saved successfully.
        </div>
      )}
      <Card title="University Configuration" description="Global scheduling parameters">
        <div className="space-y-6">
          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">Working Days</p>
            <div className="flex flex-wrap gap-2">
              {ALL_DAYS.map((day) => (
                <button
                  key={day}
                  type="button"
                  onClick={() => toggleDay(day)}
                  className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-colors ${
                    form.workingDays.includes(day)
                      ? 'bg-primary-600 text-white border-primary-600'
                      : 'bg-white text-gray-600 border-gray-300 hover:border-primary-400'
                  }`}
                >
                  {day.slice(0, 3)}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Days per Week"
              type="number"
              min={5}
              max={6}
              value={form.daysPerWeek}
              onChange={(e) => setForm({ ...form, daysPerWeek: +e.target.value })}
              helpText="5 or 6 days"
            />
            <Input
              label="Timeslots per Day"
              type="number"
              min={1}
              value={form.timeslotsPerDay}
              onChange={(e) => setForm({ ...form, timeslotsPerDay: +e.target.value })}
            />
            <Input
              label="Max Classes per Day"
              type="number"
              min={1}
              value={form.maxClassesPerDay}
              onChange={(e) => setForm({ ...form, maxClassesPerDay: +e.target.value })}
            />
          </div>

          <div>
            <p className="block text-sm font-medium text-gray-700 mb-1">Break Slot Indices</p>
            <p className="text-xs text-gray-500 mb-2">
              Comma-separated 0-based slot indices that are reserved for breaks.
            </p>
            <input
              className="input"
              value={form.breakSlotIndices.join(', ')}
              onChange={(e) => {
                const indices = e.target.value
                  .split(',')
                  .map((n) => parseInt(n.trim()))
                  .filter((n) => !isNaN(n))
                setForm({ ...form, breakSlotIndices: indices })
              }}
              placeholder="3, 6"
            />
          </div>

          <div className="flex justify-end pt-2">
            <Button loading={saving} onClick={handleSave}>Save Configuration</Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
