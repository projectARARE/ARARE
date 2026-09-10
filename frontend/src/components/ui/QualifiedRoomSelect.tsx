import { useEffect, useState } from 'react'
import SearchableSelect from './SearchableSelect'
import type { Room, Subject } from '../../types'

export function isRoomSuitableForSubject(room: Room, subject?: Subject | null): boolean {
  if (!subject) return true
  if (room.type !== subject.roomTypeRequired) return false
  if (subject.isLab && subject.labSubtypeRequired && room.labSubtype !== subject.labSubtypeRequired) {
    return false
  }
  return true
}

export function isRoomCapacityEnough(room: Room, minCapacity?: number | null): boolean {
  return minCapacity == null || !(minCapacity > 0) || room.capacity >= minCapacity
}

interface QualifiedRoomSelectProps {
  rooms: Room[]
  /** Room-type/lab-subtype context; when null every room is offered. */
  subject?: Subject | null
  /** Optional minimum capacity the room must satisfy (e.g. batch size). */
  minCapacity?: number | null
  value: number | null
  onChange: (value: number | null) => void
  label?: string
  placeholder?: string
  allowClear?: boolean
  disabled?: boolean
  className?: string
}

/**
 * Room picker that defaults to ONLY rooms suitable for the selected subject
 * (type/lab-subtype, plus optional capacity). Mirrors QualifiedTeacherSelect:
 * states when nothing is suitable, and offers a "show unsuitable anyway"
 * escape hatch. The currently selected room is always listed (marked) so
 * edits never orphan a value. Without a subject every room is offered.
 */
export default function QualifiedRoomSelect({
  rooms,
  subject,
  minCapacity,
  value,
  onChange,
  label = 'Room',
  placeholder = 'Select room…',
  allowClear = true,
  disabled = false,
  className,
}: QualifiedRoomSelectProps) {
  const [showUnqualified, setShowUnqualified] = useState(false)

  const hasContext = subject != null && (minCapacity ?? 0) > 0
  const suitable = rooms.filter(
    (r) => isRoomSuitableForSubject(r, subject) && isRoomCapacityEnough(r, minCapacity),
  )
  const unsuitable = rooms.filter(
    (r) => !isRoomSuitableForSubject(r, subject) || !isRoomCapacityEnough(r, minCapacity),
  )
  const currentIsUnsuitable = value != null && unsuitable.some((r) => r.id === value)

  useEffect(() => {
    setShowUnqualified(false)
  }, [subject?.id, minCapacity])

  const roomLabel = (r: Room) =>
    `${r.roomNumber}${r.buildingName ? ` (${r.buildingName})` : ''} [${r.type}]${r.capacity ? ` · ${r.capacity}` : ''}`

  const options = [
    ...suitable.map((r) => ({ value: r.id, label: roomLabel(r) })),
    ...(showUnqualified || currentIsUnsuitable
      ? unsuitable.map((r) => ({
          value: r.id,
          label: currentIsUnsuitable && r.id === value
            ? `${roomLabel(r)} · selected (not suitable)`
            : `${roomLabel(r)} · not suitable`,
        }))
      : []),
  ]

  const noneSuitable = hasContext && suitable.length === 0

  return (
    <div className={className}>
      <SearchableSelect
        label={label}
        value={value}
        onChange={(v) => onChange(typeof v === 'number' ? v : v == null ? null : Number(v))}
        options={options}
        placeholder={placeholder}
        allowClear={allowClear}
        disabled={disabled}
      />
      {hasContext && !disabled && (unsuitable.length > 0 || noneSuitable) && (
        <div className="mt-1.5 flex items-center justify-between gap-2">
          <span className={`text-xs ${noneSuitable ? 'text-amber-700' : 'text-gray-500'}`}>
            {noneSuitable
              ? 'No suitable room for this subject.'
              : `${suitable.length} suitable room${suitable.length === 1 ? '' : 's'}`}
          </span>
          <button
            type="button"
            onClick={() => setShowUnqualified((s) => !s)}
            className="text-xs font-medium text-indigo-600 hover:text-indigo-700 focus:outline-none focus:underline"
          >
            {showUnqualified ? 'Hide unsuitable' : 'Show unsuitable rooms'}
          </button>
        </div>
      )}
    </div>
  )
}