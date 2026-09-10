import { useEffect, useMemo, useState } from 'react'
import { BarChart, Bar, CartesianGrid, Tooltip, ResponsiveContainer, XAxis, YAxis, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar } from 'recharts'
import { Card, Select } from '../components/ui'
import { scheduleApi, teacherApi, roomApi } from '../services/api'
import type { ClassSession, Schedule, Teacher, Room } from '../types'
import { useToast } from '../contexts/ToastContext'

export default function AnalyticsDashboard() {
  const { toast } = useToast()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedScheduleId, setSelectedScheduleId] = useState<number | undefined>()
  const [sessions, setSessions] = useState<ClassSession[]>([])
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingSessions, setLoadingSessions] = useState(false)

  useEffect(() => {
    Promise.allSettled([scheduleApi.getAll(), teacherApi.getAll(), roomApi.getAll()])
      .then(([sch, t, r]) => {
        if (sch.status === 'fulfilled') {
          setSchedules(sch.value)
          if (sch.value.length > 0) setSelectedScheduleId(sch.value[0].id)
        } else {
          toast.error('Failed to load schedules')
        }
        if (t.status === 'fulfilled') setTeachers(t.value)
        else toast.error('Failed to load teachers')
        if (r.status === 'fulfilled') setRooms(r.value)
        else toast.error('Failed to load rooms')
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!selectedScheduleId) return
    setLoadingSessions(true)
    scheduleApi.getSessions(selectedScheduleId)
      .then(setSessions)
      .catch((e) => {
        setSessions([])
        toast.error(e instanceof Error ? e.message : 'Failed to load sessions')
      })
      .finally(() => setLoadingSessions(false))
  }, [selectedScheduleId])

  const teacherLoad = useMemo(() => {
    return teachers.map((teacher) => {
      const teacherSessions = sessions.filter((s) => s.teacherId === teacher.id)
      const hours = teacherSessions.reduce((sum, s) => sum + (s.duration ?? 1), 0)
      return { name: teacher.name, hours }
    }).sort((a, b) => b.hours - a.hours).slice(0, 12)
  }, [teachers, sessions])

  const roomPulse = useMemo(() => {
    const distinctSlots = new Set(sessions.map((s) => `${s.day}-${s.timeslotId}`)).size
    const denom = Math.max(1, distinctSlots)
    return rooms.map((room) => {
      const used = sessions.filter((s) => s.roomId === room.id).length
      return {
        room: room.roomNumber,
        occupancy: Math.min(100, Math.round((used / denom) * 100)),
      }
    }).sort((a, b) => b.occupancy - a.occupancy).slice(0, 12)
  }, [rooms, sessions])

  const radarData = useMemo(() => {
    const hardLike = sessions.filter((s) => !s.teacherId || !s.roomId || !s.timeslotId).length
    const mediumLike = sessions.filter((s) => (s.duration ?? 1) > 1).length
    const softLike = sessions.filter((s) => !!s.isLocked).length
    const total = Math.max(1, sessions.length)

    return [
      { metric: 'Sustainability', score: Math.max(5, 100 - Math.round((hardLike / total) * 100)) },
      { metric: 'Teacher Preference', score: Math.max(5, 100 - Math.round((mediumLike / total) * 70)) },
      { metric: 'Student Experience', score: Math.max(5, 100 - Math.round((softLike / total) * 50)) },
      { metric: 'Room Utilization', score: Math.round(roomPulse.reduce((a, b) => a + b.occupancy, 0) / Math.max(1, roomPulse.length)) },
      { metric: 'Constraint Health', score: Math.max(5, 100 - Math.round((hardLike / total) * 100)) },
    ]
  }, [sessions, roomPulse])

  return (
    <div className="space-y-4">
      <Card title="Resource Analytics" description="Load, occupancy, and schedule quality insights.">
        {loading ? (
          <div className="h-16 bg-gray-100 rounded-lg animate-pulse" />
        ) : schedules.length === 0 ? (
          <div className="text-center py-6">
            <p className="text-gray-500">No schedules available. Generate a schedule first.</p>
          </div>
        ) : (
          <div className="max-w-sm">
            <Select
              label="Schedule"
              value={selectedScheduleId ?? ''}
              onChange={(e) => setSelectedScheduleId(+e.target.value)}
              options={schedules.map((s) => ({ value: s.id, label: `${s.name} (${s.status})` }))}
            />
          </div>
        )}
      </Card>

      <Card title="Constraint score — what the solver is actually optimizing"
        description="The live score from the timetable itself, not an approximation. Lower penalties are better.">
        {(() => {
          const selected = schedules.find((s) => s.id === selectedScheduleId)
          if (!selected) return <p className="text-gray-500 text-sm">Select a schedule to see its real score.</p>
          const scoreMatch = selected.score?.match(/(-?\d+)hard\/(-?\d+)medium\/(-?\d+)soft/)
          return (
            <div className="space-y-3">
              <div className="flex items-center gap-3 flex-wrap">
                <span className="text-xs text-gray-500">Raw score</span>
                <code className="px-2 py-1 rounded bg-gray-100 text-sm font-mono">{selected.score ?? '—'}</code>
                {selected.scoreExplanation && (
                  <span className="text-xs text-gray-400 break-all font-mono">{selected.scoreExplanation}</span>
                )}
              </div>
              {scoreMatch && (
                <div className="grid sm:grid-cols-3 gap-3 text-sm">
                  {[
                    { label: 'Hard penalties', value: Number(scoreMatch[1]), tone: Number(scoreMatch[1]) > 0 ? 'text-rose-700 bg-rose-50 border-rose-200' : 'text-emerald-700 bg-emerald-50 border-emerald-200' },
                    { label: 'Medium penalties', value: Number(scoreMatch[2]), tone: 'text-amber-700 bg-amber-50 border-amber-200' },
                    { label: 'Soft penalties', value: Number(scoreMatch[3]), tone: Number(scoreMatch[3]) > 0 ? 'text-gray-700 bg-gray-50 border-gray-200' : 'text-emerald-700 bg-emerald-50 border-emerald-200' },
                  ].map((item) => (
                    <div key={item.label} className={`rounded-lg border px-3 py-2.5 ${item.tone}`}>
                      <p className="text-xs opacity-80">{item.label}</p>
                      <p className="text-lg font-semibold">{Math.abs(item.value)}</p>
                    </div>
                  ))}
                </div>
              )}
              <p className="text-xs text-gray-500">
                Hard constraints must be satisfied before anything else: a teacher assigned twice at once, two classes in
                the same room at the same time, or a batch double-booked. Medium penalties cover daily-load balance and
                duration issues; soft penalties cover the remaining preferences last.
              </p>
            </div>
          )
        })()}
      </Card>

      {loadingSessions ? (
        <div className="h-72 bg-gray-100 rounded-lg animate-pulse" />
      ) : schedules.length === 0 ? null : (
      <div className="grid xl:grid-cols-2 gap-4">
        <Card title="Teacher Load Distribution" description="Hours assigned per teacher.">
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={teacherLoad} margin={{ top: 12, right: 8, left: -12, bottom: 28 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" angle={-25} textAnchor="end" height={52} interval={0} />
                <YAxis />
                <Tooltip />
                <Bar dataKey="hours" fill="#22d3ee" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="Room Occupancy Pulse" description="How frequently each room is utilized.">
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={roomPulse} margin={{ top: 12, right: 8, left: -12, bottom: 20 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="room" interval={0} angle={-20} textAnchor="end" height={48} />
                <YAxis domain={[0, 100]} />
                <Tooltip />
                <Bar dataKey="occupancy" fill="#34d399" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>
      )}

      <Card title="Constraint Health Radar" description="Quality surface across core objective categories.">
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%">
            <RadarChart cx="50%" cy="50%" outerRadius="72%" data={radarData}>
              <PolarGrid />
              <PolarAngleAxis dataKey="metric" />
              <PolarRadiusAxis domain={[0, 100]} />
              <Radar name="Score" dataKey="score" stroke="#818cf8" fill="#818cf8" fillOpacity={0.28} />
              <Tooltip />
            </RadarChart>
          </ResponsiveContainer>
        </div>
      </Card>
    </div>
  )
}
