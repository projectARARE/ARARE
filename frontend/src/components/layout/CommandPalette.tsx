import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  BookOpen,
  CalendarDays,
  CalendarPlus,
  CalendarX2,
  Clock,
  DoorOpen,
  FileSpreadsheet,
  GraduationCap,
  History,
  LayoutDashboard,
  Search,
  Users,
  UsersRound,
} from 'lucide-react'
import { scheduleApi, teacherApi, roomApi, subjectApi } from '../../services/api'
import { useUiPreferences, type FeatureKey } from '../../contexts/UiPreferencesContext'
import type { Schedule } from '../../types'

interface PaletteItem {
  id: string
  label: string
  hint?: string
  feature?: FeatureKey
  run: () => void
}

const NAV_ICONS: Record<string, typeof LayoutDashboard> = {
  'nav-dashboard': LayoutDashboard,
  'nav-generate': CalendarPlus,
  'nav-history': History,
  'nav-events': CalendarX2,
  'nav-disruptions': CalendarDays,
  'nav-teachers': Users,
  'nav-rooms': DoorOpen,
  'nav-subjects': BookOpen,
  'nav-batches': GraduationCap,
  'nav-sections': UsersRound,
  'nav-timeslots': Clock,
  'nav-import': FileSpreadsheet,
}

export default function CommandPalette({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const { prefs } = useUiPreferences()
  const [query, setQuery] = useState('')
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [teachers, setTeachers] = useState<{ id: number; name: string }[]>([])
  const [rooms, setRooms] = useState<{ id: number; roomNumber: string; buildingName?: string }[]>([])
  const [subjects, setSubjects] = useState<{ id: number; name: string; code?: string }[]>([])
  const [selected, setSelected] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
    scheduleApi.getAll().then(setSchedules).catch(() => {})
    teacherApi.getAll().then(setTeachers).catch(() => {})
    roomApi.getAll().then(setRooms).catch(() => {})
    subjectApi.getAll().then(setSubjects).catch(() => {})
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const openSchedule = (id: number) => {
    onClose()
    navigate(`/schedule/view/${id}`)
  }

  const go = (path: string) => () => {
    onClose()
    navigate(path)
  }

  const NAV_ITEMS: PaletteItem[] = [
    { id: 'nav-dashboard', label: 'Dashboard', hint: '/dashboard', run: go('/dashboard') },
    { id: 'nav-generate', label: 'Generate Schedule', hint: '/schedule/generate', run: go('/schedule/generate') },
    { id: 'nav-history', label: 'Schedule History', hint: '/schedule/history', run: go('/schedule/history') },
    { id: 'nav-events', label: 'Events', hint: '/events', feature: 'events', run: go('/events') },
    { id: 'nav-disruptions', label: 'Disruptions', hint: '/disruptions', feature: 'disruptions', run: go('/disruptions') },
    { id: 'nav-teachers', label: 'Teachers', hint: '/teachers', run: go('/teachers') },
    { id: 'nav-rooms', label: 'Rooms', hint: '/rooms', run: go('/rooms') },
    { id: 'nav-subjects', label: 'Subjects', hint: '/subjects', run: go('/subjects') },
    { id: 'nav-batches', label: 'Batches', hint: '/batches', run: go('/batches') },
    { id: 'nav-sections', label: 'Class Sections', hint: '/sections', run: go('/sections') },
    { id: 'nav-timeslots', label: 'Timeslots', hint: '/timeslots', run: go('/timeslots') },
    { id: 'nav-import', label: 'Import / Export', hint: '/import/csv', feature: 'importExport', run: go('/import/csv') },
  ]

  const visibleNav = useMemo(() => NAV_ITEMS.filter((i) => !i.feature || prefs[i.feature]), [prefs])

  const items = useMemo<PaletteItem[]>(() => {
    const q = query.trim().toLowerCase()
    const result: PaletteItem[] = []
    if (q) {
      const nav = visibleNav.filter((i) => i.label.toLowerCase().includes(q) || (i.hint ?? '').includes(q))
      result.push(...nav)
      schedules.filter((s) => s.name.toLowerCase().includes(q)).forEach((s) =>
        result.push({ id: `sched-${s.id}`, label: `Schedule: ${s.name}`, hint: `[${s.status}]`, run: () => openSchedule(s.id) }))
      teachers.filter((t) => t.name.toLowerCase().includes(q)).forEach((t) =>
        result.push({ id: `teacher-${t.id}`, label: `Teacher: ${t.name}`, hint: 'teacher', run: () => { onClose(); navigate(`/teachers`) } }))
      rooms.filter((r) => r.roomNumber.toLowerCase().includes(q)).forEach((r) =>
        result.push({ id: `room-${r.id}`, label: `Room: ${r.roomNumber}`, hint: r.buildingName ?? 'room', run: () => { onClose(); navigate(`/rooms`) } }))
      subjects.filter((s) => s.name.toLowerCase().includes(q) || (s.code ?? '').toLowerCase().includes(q)).forEach((s) =>
        result.push({ id: `subject-${s.id}`, label: `Subject: ${s.name}`, hint: s.code, run: () => { onClose(); navigate(`/subjects`) } }))
    } else {
      result.push(...visibleNav)
      schedules.slice(0, 8).forEach((s) =>
        result.push({ id: `sched-${s.id}`, label: `Schedule: ${s.name}`, hint: `[${s.status}]`, run: () => openSchedule(s.id) }))
    }
    return result.slice(0, 12)
  }, [query, visibleNav, schedules, teachers, rooms, subjects])

  useEffect(() => setSelected(0), [query])

  const runSelected = () => {
    items[selected]?.run()
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-slate-900/40 pt-24 px-4"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="w-full max-w-xl rounded-xl border border-slate-200 bg-white shadow-2xl overflow-hidden">
        <div className="flex items-center gap-2 border-b border-slate-200 px-4 py-3">
          <Search size={16} className="text-slate-400 shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') { e.preventDefault(); setSelected((x) => Math.min(items.length - 1, x + 1)) }
              if (e.key === 'ArrowUp') { e.preventDefault(); setSelected((x) => Math.max(0, x - 1)) }
              if (e.key === 'Enter') { e.preventDefault(); runSelected() }
            }}
            placeholder="Search pages, schedules, teachers, rooms, subjects… (Esc to close)"
            className="flex-1 bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
          />
          <kbd className="rounded border border-slate-300 bg-slate-50 px-1.5 py-0.5 text-[10px] text-slate-500">Esc</kbd>
        </div>
        <ul className="max-h-96 overflow-y-auto p-2">
          {items.length === 0 && (
            <li className="px-3 py-6 text-center text-sm text-slate-500">No matches</li>
          )}
          {items.map((item, i) => {
            const Icon = NAV_ICONS[item.id] ?? ArrowRight
            return (
              <li key={item.id}>
                <button
                  type="button"
                  onMouseEnter={() => setSelected(i)}
                  onClick={item.run}
                  className={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm ${
                    i === selected ? 'bg-cyan-50 text-cyan-900' : 'text-slate-700'
                  }`}
                >
                  <Icon size={14} className="shrink-0 text-slate-400" />
                  <span className="flex-1 truncate">{item.label}</span>
                  {item.hint && <span className="shrink-0 text-xs text-slate-400">{item.hint}</span>}
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
