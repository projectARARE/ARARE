import { useLocation } from 'react-router-dom'
import { Search } from 'lucide-react'

const titles: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/buildings': 'Buildings',
  '/rooms': 'Rooms',
  '/teachers': 'Teachers',
  '/subjects': 'Subjects',
  '/departments': 'Departments',
  '/batches': 'Batches',
  '/sections': 'Class Sections',
  '/timeslots': 'Timeslots',
  '/config': 'University Configuration',
  '/schedule/generate': 'Generate Schedule',
  '/schedule/history': 'Schedule History',
  '/events': 'Events',
  '/disruptions': 'Disruptions',
  '/import/csv': 'Import & Export',
}

export default function Header() {
  const { pathname } = useLocation()
  const title =
    titles[pathname] ??
    (pathname.startsWith('/schedule/view/') ? 'Timetable Viewer' : 'ARARE')

  const openPalette = () => window.dispatchEvent(new Event('arare:open-command-palette'))

  return (
    <header className="sticky top-0 z-10 bg-white border-b border-gray-200 px-8 py-4 flex items-center justify-between">
      <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
      <button
        type="button"
        onClick={openPalette}
        className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 hover:text-gray-700"
      >
        <Search size={13} />
        Search… <kbd className="rounded border border-gray-300 bg-white px-1 text-[10px]">Ctrl K</kbd>
      </button>
    </header>
  )
}
