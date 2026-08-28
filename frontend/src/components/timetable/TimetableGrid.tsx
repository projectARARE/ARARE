import { useMemo, useEffect } from 'react'
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

type Density = 'compact' | 'comfortable'

interface TimetableGridProps {
  sessions: ClassSession[]
  timeslots: Timeslot[]
  activeDays?: SchoolDay[]
  density?: Density
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
  filterBatchIds?: number[]
  filterTeacherIds?: number[]
  filterRoomIds?: number[]
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
  /** Id of the session currently grabbed for a drag — used to outline it. */
  draggedSessionId?: number | null
  /** When true, non-conflicting (no heat) placed sessions are dimmed. */
  highlightConflictsOnly?: boolean
  /** When true, render only unplaced sessions as a compact list. */
  showUnplacedOnly?: boolean
}

export default function TimetableGrid({
  sessions,
  timeslots,
  activeDays,
  density = 'comfortable',
  onSessionClick,
  onSessionHover,
  onSessionDragStart,
  onSessionDragEnd,
  onSessionContextMenu,
  onSlotDragHover,
  onSlotDrop,
  onCellContextMenu,
  onSlotAdd,
  filterBatchIds,
  filterTeacherIds,
  filterRoomIds,
  heatmapEnabled = false,
  heatBySessionId = {},
  highlightedSessionIds,
  preAllocatedSessionIds,
  dragPreview,
  onOrphanSessionsCount,
  blockedDays = [],
  draggedSessionId = null,
  highlightConflictsOnly = false,
  showUnplacedOnly = false,
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
      if (filterBatchIds?.length && (!s.batchId || !filterBatchIds.includes(s.batchId))) return false
      if (filterTeacherIds?.length && (!s.teacherId || !filterTeacherIds.includes(s.teacherId))) return false
      if (filterRoomIds?.length && (!s.roomId || !filterRoomIds.includes(s.roomId))) return false
      return true
    })
  }, [sessions, filterBatchIds, filterTeacherIds, filterRoomIds])

  // Count sessions that cannot be rendered into a cell (missing day/timeslot
  // or whose timeslot is absent from the loaded set). Reported to the caller
  // via onOrphanSessionsCount from an effect below — never during render.
  const orphanCount = useMemo(() => {
    const timeslotIds = new Set(timeslots.map((t) => t.id))
    let count = 0
    for (const s of filteredSessions) {
      if (!s.day || !s.timeslotId || !timeslotIds.has(s.timeslotId)) {
        count += 1
      }
    }
    return count
  }, [filteredSessions, timeslots])

  useEffect(() => {
    // Only report once the timeslot set is loaded; otherwise every session
    // would look orphaned purely because timeslots are empty.
    if (timeslots.length > 0) {
      onOrphanSessionsCount?.(orphanCount)
    }
  }, [timeslots, orphanCount, onOrphanSessionsCount])

  const sessionIndex = useMemo(() => {
    const index: Record<string, ClassSession[]> = {}
    const timeslotIds = new Set(timeslots.map((t) => t.id))
    for (const s of filteredSessions) {
      if (!s.day || !s.timeslotId || !timeslotIds.has(s.timeslotId)) {
        continue
      }
      const key = `${s.day}:${s.timeslotId}`
      if (!index[key]) index[key] = []
      index[key].push(s)
    }
    return index
  }, [filteredSessions, timeslots])

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

  // "Show only unplaced" — render the unplaced sessions as a compact,
  // scrollable list instead of the day/time matrix so the operator can focus
  // purely on what still needs to be scheduled.
  if (showUnplacedOnly) {
    const unplaced = sessions.filter((s) => !s.timeslotId)
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3">
        <p className="text-xs font-medium text-amber-800 mb-2 flex items-center gap-1.5">
          {unplaced.length} unplaced session{unplaced.length !== 1 ? 's' : ''}
          {filterBatchIds?.length || filterTeacherIds?.length || filterRoomIds?.length
            ? ' (after current filters)'
            : ''}
        </p>
        {unplaced.length === 0 ? (
          <p className="text-xs text-amber-700/80">Nothing left to place — every filtered session has a slot.</p>
        ) : (
          <div className="flex flex-wrap gap-2 max-h-[60vh] overflow-auto">
            {unplaced.map((s) => (
              <SessionCell
                key={s.id}
                session={s}
                density={density}
                onClick={onSessionClick}
                onHover={onSessionHover}
                onDragStart={onSessionDragStart}
                onDragEnd={onSessionDragEnd}
                onContextMenu={onSessionContextMenu}
                heatState="none"
                highlighted={highlightedSessionIds?.has(s.id) ?? false}
                preAllocated={preAllocatedSessionIds?.has(s.id) ?? false}
              />
            ))}
          </div>
        )}
      </div>
    )
  }

  const cellPad = density === 'compact' ? 'px-1.5 py-1' : 'px-2.5 py-2'
  const cellMinH = density === 'compact' ? 'min-h-[44px]' : 'min-h-[64px]'
  const isDragging = draggedSessionId != null

  return (
    <div className="overflow-auto rounded-lg border border-gray-200" style={{ maxHeight: 'calc(100vh - 320px)' }}>
      <table className="min-w-full border-collapse text-sm">
        <thead>
          <tr className="bg-slate-100">
            <th className="sticky left-0 top-0 z-30 border border-gray-200 px-3 py-2 text-left text-xs font-semibold text-gray-500 bg-slate-100 w-28">
              Time
            </th>
            {days.map((day) => (
              <th
                key={day}
                className="sticky top-0 z-20 border border-gray-200 px-3 py-2 text-center text-xs font-semibold text-gray-700 min-w-[140px] bg-slate-100"
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
              <td className={`sticky left-0 z-10 border border-gray-200 px-3 py-2 text-xs text-gray-500 whitespace-nowrap bg-slate-50 ${cellPad}`}>
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
                const isDropTarget = isDragging && slot && !isBlockedDay

                return (
                  <td
                    key={day}
                    rowSpan={rowSpan}
                    className={`group border border-gray-200 ${cellPad} align-top ${cellMinH} ${
                      isBlockedDay ? 'bg-slate-200/50' : ''
                    } ${isDropTarget ? 'outline-dashed outline-2 outline-emerald-400 bg-emerald-50/40' : ''}`}
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
                        const dimmed = highlightConflictsOnly && heatState === 'none'
                        return (
                          <div key={s.id} className={dimmed ? 'opacity-25 transition-opacity' : ''}>
                            <SessionCell
                              session={s}
                              density={density}
                              onClick={onSessionClick}
                              onHover={onSessionHover}
                              onDragStart={onSessionDragStart}
                              onDragEnd={onSessionDragEnd}
                              onContextMenu={onSessionContextMenu}
                              heatState={heatState}
                              inspectorNotes={heat.notes}
                              highlighted={highlightedSessionIds?.has(s.id) ?? false}
                              preAllocated={preAllocatedSessionIds?.has(s.id) ?? false}
                              dragging={draggedSessionId === s.id}
                            />
                          </div>
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
