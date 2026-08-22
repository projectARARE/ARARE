import { useMemo } from 'react'
import { Plus } from 'lucide-react'
import type { ClassSession, Timeslot, SchoolDay } from '../../types'
import SessionCell from './SessionCell'

const DAYS: SchoolDay[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
]

const DAY_LABELS: Record<SchoolDay, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
}

interface TimetableGridProps {
  sessions: ClassSession[]
  timeslots: Timeslot[]
  activeDays?: SchoolDay[]
  onSessionClick?: (session: ClassSession) => void
  onSessionHover?: (session: ClassSession | null) => void
  onSessionDragStart?: (session: ClassSession) => void
  onSessionDragEnd?: () => void
  onSessionContextMenu?: (session: ClassSession, x: number, y: number) => void
  onSlotDragHover?: (slot: Timeslot | null) => void
  onSlotDrop?: (slot: Timeslot) => void
  onCellContextMenu?: (slot: Timeslot, x: number, y: number) => void
  /** Quick-add affordance: show a "+" on empty cells and fire this on click */
  onSlotAdd?: (slot: Timeslot) => void
  filterBatchId?: number
  filterTeacherId?: number
  filterRoomId?: number
  heatmapEnabled?: boolean
  heatBySessionId?: Record<number, { hard: number; soft: number; notes: string[] }>
  highlightedSessionIds?: Set<number>
  preAllocatedSessionIds?: Set<number>
  dragPreview?: {
    slotId: number
    severity: 'hard' | 'soft' | 'clean'
    label: string
  } | null
  onOrphanSessionsCount?: (count: number) => void
  blockedDays?: SchoolDay[]
}

