import { useMemo } from 'react'
import type { ClassSession, Timeslot, SchoolDay } from '../../types'
import SessionCell from './SessionCell'

const DAYS: SchoolDay[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY',
]

const DAY_LABELS: Record<SchoolDay, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
}

interface TimetableGridProps {
  sessions: ClassSession[]
  timeslots: Timeslot[]
  activeDays?: SchoolDay[]
  onSessionClick?: (session: ClassSession) => void
  filterBatchId?: number
  filterTeacherId?: number
  filterRoomId?: number
}

export default function TimetableGrid({
  sessions,
  timeslots,
  activeDays,
  onSessionClick,
  filterBatchId,
  filterTeacherId,
  filterRoomId,
}: TimetableGridProps) {
  // Determine which days to show
  const days = activeDays?.length
    ? DAYS.filter((d) => activeDays.includes(d))
    : DAYS

  // Unique timeslots per day ordered by startTime
  const slotsByDay = useMemo(() => {
    const map: Record<string, Timeslot[]> = {}
    for (const day of days) {
      map[day] = timeslots
        .filter((t) => t.day === day)
        .sort((a, b) => a.startTime.localeCompare(b.startTime))
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

  // Index sessions by day + timeslotId for fast lookup
  const sessionIndex = useMemo(() => {
    const index: Record<string, ClassSession[]> = {}
    for (const s of filteredSessions) {
      if (!s.day || !s.timeslotId) continue
      const key = `${s.day}:${s.timeslotId}`
      if (!index[key]) index[key] = []
      index[key].push(s)
    }
    return index
  }, [filteredSessions])

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

      const endIdx = Math.min(daySlots.length - 1, startIdx + s.duration - 1)
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

                return (
                  <td
                    key={day}
                    rowSpan={rowSpan}
                    className="border border-gray-200 px-2 py-2 align-top min-h-[60px]"
                  >
                    <div className="space-y-1">
                      {cellSessions.map((s) => (
                        <SessionCell
                          key={s.id}
                          session={s}
                          onClick={onSessionClick}
                        />
                      ))}
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
