import { useEffect, useMemo, useState } from 'react'
import { Card, Select } from '../components/ui'
import { scheduleApi } from '../services/api'
import type { Schedule, ClassSession } from '../types'

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
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [aId, setAId] = useState<number | undefined>()
  const [bId, setBId] = useState<number | undefined>()
  const [aSessions, setASessions] = useState<ClassSession[]>([])
  const [bSessions, setBSessions] = useState<ClassSession[]>([])

  useEffect(() => {
    scheduleApi.getAll().then((res) => {
      setSchedules(res)
      if (res.length > 0) setAId(res[0].id)
      if (res.length > 1) setBId(res[1].id)
    }).catch(() => setSchedules([]))
  }, [])

  useEffect(() => {
    if (!aId) return
    scheduleApi.getSessions(aId).then(setASessions).catch(() => setASessions([]))
  }, [aId])
  useEffect(() => {
    if (!bId) return
    scheduleApi.getSessions(bId).then(setBSessions).catch(() => setBSessions([]))
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
        </div>
      </Card>

      <Card>
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
      </Card>
    </div>
  )
}
