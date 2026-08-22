import { useLocation } from 'react-router-dom'
import { Search, Settings } from 'lucide-react'

const titles: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/analytics': 'Analytics',
  '/buildings': 'Buildings',
  '/rooms': 'Rooms',
  '/teachers': 'Teachers',
  '/subjects': 'Subjects',
  '/departments': 'Departments',
  '/institutes': 'Institutes',
  '/batches': 'Batches',
  '/sections': 'Class Sections',
  '/assignments': 'Teacher Assignments',
  '/offerings': 'Subject Offerings',
  '/timeslots': 'Timeslots',
  '/config': 'University Configuration',
  '/schedule/generate': 'Generate Schedule',
  '/schedule/history': 'Schedule History',
  '/sessions/manual': 'Manual Sessions',
  '/events': 'Events',
  '/disruptions': 'Disruptions',
  '/academic-terms': 'Academic Terms',
  '/what-if': 'What-If Compare',
  '/import/csv': 'Import & Export',
}

export default function Header() {
  const { pathname } = useLocation()
  const title =
    titles[pathname] ??
    (pathname.startsWith('/schedule/view/') ? 'Timetable Viewer' : 'ARARE')

  const openPalette = () => window.dispatchEvent(new Event('arare:open-command-palette'))
  const openSettings = () => window.dispatchEvent(new Event('arare:open-settings'))

  return (
    <header className="sticky top-0 z-10 bg-white border-b border-gray-200 px-8 py-4 flex items-center justify-between">
      <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={openPalette}
          className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          <Search size={13} />
          Search… <kbd className="rounded border border-gray-300 bg-white px-1 text-[10px]">Ctrl K</kbd>
        </button>
        <button
          type="button"
          onClick={openSettings}
          aria-label="Settings"
          title="Settings"
          className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          <Settings size={13} />
          Settings
        </button>
      </div>
    </header>
  )
}
