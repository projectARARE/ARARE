import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Building2,
  DoorOpen,
  Users,
  BookOpen,
  GraduationCap,
  UsersRound,
  Clock,
  Settings,
  CalendarPlus,
  History,
  CalendarX2,
  Layers,
  Zap,
  CalendarDays,
} from 'lucide-react'

interface NavItem {
  to: string
  icon: React.ComponentType<{ className?: string }>
  label: string
}

interface NavGroup {
  group: string
  items: NavItem[]
}

const nav: NavGroup[] = [
  {
    group: 'Overview',
    items: [{ to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' }],
  },
  {
    group: 'Scheduling',
    items: [
      { to: '/schedule/generate', icon: CalendarPlus, label: 'Generate Schedule' },
      { to: '/schedule/history', icon: History, label: 'History' },
      { to: '/academic-terms', icon: CalendarDays, label: 'Academic Terms' },
      { to: '/events', icon: CalendarX2, label: 'Events' },
    ],
  },
  {
    group: 'Resources',
    items: [
      { to: '/buildings', icon: Building2, label: 'Buildings' },
      { to: '/rooms', icon: DoorOpen, label: 'Rooms' },
      { to: '/teachers', icon: Users, label: 'Teachers' },
    ],
  },
  {
    group: 'Academics',
    items: [
      { to: '/departments', icon: Layers, label: 'Departments' },
      { to: '/subjects', icon: BookOpen, label: 'Subjects' },
      { to: '/batches', icon: GraduationCap, label: 'Batches' },
      { to: '/sections', icon: UsersRound, label: 'Sections' },
      { to: '/timeslots', icon: Clock, label: 'Timeslots' },
    ],
  },
  {
    group: 'Configuration',
    items: [{ to: '/config', icon: Settings, label: 'University Config' }],
  },
]

export default function Sidebar() {
  return (
    <aside className="w-60 shrink-0 bg-white border-r border-gray-200 flex flex-col h-screen sticky top-0 overflow-y-auto">
      <div className="px-6 py-5 border-b border-gray-200 flex items-center gap-3">
        <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center">
          <Zap className="w-4 h-4 text-white" />
        </div>
        <div>
          <h1 className="text-lg font-bold text-primary-700 tracking-tight leading-none">
            ARARE
          </h1>
          <p className="text-xs text-gray-500 mt-0.5">Timetable Scheduler</p>
        </div>
      </div>
      <nav className="flex-1 px-3 py-4 space-y-6">
        {nav.map(({ group, items }) => (
          <div key={group}>
            <p className="px-3 mb-1 text-xs font-semibold text-gray-400 uppercase tracking-wider">
              {group}
            </p>
            <ul className="space-y-0.5">
              {items.map(({ to, icon: Icon, label }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    className={({ isActive }) =>
                      `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-primary-50 text-primary-700'
                          : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                      }`
                    }
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
    </aside>
  )
}
