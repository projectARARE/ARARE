import { Activity, BrainCircuit, Gauge, Sparkles } from 'lucide-react'

interface ScorePoint {
  t: number
  score: number
}

interface SolverProgressDashboardProps {
  elapsedSeconds: number
  targetSeconds: number
  bestScore: number
  insights: string[]
  scorePoints: ScorePoint[]
}

function buildPath(points: ScorePoint[], width: number, height: number): string {
  if (points.length === 0) return ''
  const minX = points[0].t
  const maxX = points[points.length - 1].t || 1
  const minY = Math.min(...points.map((p) => p.score))
  const maxY = Math.max(...points.map((p) => p.score))
  const ySpan = Math.max(1, maxY - minY)

  return points
    .map((p, i) => {
      const x = ((p.t - minX) / Math.max(1, maxX - minX)) * width
      const y = height - ((p.score - minY) / ySpan) * height
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

export default function SolverProgressDashboard({
  elapsedSeconds,
  targetSeconds,
  bestScore,
  insights,
  scorePoints,
}: SolverProgressDashboardProps) {
  const percent = Math.min(100, Math.round((elapsedSeconds / Math.max(1, targetSeconds)) * 100))
  const path = buildPath(scorePoints, 440, 120)

  return (
    <section className="card-glass rounded-2xl border border-slate-200 p-5 space-y-4 text-slate-900">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <p className="text-xs uppercase tracking-[0.14em] text-slate-500">Solver Cockpit</p>
          <h3 className="text-lg font-semibold">Search in progress</h3>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-1 text-emerald-700 text-xs">
          <Gauge size={14} />
          Best score: {bestScore}
        </div>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>{elapsedSeconds}s elapsed</span>
          <span>Target {targetSeconds}s</span>
        </div>
        <div className="h-2 rounded-full bg-slate-200 overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-emerald-400 via-cyan-400 to-blue-400 transition-all duration-700"
            style={{ width: `${percent}%` }}
          />
        </div>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-3">
        <div className="flex items-center gap-2 mb-2 text-xs text-slate-500">
          <Activity size={13} />
          Score trend
        </div>
        <svg viewBox="0 0 440 120" className="w-full h-32">
          <path d="M0,120 L440,120" stroke="rgba(148,163,184,0.25)" strokeWidth="1" fill="none" />
          <path
            d={path}
            stroke="url(#solverLineGradient)"
            strokeWidth="3"
            fill="none"
            strokeLinecap="round"
          />
          <defs>
            <linearGradient id="solverLineGradient" x1="0" x2="1" y1="0" y2="0">
              <stop offset="0%" stopColor="#34d399" />
              <stop offset="50%" stopColor="#22d3ee" />
              <stop offset="100%" stopColor="#60a5fa" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      <div className="grid md:grid-cols-2 gap-2 text-xs text-slate-700">
        {insights.map((insight, idx) => (
          <div key={`${insight}-${idx}`} className="rounded-lg border border-slate-200 bg-white px-3 py-2 flex items-center gap-2">
            {idx % 2 === 0 ? <BrainCircuit size={13} className="text-cyan-600" /> : <Sparkles size={13} className="text-emerald-600" />}
            <span>{insight}</span>
          </div>
        ))}
      </div>
    </section>
  )
}
