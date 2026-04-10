import { AlertTriangle, BarChart3, Flag, Sparkles } from 'lucide-react'
import type { ClassSession, ConstraintBreakdown, ScoreExplanation } from '../../types'

interface ScoreBreakdownPanelProps {
  score: ScoreExplanation | null
  rawExplanation: string | null
  sessions: ClassSession[]
  highlightedSessionIds: Set<number>
  onViolationClick: (text: string) => void
  onClearHighlight: () => void
}

function groupByLevel(constraints: ConstraintBreakdown[]) {
  return {
    hard: constraints.filter((c) => c.level === 'HARD'),
    medium: constraints.filter((c) => c.level === 'MEDIUM'),
    soft: constraints.filter((c) => c.level === 'SOFT'),
  }
}

function parseRawLines(rawExplanation: string | null): string[] {
  if (!rawExplanation) return []
  return rawExplanation
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .slice(0, 20)
}

export default function ScoreBreakdownPanel({
  score,
  rawExplanation,
  sessions,
  highlightedSessionIds,
  onViolationClick,
  onClearHighlight,
}: ScoreBreakdownPanelProps) {
  const grouped = groupByLevel(score?.constraints ?? [])
  const details = parseRawLines(rawExplanation)

  return (
    <aside className="space-y-3">
      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-semibold text-slate-900">Score Inspector</h3>
          {highlightedSessionIds.size > 0 && (
            <button
              onClick={onClearHighlight}
              className="text-xs text-indigo-600 hover:text-indigo-700"
            >
              Clear highlight
            </button>
          )}
        </div>
        {!score && (
          <p className="text-sm text-slate-500">No score breakdown loaded yet.</p>
        )}
        {score && (
          <div className="space-y-2 text-xs text-slate-700">
            <div className="flex justify-between"><span>Feasible</span><span className={score.feasible ? 'text-emerald-600' : 'text-rose-600'}>{score.feasible ? 'Yes' : 'No'}</span></div>
            <div className="flex justify-between"><span>Hard</span><span>{score.hardScore}</span></div>
            <div className="flex justify-between"><span>Medium</span><span>{score.mediumScore}</span></div>
            <div className="flex justify-between"><span>Soft</span><span>{score.softScore}</span></div>
          </div>
        )}
      </section>

      <section className="rounded-xl border border-rose-200 bg-rose-50/70 p-4">
        <h4 className="text-xs uppercase tracking-wide text-rose-700 flex items-center gap-1.5 mb-2"><AlertTriangle size={12} /> Infeasibility Items</h4>
        <div className="space-y-1.5">
          {grouped.hard.length === 0 && <p className="text-xs text-rose-700/70">No hard violations.</p>}
          {grouped.hard.map((c) => (
            <button key={`${c.constraintName}-hard`} onClick={() => onViolationClick(c.constraintName)} className="w-full text-left text-xs rounded-md border border-rose-200 bg-white px-2 py-1.5 hover:border-rose-300">
              <div className="font-medium text-slate-800">{c.constraintName}</div>
              <div className="text-slate-500">{c.matchCount} matches • {c.scoreImpact}</div>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-amber-200 bg-amber-50/70 p-4">
        <h4 className="text-xs uppercase tracking-wide text-amber-700 flex items-center gap-1.5 mb-2"><BarChart3 size={12} /> Efficiency Items</h4>
        <div className="space-y-1.5">
          {grouped.medium.length === 0 && <p className="text-xs text-amber-700/70">No medium penalties.</p>}
          {grouped.medium.map((c) => (
            <button key={`${c.constraintName}-medium`} onClick={() => onViolationClick(c.constraintName)} className="w-full text-left text-xs rounded-md border border-amber-200 bg-white px-2 py-1.5 hover:border-amber-300">
              <div className="font-medium text-slate-800">{c.constraintName}</div>
              <div className="text-slate-500">{c.matchCount} matches • {c.scoreImpact}</div>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-indigo-200 bg-indigo-50/70 p-4">
        <h4 className="text-xs uppercase tracking-wide text-indigo-700 flex items-center gap-1.5 mb-2"><Flag size={12} /> Preferences</h4>
        <div className="space-y-1.5">
          {grouped.soft.length === 0 && <p className="text-xs text-indigo-700/70">No soft penalties.</p>}
          {grouped.soft.map((c) => (
            <button key={`${c.constraintName}-soft`} onClick={() => onViolationClick(c.constraintName)} className="w-full text-left text-xs rounded-md border border-indigo-200 bg-white px-2 py-1.5 hover:border-indigo-300">
              <div className="font-medium text-slate-800">{c.constraintName}</div>
              <div className="text-slate-500">{c.matchCount} matches • {c.scoreImpact}</div>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <h4 className="text-xs uppercase tracking-wide text-slate-600 flex items-center gap-1.5 mb-2"><Sparkles size={12} /> Inspector Details</h4>
        <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
          {details.length === 0 && <p className="text-xs text-slate-500">No detailed text available.</p>}
          {details.map((line, idx) => (
            <button key={`${line}-${idx}`} onClick={() => onViolationClick(line)} className="w-full text-left text-xs rounded-md border border-slate-200 px-2 py-1.5 hover:bg-slate-50">
              {line}
            </button>
          ))}
        </div>
        <p className="text-[11px] text-slate-400 mt-2">
          Highlighted sessions: {highlightedSessionIds.size} / {sessions.length}
        </p>
      </section>
    </aside>
  )
}
