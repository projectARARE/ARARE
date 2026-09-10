import { useState, type ReactNode } from 'react'
import { Filter, ChevronDown } from 'lucide-react'

interface FilterPanelProps {
  /** Number of currently-active filters; shows a badge when > 0. */
  activeCount?: number
  children: ReactNode
  /** Optional localStorage key to remember open/closed across visits. */
  persistKey?: string
  className?: string
}

/**
 * Collapsible filter bar (ServiceNow-style list filters). Defaults to CLOSED
 * so the table is the hero; click "Filters" to expand the controls. When
 * filters are active a count badge appears on the toggle.
 */
export default function FilterPanel({ activeCount = 0, children, persistKey, className }: FilterPanelProps) {
  const [open, setOpen] = useState(() =>
    persistKey ? localStorage.getItem(persistKey) === 'open' : false,
  )

  const toggle = () =>
    setOpen((o) => {
      const next = !o
      if (persistKey) localStorage.setItem(persistKey, next ? 'open' : 'closed')
      return next
    })

  return (
    <div className={className}>
      <div className="flex items-center">
        <button
          type="button"
          onClick={toggle}
          title={open ? 'Hide filters' : 'Show filters'}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50 transition-colors"
        >
          <Filter size={13} />
          Filters
          {activeCount > 0 && (
            <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] rounded-full bg-indigo-600 text-white text-[10px] font-semibold px-1">
              {activeCount}
            </span>
          )}
          <ChevronDown size={13} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      </div>
      {open && (
        <div className="mt-2 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div className="flex flex-wrap items-end gap-3">{children}</div>
        </div>
      )}
    </div>
  )
}