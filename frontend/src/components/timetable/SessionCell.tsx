import type { ClassSession } from '../../types'
import { Lock, Pin } from 'lucide-react'

const COLORS = [
  'bg-blue-100 border-blue-300 text-blue-900',
  'bg-green-100 border-green-300 text-green-900',
  'bg-purple-100 border-purple-300 text-purple-900',
  'bg-orange-100 border-orange-300 text-orange-900',
  'bg-pink-100 border-pink-300 text-pink-900',
  'bg-teal-100 border-teal-300 text-teal-900',
  'bg-yellow-100 border-yellow-300 text-yellow-900',
  'bg-indigo-100 border-indigo-300 text-indigo-900',
]

function colorForSubject(id: number): string {
  return COLORS[id % COLORS.length]
}

interface SessionCellProps {
  session: ClassSession
  onClick?: (session: ClassSession) => void
  onHover?: (session: ClassSession | null) => void
  onDragStart?: (session: ClassSession) => void
  onDragEnd?: () => void
  onContextMenu?: (session: ClassSession, x: number, y: number) => void
  highlighted?: boolean
  heatState?: 'none' | 'soft' | 'hard'
  inspectorNotes?: string[]
  preAllocated?: boolean
  density?: 'compact' | 'comfortable'
  dragging?: boolean
}

export default function SessionCell({
  session,
  onClick,
  onHover,
  onDragStart,
  onDragEnd,
  onContextMenu,
  highlighted = false,
  heatState = 'none',
  inspectorNotes = [],
  preAllocated = false,
  density = 'comfortable',
  dragging = false,
}: SessionCellProps) {
  const heatClass =
    heatState === 'hard'
      ? 'ring-1 ring-rose-400 animate-cell-pulse'
      : heatState === 'soft'
        ? 'ring-1 ring-amber-400'
        : ''

  const compact = density === 'compact'
  const denseText = compact ? 'text-[10px] leading-tight' : 'text-xs leading-tight'

  // Full details live in the native tooltip so cells stay small (especially in
  // compact density) while the operator can still read teacher/room on hover.
  const tooltip = [
    session.subjectName ?? 'Session',
    session.teacherName ? `Teacher: ${session.teacherName}` : '',
    session.roomNumber ? `Room: ${session.roomNumber}${session.buildingName ? ` (${session.buildingName})` : ''}` : '',
    session.batchLabel ? `Batch: ${session.batchLabel}` : '',
    session.isLocked ? 'Locked — drag disabled' : '',
    ...inspectorNotes,
  ].filter(Boolean).join('  •  ')

  const lockedBadge = session.isLocked ? (
    <span className="absolute right-1 top-1 inline-flex items-center gap-1 rounded-full bg-amber-600 px-1.5 py-0.5 text-[10px] font-semibold text-white shadow-sm">
      <Lock className="h-2.5 w-2.5" />
      Locked
    </span>
  ) : null

  const preAllocatedBadge = preAllocated ? (
    <span className="absolute left-1 top-1 inline-flex items-center gap-0.5 rounded-full bg-cyan-600 px-1.5 py-0.5 text-[10px] font-semibold text-white shadow-sm" title="Pre-assigned teacher">
      <Pin className="h-2.5 w-2.5" />
    </span>
  ) : null

  return (
    <div
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      draggable={!session.isLocked && Boolean(onDragStart)}
      onClick={() => onClick?.(session)}
      onContextMenu={(e) => {
        if (!onContextMenu) return
        e.preventDefault()
        onContextMenu(session, e.clientX, e.clientY)
      }}
      onKeyDown={(e) => e.key === 'Enter' && onClick?.(session)}
      onMouseEnter={() => onHover?.(session)}
      onMouseLeave={() => onHover?.(null)}
      onDragStart={() => !session.isLocked && onDragStart?.(session)}
      onDragEnd={() => onDragEnd?.()}
      title={tooltip}
      className={`
        relative rounded-md border ${compact ? 'p-1' : 'p-1.5'} ${denseText}
        transition-shadow hover:shadow-md select-none
        ${colorForSubject(session.subjectId ?? session.id)}
        ${heatClass}
        ${highlighted ? 'outline outline-2 outline-offset-1 outline-cyan-400' : ''}
        ${dragging ? 'opacity-60 ring-2 ring-primary-400 shadow-lg' : ''}
        ${session.isLocked ? 'ring-2 ring-offset-1 ring-amber-500 bg-amber-50/70 opacity-95' : ''}
        ${(onClick || onDragStart) && !session.isLocked ? 'cursor-grab active:cursor-grabbing' : ''}
        ${session.isLocked && onDragStart ? 'cursor-not-allowed' : ''}
      `}
    >
      {lockedBadge}
      {preAllocatedBadge}
      <p className="font-semibold truncate">{session.subjectName}</p>
      {!compact && session.teacherName && (
        <p className="truncate opacity-80">{session.teacherName}</p>
      )}
      {!compact && session.roomNumber && (
        <p className="truncate opacity-70">{session.roomNumber}</p>
      )}
      {!compact && session.batchLabel && (
        <p className="truncate opacity-70">{session.batchLabel}</p>
      )}
      {session.isLocked && (
        <Lock className="w-2.5 h-2.5 mt-0.5 opacity-60 inline-block" />
      )}
    </div>
  )
}
