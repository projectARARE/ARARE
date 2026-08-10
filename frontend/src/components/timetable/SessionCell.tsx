import type { ClassSession } from '../../types'
import { Lock } from 'lucide-react'

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
}: SessionCellProps) {
  const heatClass =
    heatState === 'hard'
      ? 'ring-1 ring-rose-400 animate-cell-pulse'
      : heatState === 'soft'
        ? 'ring-1 ring-amber-400'
        : ''

  const lockedBadge = session.isLocked ? (
    <span className="absolute right-1 top-1 inline-flex items-center gap-1 rounded-full bg-amber-600 px-1.5 py-0.5 text-[10px] font-semibold text-white shadow-sm">
      <Lock className="h-2.5 w-2.5" />
      Locked
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
      title={[session.isLocked ? 'Locked - drag disabled' : '', ...inspectorNotes].filter(Boolean).join(' | ')}
      className={`
        relative rounded-md border p-1.5 text-xs leading-tight
        transition-shadow hover:shadow-md select-none
        ${colorForSubject(session.subjectId ?? session.id)}
        ${heatClass}
        ${highlighted ? 'outline outline-2 outline-offset-1 outline-cyan-400' : ''}
        ${session.isLocked ? 'ring-2 ring-offset-1 ring-amber-500 bg-amber-50/70 opacity-95' : ''}
        ${(onClick || onDragStart) && !session.isLocked ? 'cursor-pointer' : ''}
        ${session.isLocked && onDragStart ? 'cursor-not-allowed' : ''}
      `}
    >
      {lockedBadge}
      <p className="font-semibold truncate">{session.subjectName}</p>
      {session.teacherName && (
        <p className="truncate opacity-80">{session.teacherName}</p>
      )}
      {session.roomNumber && (
        <p className="truncate opacity-70">{session.roomNumber}</p>
      )}
      {session.batchLabel && (
        <p className="truncate opacity-70">{session.batchLabel}</p>
      )}
      {session.isLocked && (
        <Lock className="w-2.5 h-2.5 mt-0.5 opacity-60 inline-block" />
      )}
    </div>
  )
}
