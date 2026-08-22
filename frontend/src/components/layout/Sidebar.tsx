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
  AlertTriangle,
  BarChart3,
  GitCompare,
  FileSpreadsheet,
  UserCheck,
  School,
  BookCopy,
  ClipboardList,
} from 'lucide-react'
import { useUiPreferences, type FeatureKey } from '../../contexts/UiPreferencesContext'

interface NavItem {
  to: string
  icon: React.ComponentType<{ className?: string }>
  label: string
  feature?: FeatureKey
}

interface NavGroup {
  group: string
  items: NavItem[]
}

const nav: NavGroup[] = [
  {
    group: 'Overview',
    items: [
      { to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
      { to: '/analytics', icon: BarChart3, label: 'Analytics', feature: 'analytics' },
    ],
  },
  {
    group: 'Scheduling',
    items: [
      { to: '/schedule/generate', icon: CalendarPlus, label: 'Generate Schedule' },
      { to: '/schedule/history', icon: History, label: 'History' },
      { to: '/sessions/manual', icon: ClipboardList, label: 'Manual Sessions', feature: 'manualSessions' },
      { to: '/what-if', icon: GitCompare, label: 'What-If Compare', feature: 'whatIf' },
      { to: '/academic-terms', icon: CalendarDays, label: 'Academic Terms', feature: 'academicTerms' },
      { to: '/events', icon: CalendarX2, label: 'Events', feature: 'events' },
      { to: '/disruptions', icon: AlertTriangle, label: 'Disruptions', feature: 'disruptions' },
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
      { to: '/institutes', icon: School, label: 'Institutes' },
      { to: '/departments', icon: Layers, label: 'Departments' },
      { to: '/subjects', icon: BookOpen, label: 'Subjects' },
      { to: '/offerings', icon: BookCopy, label: 'Subject Offerings' },
      { to: '/batches', icon: GraduationCap, label: 'Batches' },
      { to: '/sections', icon: UsersRound, label: 'Sections' },
      { to: '/assignments', icon: UserCheck, label: 'Teacher Assignments' },
      { to: '/timeslots', icon: Clock, label: 'Timeslots' },
    ],
  },
  {
    group: 'Configuration',
    items: [
      { to: '/config', icon: Settings, label: 'University Config' },
      { to: '/import/csv', icon: FileSpreadsheet, label: 'Import / Export', feature: 'importExport' },
    ],
  },
]

export default function Sidebar() {
  const { prefs } = useUiPreferences()

  const visibleNav = nav
    .map(({ group, items }) => ({
      group,
      items: items.filter((item) => !item.feature || prefs[item.feature]),
    }))
    .filter(({ items }) => items.length > 0)

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
        {visibleNav.map(({ group, items }) => (
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
