import { useState, useEffect, useMemo } from 'react'
import { Card, Button, Input, AvailabilityPainter } from '../components/ui'
import { universityConfigApi, timeslotApi } from '../services/api'
import type { UniversityConfig, SchoolDay } from '../types'
import type { Timeslot } from '../types'

const ALL_DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const DAY_PRESETS: { label: string; days: SchoolDay[] }[] = [
  { label: 'Mon – Fri', days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] },
  { label: 'Mon – Sat', days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'] },
  { label: 'Mon – Sun', days: ALL_DAYS },
]

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
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [missingConfig, setMissingConfig] = useState(false)
  const [paintedAvailableTimeslotIds, setPaintedAvailableTimeslotIds] = useState<number[]>([])
  const [diagnostics, setDiagnostics] = useState<any>(null)

  const painterTimeslots = useMemo<Timeslot[]>(() => {
    const days = form.workingDays.length > 0 ? form.workingDays : ALL_DAYS.slice(0, Math.max(1, Math.min(7, form.daysPerWeek)))
    const slotsPerDay = Math.max(1, form.timeslotsPerDay || 1)
    const preview: Timeslot[] = []
    let nextId = 100000

    for (const day of days) {
      for (let slot = 0; slot < slotsPerDay; slot++) {
        const existing = timeslots.find((t) => t.day === day && t.slotNumber === slot + 1 && t.type === 'CLASS')
        if (existing) {
          preview.push(existing)
        } else {
          const hour = 9 + slot
          const startHour = String(hour).padStart(2, '0')
          const endHour = String(hour + 1).padStart(2, '0')
          preview.push({
            id: nextId++,
            day,
            startTime: `${startHour}:00`,
            endTime: `${endHour}:00`,
            slotNumber: slot,
            type: 'CLASS',
          })
        }
      }
    }
    return preview
  }, [timeslots, form.workingDays, form.daysPerWeek, form.timeslotsPerDay])

  const loadData = () => {
    Promise.allSettled([
      universityConfigApi.get(),
      timeslotApi.getAll(),
      universityConfigApi.diagnostics()
    ])
      .then(([cfgRes, tsRes, diagRes]) => {
        if (cfgRes.status === 'fulfilled' && cfgRes.value) {
          setForm(cfgRes.value)
        } else {
          setMissingConfig(true)
        }
        if (tsRes.status === 'fulfilled') {
          setTimeslots(tsRes.value)
        }
        if (diagRes.status === 'fulfilled' && diagRes.value) {
          setDiagnostics(diagRes.value)
        }
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load configuration'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadData()
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
    const workingCount = form.workingDays.length
    if (form.daysPerWeek !== workingCount) {
      setError(`Days per week (${form.daysPerWeek}) must match the number of working days selected (${workingCount})`)
      return
    }
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      await universityConfigApi.save(form)
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
      loadData()
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
          {missingConfig && (
            <div className="rounded-lg bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800">
              No configuration has been saved yet — showing defaults. Saving will create the first
              university configuration record.
            </div>
          )}

          <div>
            <p className="block text-sm font-medium text-gray-700 mb-1">Working Days</p>
            <div className="flex flex-wrap items-center gap-2 mb-3">
              {DAY_PRESETS.map((preset) => (
                <button
                  key={preset.label}
                  type="button"
                  onClick={() => setForm((prev) => ({
                    ...prev,
                    workingDays: [...preset.days],
                    daysPerWeek: preset.days.length,
                  }))}
                  className="px-3 py-1.5 rounded-md text-xs font-medium border transition-colors bg-gray-50 text-gray-700 border-gray-300 hover:border-primary-400 hover:bg-primary-50"
                >
                  {preset.label}
                </button>
              ))}
              <span className="text-xs text-gray-400">or pick days below</span>
            </div>
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
              min={1}
              max={7}
              value={form.daysPerWeek}
              onChange={(e) => setForm({ ...form, daysPerWeek: +e.target.value })}
              helpText="1 to 7 days — the working days selected above must match this count"
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
              Select which slots should be reserved for breaks.
            </p>
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: Math.max(1, form.timeslotsPerDay || 1) }).map((_, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => {
                    const indices = form.breakSlotIndices.includes(idx)
                      ? form.breakSlotIndices.filter((i) => i !== idx)
                      : [...form.breakSlotIndices, idx].sort((a, b) => a - b)
                    setForm({ ...form, breakSlotIndices: indices })
                  }}
                  className={`px-3 py-1 rounded text-sm font-medium border transition-colors ${
                    form.breakSlotIndices.includes(idx)
                      ? 'bg-amber-100 text-amber-800 border-amber-300'
                      : 'bg-white text-gray-600 border-gray-300 hover:border-amber-200'
                  }`}
                >
                  Slot {idx + 1}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="block text-sm font-medium text-gray-700 mb-1">Availability Preview</p>
            <p className="text-xs text-gray-500 mb-2">
              Visualizes the operating window implied by the current configuration. This grid is
              informational only — painting here does not change saved settings. Use the Working Days,
              Timeslots per Day, and Break Slot controls above to configure the actual schedule.
            </p>
            {timeslots.filter((ts) => ts.type === 'CLASS').length === 0 && (
              <p className="text-xs text-amber-700 mb-2">
                No class timeslots found yet. Showing a generated preview grid based on current configuration.
              </p>
            )}
            <AvailabilityPainter
              timeslots={painterTimeslots}
              selectedIds={paintedAvailableTimeslotIds}
              onChange={setPaintedAvailableTimeslotIds}
              days={form.workingDays.length > 0 ? form.workingDays : undefined}
            />
          </div>

          <div className="flex justify-end pt-2">
            <Button loading={saving} onClick={handleSave}>Save Configuration</Button>
          </div>
        </div>
      </Card>

      {diagnostics && (
        <Card title="Configuration Diagnostics" description="System viability based on the active config">
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className={`w-3 h-3 rounded-full ${diagnostics.valid ? 'bg-green-500' : 'bg-red-500'}`}></span>
              <span className="font-medium text-gray-900">{diagnostics.valid ? 'Valid Configuration' : 'Configuration has issues'}</span>
            </div>
            <p className="text-sm text-gray-600">{diagnostics.summary}</p>
            {diagnostics.issues && diagnostics.issues.length > 0 && (
              <ul className="list-disc pl-5 text-sm text-red-600 space-y-1">
                {diagnostics.issues.map((issue: string, i: number) => (
                  <li key={i}>{issue}</li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      )}
    </div>
  )
}