export default function TimetableGrid({
  sessions,
  timeslots,
  activeDays,
  onSessionClick,
  onSessionHover,
  onSessionDragStart,
  onSessionDragEnd,
  onSessionContextMenu,
  onSlotDragHover,
  onSlotDrop,
  onCellContextMenu,
  onSlotAdd,
  filterBatchId,
  filterTeacherId,
  filterRoomId,
  heatmapEnabled = false,
  heatBySessionId = {},
  highlightedSessionIds,
  preAllocatedSessionIds,
  dragPreview,
  onOrphanSessionsCount,
  blockedDays = [],
}: TimetableGridProps) {
  // Determine which days to show. The canonical order is Mon→Sun, but a day
  // is only rendered if the calendar actually has timeslots on it — so a
  // Sunday-lead or Saturday-off calendar renders exactly its own days.
  const days = useMemo(() => {
    const daysWithSlots = new Set<SchoolDay>(timeslots.map((t) => t.day))
    if (activeDays?.length) {
      return DAYS.filter((d) => activeDays.includes(d) && daysWithSlots.has(d))
    }
    return DAYS.filter((d) => daysWithSlots.has(d))
  }, [timeslots, activeDays])

  // Unique timeslots per day ordered by startTime
  const slotsByDay = useMemo(() => {
    const map: Record<string, Timeslot[]> = {}
    for (const day of days) {
      map[day] = timeslots
        .filter((t) => t.day === day)
        .sort((a, b) => {
          const slotCmp = (a.slotNumber ?? Number.MAX_SAFE_INTEGER) - (b.slotNumber ?? Number.MAX_SAFE_INTEGER)
          if (slotCmp !== 0) return slotCmp
          return a.startTime.localeCompare(b.startTime)
        })
    }
    return map
  }, [timeslots, days])

  // Filtered sessions
  const filteredSessions = useMemo(() => {
    return sessions.filter((s) => {
      if (filterBatchId !== undefined && s.batchId !== filterBatchId) return false
      if (filterTeacherId !== undefined && s.teacherId !== filterTeacherId) return false
      if (filterRoomId !== undefined && s.roomId !== filterRoomId) return false
      return true
    })
  }, [sessions, filterBatchId, filterTeacherId, filterRoomId])

  // Index sessions by day + timeslotId for fast lookup. Sessions whose
  // day/timeslotId is missing OR whose timeslot is not present in the loaded
  // timeslot set are "orphans" — they cannot be rendered into a cell, so they
  // are reported to the caller via onOrphanSessionsCount instead of being
  // silently dropped (the previous behavior hid real data from the operator).
  const sessionIndex = useMemo(() => {
    const index: Record<string, ClassSession[]> = {}
    const timeslotIds = new Set(timeslots.map((t) => t.id))
    const orphans: ClassSession[] = []
    for (const s of filteredSessions) {
      if (!s.day || !s.timeslotId) {
        orphans.push(s)
        continue
      }
      if (!timeslotIds.has(s.timeslotId)) {
        orphans.push(s)
        continue
      }
      const key = `${s.day}:${s.timeslotId}`
      if (!index[key]) index[key] = []
      index[key].push(s)
    }
    // Only report an orphan count once the timeslot set is loaded; otherwise
    // every session would look orphaned purely because timeslots are empty.
    if (timeslots.length > 0) {
      onOrphanSessionsCount?.(orphans.length)
    }
    return index
  }, [filteredSessions, timeslots, onOrphanSessionsCount])

  // All unique timeslot times across all days for the time column
  const allSlotTimes = useMemo(() => {
    const seen = new Set<string>()
    const result: { startTime: string; endTime: string }[] = []
    for (const day of days) {
      for (const slot of slotsByDay[day] ?? []) {
        const key = `${slot.startTime}-${slot.endTime}`
        if (!seen.has(key)) {
          seen.add(key)
          result.push({ startTime: slot.startTime, endTime: slot.endTime })
        }
      }
    }
    return result.sort((a, b) => a.startTime.localeCompare(b.startTime))
  }, [slotsByDay, days])

  const timeKey = (startTime: string, endTime: string) => `${startTime}-${endTime}`

  const spanMeta = useMemo(() => {
    const rowIndexByTime = new Map<string, number>()
    allSlotTimes.forEach((t, idx) => {
      rowIndexByTime.set(timeKey(t.startTime, t.endTime), idx)
    })

    const consumed = new Set<string>()
    const rowSpanByCell = new Map<string, number>()

    for (const s of filteredSessions) {
      if (!s.day || !s.timeslotId || !s.duration || s.duration <= 1) continue

      const daySlots = slotsByDay[s.day] ?? []
      const startIdx = daySlots.findIndex((t) => t.id === s.timeslotId)
      if (startIdx < 0) continue

      const requiredSlots = s.duration
      let coveredSlots = 0
      let endIdx = startIdx

      for (let idx = startIdx; idx < daySlots.length; idx += 1) {
        if (idx > startIdx) {
          const prev = daySlots[idx - 1]
          const current = daySlots[idx]
          // Stop coverage on gaps; multi-slot sessions must occupy contiguous slots.
          if (prev.endTime !== current.startTime) break
        }

        coveredSlots += 1
        endIdx = idx

        if (coveredSlots >= requiredSlots) break
      }

      const startSlot = daySlots[startIdx]
      const endSlot = daySlots[endIdx]

      const startRow = rowIndexByTime.get(timeKey(startSlot.startTime, startSlot.endTime))
      const endRow = rowIndexByTime.get(timeKey(endSlot.startTime, endSlot.endTime))
      if (startRow === undefined || endRow === undefined || endRow <= startRow) continue

      rowSpanByCell.set(`${s.day}:${startRow}`, endRow - startRow + 1)
      for (let r = startRow + 1; r <= endRow; r += 1) {
        consumed.add(`${s.day}:${r}`)
      }
    }

    return { consumed, rowSpanByCell }
  }, [filteredSessions, slotsByDay, allSlotTimes])

  if (timeslots.length === 0) {
    return (
      <div className="text-center py-16 text-gray-400">
        No timeslots configured. Add timeslots first.
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full border-collapse text-sm">
        <thead>
          <tr className="bg-gray-50">
            <th className="border border-gray-200 px-3 py-2 text-left text-xs font-semibold text-gray-500 w-28">
              Time
            </th>
            {days.map((day) => (
              <th
                key={day}
                className="border border-gray-200 px-3 py-2 text-center text-xs font-semibold text-gray-700 min-w-[140px]"
              >
                {DAY_LABELS[day]}
                {blockedDays.includes(day) && (
                  <span className="ml-1.5 align-middle rounded bg-rose-100 text-rose-600 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide">
                    Blocked
                  </span>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {allSlotTimes.map(({ startTime, endTime }, rowIndex) => (
            <tr key={`${startTime}-${endTime}`} className="hover:bg-gray-50/40">
              <td className="border border-gray-200 px-3 py-2 text-xs text-gray-500 whitespace-nowrap bg-gray-50">
                <div className="font-medium">{startTime}</div>
                <div className="opacity-70">– {endTime}</div>
              </td>
              {days.map((day) => {
                const cellKey = `${day}:${rowIndex}`
                if (spanMeta.consumed.has(cellKey)) {
                  return null
                }

                const slot = slotsByDay[day]?.find(
                  (t) => t.startTime === startTime && t.endTime === endTime,
                )
                const cellSessions = slot
                  ? sessionIndex[`${day}:${slot.id}`] ?? []
                  : []
                const rowSpan = spanMeta.rowSpanByCell.get(cellKey) ?? 1
                const activePreview = dragPreview?.slotId === slot?.id ? dragPreview : null
                const isBlockedDay = blockedDays.includes(day)

                return (
                  <td
                    key={day}
                    rowSpan={rowSpan}
                    className={`group border border-gray-200 px-2 py-2 align-top min-h-[60px] ${
                      isBlockedDay ? 'bg-slate-200/50' : ''
                    }`}
                    title={isBlockedDay ? `${DAY_LABELS[day]} is blocked for this schedule` : undefined}
                    onDragOver={(e) => {
                      if (!slot || isBlockedDay) return
                      e.preventDefault()
                      onSlotDragHover?.(slot)
                    }}
                    onDragLeave={() => onSlotDragHover?.(null)}
                    onDrop={(e) => {
                      if (!slot || isBlockedDay) return
                      e.preventDefault()
                      onSlotDrop?.(slot)
                    }}
                    onContextMenu={(e) => {
                      if (!slot || isBlockedDay) return
                      e.preventDefault()
                      onCellContextMenu?.(slot, e.clientX, e.clientY)
                    }}
                  >
                    {activePreview && (
                      <div className={`mb-1 rounded-md border px-2 py-1 text-[11px] font-medium ${
                        activePreview.severity === 'hard'
                          ? 'border-rose-300 bg-rose-50 text-rose-700'
                          : activePreview.severity === 'soft'
                            ? 'border-amber-300 bg-amber-50 text-amber-700'
                            : 'border-emerald-300 bg-emerald-50 text-emerald-700'
                      }`}>
                        {activePreview.label}
                      </div>
                    )}
                    <div className="space-y-1">
                      {cellSessions.map((s) => {
                        const heat = heatBySessionId[s.id] ?? { hard: 0, soft: 0, notes: [] }
                        const heatState = !heatmapEnabled
                          ? 'none'
                          : heat.hard > 0
                            ? 'hard'
                            : heat.soft > 0
                              ? 'soft'
                              : 'none'
                        return (
                          <SessionCell
                            key={s.id}
                            session={s}
                            onClick={onSessionClick}
                            onHover={onSessionHover}
                            onDragStart={onSessionDragStart}
                            onDragEnd={onSessionDragEnd}
                            onContextMenu={onSessionContextMenu}
                            heatState={heatState}
                            inspectorNotes={heat.notes}
                            highlighted={highlightedSessionIds?.has(s.id) ?? false}
                            preAllocated={preAllocatedSessionIds?.has(s.id) ?? false}
                          />
                        )
                      })}
                      {!isBlockedDay && slot && cellSessions.length === 0 && !activePreview && onSlotAdd && (
                        <button
                          type="button"
                          aria-label={`Add session at ${DAY_LABELS[day]} ${startTime}`}
                          title="Add session here"
                          onClick={() => onSlotAdd(slot)}
                          className="hidden h-7 w-full items-center justify-center gap-1 rounded-md border border-dashed border-slate-300 text-[11px] font-medium text-slate-400 opacity-0 transition group-hover:flex group-hover:opacity-100 hover:border-emerald-400 hover:bg-emerald-50 hover:text-emerald-700 focus-visible:opacity-100"
                        >
                          <Plus size={12} /> Add
                        </button>
                      )}
                    </div>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
