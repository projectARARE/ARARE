import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Building2, Users, BookOpen, CalendarDays, Zap, AlertTriangle } from 'lucide-react'
import { Card, Button, Badge } from '../components/ui'
import { buildingApi, teacherApi, subjectApi, scheduleApi } from '../services/api'
import type { Schedule, ScheduleStatus } from '../types'

interface Stat {
  label: string
  value: number | string
  icon: React.ReactNode
  color: string
}

const STATUS_VARIANT: Record<ScheduleStatus, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  DRAFT: 'gray',
  ACTIVE: 'green',
  ARCHIVED: 'blue',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<Stat[]>([])
  const [recentSchedules, setRecentSchedules] = useState<Schedule[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      buildingApi.getAll(),
      teacherApi.getAll(),
      subjectApi.getAll(),
      scheduleApi.getAll(),
    ])
      .then(([buildings, teachers, subjects, schedules]) => {
        setStats([
          {
            label: 'Buildings',
            value: buildings.length,
            icon: <Building2 size={20} />,
            color: 'text-blue-600 bg-blue-50',
          },
          {
            label: 'Teachers',
            value: teachers.length,
            icon: <Users size={20} />,
            color: 'text-green-600 bg-green-50',
          },
          {
            label: 'Subjects',
            value: subjects.length,
            icon: <BookOpen size={20} />,
            color: 'text-purple-600 bg-purple-50',
          },
          {
            label: 'Schedules',
            value: schedules.length,
            icon: <CalendarDays size={20} />,
            color: 'text-orange-600 bg-orange-50',
          },
        ])
        setRecentSchedules(schedules.slice(0, 5))
      })
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-24 bg-gray-100 rounded-lg animate-pulse" />
            ))
          : stats.map((stat) => (
              <div
                key={stat.label}
                className="bg-white rounded-lg border border-gray-200 p-4 flex items-center gap-4"
              >
                <div
                  className={`w-10 h-10 rounded-lg flex items-center justify-center ${stat.color}`}
                >
                  {stat.icon}
                </div>
                <div>
                  <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
                  <p className="text-sm text-gray-500">{stat.label}</p>
                </div>
              </div>
            ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="Quick Actions">
          <div className="space-y-3">
            <Button
              className="w-full justify-start"
              icon={<Zap size={16} />}
              onClick={() => navigate('/schedule/generate')}
            >
              Generate New Timetable
            </Button>
            <Button
              variant="secondary"
              className="w-full justify-start"
              icon={<AlertTriangle size={16} />}
              onClick={() => navigate('/events')}
            >
              Manage Events
            </Button>
            <Button
              variant="secondary"
              className="w-full justify-start"
              icon={<CalendarDays size={16} />}
              onClick={() => navigate('/schedule/history')}
            >
              View Schedule History
            </Button>
          </div>
        </Card>

        <Card
          title="Recent Schedules"
          actions={
            <Button variant="ghost" size="sm" onClick={() => navigate('/schedule/history')}>
              View all
            </Button>
          }
        >
          {recentSchedules.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">No schedules yet.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {recentSchedules.map((s) => (
                <li
                  key={s.id}
                  className="flex items-center justify-between py-2 cursor-pointer hover:bg-gray-50 rounded px-1"
                  onClick={() => navigate(`/schedule/view/${s.id}`)}
                >
                  <div>
                    <p className="text-sm font-medium text-gray-900">{s.name}</p>
                    {s.score && <p className="text-xs text-gray-400">{s.score}</p>}
                  </div>
                  <Badge
                    label={s.status}
                    variant={STATUS_VARIANT[s.status]}
                    dot
                  />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
