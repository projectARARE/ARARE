import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Zap, CalendarDays, Settings, ArrowRight } from 'lucide-react'
import { Card, Button, Badge } from '../components/ui'
import { scheduleApi } from '../services/api'
import { useToast } from '../contexts/ToastContext'
import type { Schedule, ScheduleStatus } from '../types'

const STATUS_VARIANT: Record<ScheduleStatus, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  DRAFT: 'gray',
  ACTIVE: 'green',
  ARCHIVED: 'blue',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
}

export default function Dashboard() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const [recentSchedules, setRecentSchedules] = useState<Schedule[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    scheduleApi
      .getAll()
      .then((schedules) => setRecentSchedules(schedules.slice(0, 5)))
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to load schedules'))
      .finally(() => setLoading(false))
  }, [])

  const openSettings = () => window.dispatchEvent(new Event('arare:open-settings'))

  return (
    <div className="space-y-6">
      <Card className="border-gray-200 text-gray-900 bg-gradient-to-br from-primary-50 via-white to-white">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div className="space-y-1">
            <p className="text-xs uppercase tracking-[0.14em] text-primary-600 font-semibold">
              Next step
            </p>
            <h2 className="text-xl font-semibold text-gray-900">Generate a new timetable</h2>
            <p className="text-sm text-gray-500">
              Build a schedule from your resources in a few clicks — or tweak the defaults first.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              size="lg"
              icon={<Zap size={18} />}
              onClick={() => navigate('/schedule/generate')}
            >
              Generate Timetable
              <ArrowRight size={16} />
            </Button>
            <Button variant="secondary" size="lg" icon={<Settings size={18} />} onClick={openSettings}>
              Settings
            </Button>
          </div>
        </div>
      </Card>

      <Card
        title="Recent Schedules"
        actions={
          <Button variant="ghost" size="sm" onClick={() => navigate('/schedule/history')}>
            View all
          </Button>
        }
        className="border-gray-200 text-gray-900"
      >
        {loading ? (
          <div className="h-24 bg-gray-100 rounded-lg animate-pulse" />
        ) : recentSchedules.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-4">
            No schedules yet. Start with <span className="text-primary-600 font-medium">Generate Timetable</span>.
          </p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {recentSchedules.map((s) => (
              <li
                key={s.id}
                className="flex items-center justify-between py-2 cursor-pointer hover:bg-gray-50 rounded px-1"
                onClick={() => navigate(`/schedule/view/${s.id}`)}
              >
                <div className="flex items-center gap-3">
                  <span className="w-8 h-8 rounded-lg bg-primary-50 text-primary-600 flex items-center justify-center">
                    <CalendarDays size={15} />
                  </span>
                  <div>
                    <p className="text-sm font-medium text-gray-900">{s.name}</p>
                    {s.score && <p className="text-xs text-gray-400">{s.score}</p>}
                  </div>
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
  )
}