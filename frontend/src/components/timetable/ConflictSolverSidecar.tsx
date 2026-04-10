import { Lightbulb, AlertTriangle } from 'lucide-react'
import type { ClassSession, Timeslot } from '../../types'

interface SuggestedFix {
  timeslotId: number
  label: string
  preview: string
  scoreHint: string
}

interface ConflictSolverSidecarProps {
  session: ClassSession | null
  suggestions: SuggestedFix[]
  onApplySuggestion: (timeslotId: number) => void
  onClose: () => void
}

export default function ConflictSolverSidecar({
  session,
  suggestions,
  onApplySuggestion,
  onClose,
}: ConflictSolverSidecarProps) {
  if (!session) return null

  return (
    <section className="rounded-xl border border-rose-200 bg-rose-50/80 p-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-wide text-rose-700">Conflict Solver</p>
          <h4 className="text-sm font-semibold text-slate-900">{session.subjectName ?? 'Session'}</h4>
        </div>
        <button className="text-xs text-slate-500 hover:text-slate-700" onClick={onClose}>Close</button>
      </div>

      <div className="rounded-md border border-rose-200 bg-white px-3 py-2 text-xs text-slate-700 flex items-start gap-2">
        <AlertTriangle size={13} className="text-rose-500 mt-0.5" />
        <span>
          Conflicting slot detected. Pick one of the suggested relocations to reduce hard clashes.
        </span>
      </div>

      <div className="space-y-2">
        {suggestions.length === 0 && (
          <p className="text-xs text-slate-600">No safe alternative slots found in current horizon.</p>
        )}
        {suggestions.map((fix) => (
          <button
            key={fix.timeslotId}
            onClick={() => onApplySuggestion(fix.timeslotId)}
            className="w-full text-left rounded-md border border-rose-200 bg-white px-3 py-2 hover:border-rose-300"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-slate-900">{fix.label}</span>
              <span className="text-[11px] text-emerald-700">{fix.scoreHint}</span>
            </div>
            <p className="text-xs text-slate-600 mt-0.5 flex items-center gap-1">
              <Lightbulb size={11} className="text-amber-500" />
              {fix.preview}
            </p>
          </button>
        ))}
      </div>
    </section>
  )
}

export function buildConflictSuggestions(session: ClassSession, timeslots: Timeslot[], sessions: ClassSession[]) {
  const classSlots = timeslots.filter((t) => t.type === 'CLASS')
  const candidates = classSlots
    .filter((slot) => slot.id !== session.timeslotId)
    .map((slot) => {
      let hard = 0
      let soft = 0
      for (const other of sessions) {
        if (other.id === session.id || other.day !== slot.day || other.timeslotId !== slot.id) continue
        if (session.teacherId && other.teacherId === session.teacherId) hard += 1
        if (session.roomId && other.roomId === session.roomId) hard += 1
        if (session.batchId && other.batchId === session.batchId) hard += 1
      }

      for (const other of sessions) {
        if (other.id === session.id || other.day !== slot.day) continue
        if (session.batchId && other.batchId === session.batchId && session.subjectId && other.subjectId === session.subjectId) {
          soft += 1
        }
      }

      return {
        timeslotId: slot.id,
        label: `${slot.day} ${slot.startTime}-${slot.endTime}`,
        preview: hard > 0 ? `${hard} hard conflict(s) remain` : `No hard conflicts, ${soft} soft issue(s)` ,
        scoreHint: hard > 0 ? `HARD +${hard}` : soft > 0 ? `Soft +${soft}` : 'Best move',
        hard,
        soft,
      }
    })
    .sort((a, b) => {
      if (a.hard !== b.hard) return a.hard - b.hard
      return a.soft - b.soft
    })
    .slice(0, 4)

  return candidates
}
