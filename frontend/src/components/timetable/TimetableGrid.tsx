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
          {allSlotTimes.map(({ startTime, endTime }) => (
            <tr key={`${startTime}-${endTime}`} className="hover:bg-gray-50/40">
              <td className="border border-gray-200 px-3 py-2 text-xs text-gray-500 whitespace-nowrap bg-gray-50">
                <div className="font-medium">{startTime}</div>
                <div className="opacity-70">– {endTime}</div>
              </td>
              {days.map((day) => {
                const slot = slotsByDay[day]?.find(
                  (t) => t.startTime === startTime && t.endTime === endTime,
                )
                const cellSessions = slot
                  ? sessionIndex[`${day}:${slot.id}`] ?? []
                  : []

                return (
                  <td
                    key={day}
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
