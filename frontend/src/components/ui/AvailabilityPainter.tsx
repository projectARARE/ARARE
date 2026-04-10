import { useMemo, useRef, useState } from 'react'
import type { SchoolDay, Timeslot } from '../../types'

interface AvailabilityPainterProps {
  timeslots: Timeslot[]
  selectedIds: number[]
  onChange: (ids: number[]) => void
  mode?: 'available' | 'blocked'
}

const DAY_ORDER: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

export default function AvailabilityPainter({ timeslots, selectedIds, onChange, mode = 'available' }: AvailabilityPainterProps) {
  const [painting, setPainting] = useState(false)
  const [paintValue, setPaintValue] = useState<boolean>(true)
  const touchedRef = useRef<Set<number>>(new Set())

  const classSlots = useMemo(() =>
    timeslots
      .filter((ts) => ts.type === 'CLASS')
      .sort((a, b) => {
        if (a.day !== b.day) return DAY_ORDER.indexOf(a.day) - DAY_ORDER.indexOf(b.day)
        const slotCmp = (a.slotNumber ?? Number.MAX_SAFE_INTEGER) - (b.slotNumber ?? Number.MAX_SAFE_INTEGER)
        if (slotCmp !== 0) return slotCmp
        return a.startTime.localeCompare(b.startTime)
      }),
  [timeslots])

  const slotsByDay = useMemo(() => {
    const map: Record<SchoolDay, Timeslot[]> = {
      MONDAY: [], TUESDAY: [], WEDNESDAY: [], THURSDAY: [], FRIDAY: [], SATURDAY: [],
    }
    for (const slot of classSlots) map[slot.day].push(slot)
    return map
  }, [classSlots])

  const timeKeys = useMemo(() => {
    const keys = new Set<string>()
    classSlots.forEach((ts) => keys.add(`${ts.startTime}-${ts.endTime}`))
    return Array.from(keys).sort((a, b) => a.localeCompare(b))
  }, [classSlots])

  const setSelected = (slotId: number, next: boolean) => {
    const set = new Set(selectedIds)
    if (next) set.add(slotId)
    else set.delete(slotId)
    onChange(Array.from(set))
  }

  const paintSlot = (slotId: number) => {
    if (touchedRef.current.has(slotId)) return
    touchedRef.current.add(slotId)
    setSelected(slotId, paintValue)
  }

  return (
    <div className="space-y-2">
      <p className="text-xs text-slate-500">
        Click or click-drag to paint slots as {mode === 'available' ? 'available' : 'blocked'}.
      </p>
      <div className="overflow-x-auto border border-slate-200 rounded-lg">
        <table className="min-w-full border-collapse text-xs">
          <thead>
            <tr className="bg-slate-50">
              <th className="border border-slate-200 px-2 py-2 text-left">Time</th>
              {DAY_ORDER.map((day) => (
                <th key={day} className="border border-slate-200 px-2 py-2">{day.slice(0, 3)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {timeKeys.map((timeKey) => {
              const [start, end] = timeKey.split('-')
              return (
                <tr key={timeKey}>
                  <td className="border border-slate-200 px-2 py-1.5 bg-slate-50">{start} - {end}</td>
                  {DAY_ORDER.map((day) => {
                    const slot = slotsByDay[day].find((s) => `${s.startTime}-${s.endTime}` === timeKey)
                    if (!slot) return <td key={day} className="border border-slate-200 bg-slate-100" />
                    const active = selectedIds.includes(slot.id)
                    return (
                      <td
                        key={day}
                        className={`border border-slate-200 transition-colors ${active ? 'bg-emerald-300/70' : 'bg-white hover:bg-slate-50'} cursor-cell`}
                        onMouseDown={() => {
                          touchedRef.current.clear()
                          const next = !active
                          setPaintValue(next)
                          setPainting(true)
                          paintSlot(slot.id)
                        }}
                        onMouseEnter={() => {
                          if (!painting) return
                          paintSlot(slot.id)
                        }}
                        onMouseUp={() => {
                          setPainting(false)
                          touchedRef.current.clear()
                        }}
                      >
                        <div className="h-6" />
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
