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
}

export default function SessionCell({ session, onClick }: SessionCellProps) {
  return (
    <div
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onClick={() => onClick?.(session)}
      onKeyDown={(e) => e.key === 'Enter' && onClick?.(session)}
      className={`
        rounded-md border p-1.5 text-xs leading-tight
        transition-shadow hover:shadow-md select-none
        ${colorForSubject(session.subjectId ?? session.id)}
        ${session.isLocked ? 'ring-2 ring-offset-1 ring-amber-400' : ''}
        ${onClick ? 'cursor-pointer' : ''}
      `}
    >
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
