import { useEffect, useMemo, useState } from 'react'
import { Card, Select } from '../components/ui'
import { scheduleApi } from '../services/api'
import type { Schedule, ClassSession } from '../types'
import { useToast } from '../contexts/ToastContext'

function parseScore(score?: string) {
  if (!score) return { hard: 0, medium: 0, soft: 0 }
  const hard = Number((score.match(/(-?\d+)hard/) ?? [])[1] ?? 0)
  const medium = Number((score.match(/(-?\d+)medium/) ?? [])[1] ?? 0)
  const soft = Number((score.match(/(-?\d+)soft/) ?? [])[1] ?? 0)
  return { hard, medium, soft }
}

function metricsFromSessions(sessions: ClassSession[]) {
  const total = Math.max(1, sessions.length)
  const assignedTeacher = sessions.filter((s) => !!s.teacherId).length
  const assignedRoom = sessions.filter((s) => !!s.roomId).length
  const assignedSlot = sessions.filter((s) => !!s.timeslotId).length
  return {
    teacherSatisfaction: Math.round((assignedTeacher / total) * 100),
    roomCoverage: Math.round((assignedRoom / total) * 100),
    scheduleCompleteness: Math.round((assignedSlot / total) * 100),
  }
}

export default function WhatIfComparison() {
  const { toast } = useToast()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [aId, setAId] = useState<number | undefined>()
  const [bId, setBId] = useState<number | undefined>()
  const [aSessions, setASessions] = useState<ClassSession[]>([])
  const [bSessions, setBSessions] = useState<ClassSession[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingSessions, setLoadingSessions] = useState(false)

  useEffect(() => {
    scheduleApi.getAll().then((res) => {
      setSchedules(res)
      if (res.length > 0) setAId(res[0].id)
      if (res.length > 1) setBId(res[1].id)
    }).catch((e) => {
      setSchedules([])
      toast.error(e instanceof Error ? e.message : 'Failed to load schedules')
    }).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!aId) return
    setLoadingSessions(true)
    scheduleApi.getSessions(aId)
      .then(setASessions)
      .catch((e) => {
        setASessions([])
        toast.error(e instanceof Error ? e.message : 'Failed to load Schedule A sessions')
      })
      .finally(() => setLoadingSessions(false))
  }, [aId])
  useEffect(() => {
    if (!bId) return
    scheduleApi.getSessions(bId)
      .then(setBSessions)
      .catch((e) => {
        setBSessions([])
        toast.error(e instanceof Error ? e.message : 'Failed to load Schedule B sessions')
      })
  }, [bId])

  const aSchedule = schedules.find((s) => s.id === aId)
  const bSchedule = schedules.find((s) => s.id === bId)

  const aMetrics = useMemo(() => metricsFromSessions(aSessions), [aSessions])
  const bMetrics = useMemo(() => metricsFromSessions(bSessions), [bSessions])

  const scoreA = parseScore(aSchedule?.score)
  const scoreB = parseScore(bSchedule?.score)

  const deltas = {
    teacherSatisfaction: bMetrics.teacherSatisfaction - aMetrics.teacherSatisfaction,
    roomCoverage: bMetrics.roomCoverage - aMetrics.roomCoverage,
    scheduleCompleteness: bMetrics.scheduleCompleteness - aMetrics.scheduleCompleteness,
    hard: scoreB.hard - scoreA.hard,
    medium: scoreB.medium - scoreA.medium,
    soft: scoreB.soft - scoreA.soft,
  }

  const rows = [
    ['Teacher Satisfaction', `${aMetrics.teacherSatisfaction}%`, `${bMetrics.teacherSatisfaction}%`, `${deltas.teacherSatisfaction > 0 ? '+' : ''}${deltas.teacherSatisfaction}%`],
    ['Room Coverage', `${aMetrics.roomCoverage}%`, `${bMetrics.roomCoverage}%`, `${deltas.roomCoverage > 0 ? '+' : ''}${deltas.roomCoverage}%`],
    ['Schedule Completeness', `${aMetrics.scheduleCompleteness}%`, `${bMetrics.scheduleCompleteness}%`, `${deltas.scheduleCompleteness > 0 ? '+' : ''}${deltas.scheduleCompleteness}%`],
    ['Hard Score', String(scoreA.hard), String(scoreB.hard), `${deltas.hard > 0 ? '+' : ''}${deltas.hard}`],
    ['Medium Score', String(scoreA.medium), String(scoreB.medium), `${deltas.medium > 0 ? '+' : ''}${deltas.medium}`],
    ['Soft Score', String(scoreA.soft), String(scoreB.soft), `${deltas.soft > 0 ? '+' : ''}${deltas.soft}`],
  ]

  return (
    <div className="space-y-4">
      <Card title="What-If Comparison" description="Compare two generated schedules side-by-side.">
        {loading ? (
          <div className="h-16 bg-gray-100 rounded-lg animate-pulse" />
        ) : schedules.length === 0 ? (
          <div className="text-center py-8">
            <p className="text-gray-500">No schedules available. Generate a schedule first.</p>
          </div>
        ) : (
          <div className="grid md:grid-cols-2 gap-3">
            <Select
              label="Schedule A"
              value={aId ?? ''}
              onChange={(e) => setAId(+e.target.value)}
              options={schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))}
            />
            <Select
              label="Schedule B"
              value={bId ?? ''}
              onChange={(e) => setBId(+e.target.value)}
              options={schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))}
            />
            {schedules.length === 1 && (
              <p className="text-sm text-gray-500 md:col-span-2">
                Only one schedule exists — pick a second to compare, or generate a new one.
              </p>
            )}
          </div>
        )}
      </Card>

      <Card>
        {loadingSessions ? (
          <div className="h-48 bg-gray-100 rounded-lg animate-pulse" />
        ) : schedules.length === 0 ? (
          <div className="text-center py-8 text-sm text-gray-500">Nothing to compare yet.</div>
        ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm border-collapse">
            <thead>
              <tr className="bg-slate-50">
                <th className="border border-slate-200 px-3 py-2 text-left">Metric</th>
                <th className="border border-slate-200 px-3 py-2 text-left">A</th>
                <th className="border border-slate-200 px-3 py-2 text-left">B</th>
                <th className="border border-slate-200 px-3 py-2 text-left">Delta (B-A)</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(([metric, a, b, delta]) => (
                <tr key={metric}>
                  <td className="border border-slate-200 px-3 py-2 font-medium">{metric}</td>
                  <td className="border border-slate-200 px-3 py-2">{a}</td>
                  <td className="border border-slate-200 px-3 py-2">{b}</td>
                  <td className="border border-slate-200 px-3 py-2">{delta}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        )}
      </Card>
    </div>
  )
}
