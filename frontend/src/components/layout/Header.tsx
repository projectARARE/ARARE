import { useLocation } from 'react-router-dom'

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
}

export default function Header() {
  const { pathname } = useLocation()
  const title =
    titles[pathname] ??
    (pathname.startsWith('/schedule/view/') ? 'Timetable Viewer' : 'ARARE')

  return (
    <header className="sticky top-0 z-10 bg-white border-b border-gray-200 px-8 py-4">
      <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
    </header>
  )
}
