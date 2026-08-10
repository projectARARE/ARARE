import { useEffect, useMemo, useRef, useState } from 'react'
import { Check, ChevronDown, Search, X } from 'lucide-react'

interface MultiSelectOption {
  value: number
  label: string
}

interface MultiSelectProps {
  options: MultiSelectOption[]
  selected: number[]
  onChange: (next: number[]) => void
  label?: string
  placeholder?: string
  searchable?: boolean
  maxHeight?: number
}

export default function MultiSelect({
  options,
  selected,
  onChange,
  label,
  placeholder = 'Select…',
  searchable = true,
  maxHeight = 240,
}: MultiSelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handle = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent && e.key !== 'Escape') return
      if (e instanceof MouseEvent && rootRef.current?.contains(e.target as Node)) return
      setOpen(false)
    }
    document.addEventListener('mousedown', handle)
    document.addEventListener('keydown', handle)
    return () => {
      document.removeEventListener('mousedown', handle)
      document.removeEventListener('keydown', handle)
    }
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return options
    return options.filter((o) => o.label.toLowerCase().includes(q))
  }, [options, query])

  const labelById = useMemo(() => new Map(options.map((o) => [o.value, o.label])), [options])

  const toggle = (value: number) => {
    onChange(selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value])
  }

  const displayText =
    selected.length === 0
      ? placeholder
      : selected.length === options.length && options.length > 0
        ? `All (${options.length})`
        : selected.length === 1
          ? labelById.get(selected[0]) ?? placeholder
          : `${selected.length} selected`

  return (
    <div ref={rootRef} className="space-y-1">
      {label && <label className="form-label">{label}</label>}
      <div className="relative">
        <button
          type="button"
          onClick={() => { setOpen((v) => !v); setQuery('') }}
          className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm text-left focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        >
          <span className="flex items-center justify-between gap-2">
            <span className={selected.length === 0 ? 'text-gray-400' : 'text-gray-900'}>{displayText}</span>
            <ChevronDown size={14} className={`shrink-0 text-gray-400 transition-transform ${open ? 'rotate-180' : ''}`} />
          </span>
        </button>

        {open && (
          <div className="absolute z-20 mt-1 w-full rounded-md border border-gray-200 bg-white shadow-lg overflow-hidden">
            {searchable && (
              <div className="p-2 border-b border-gray-100">
                <div className="relative">
                  <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    autoFocus
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search…"
                    className="w-full rounded-md border border-gray-200 pl-8 pr-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-primary-500"
                  />
                </div>
              </div>
            )}
            <div className="overflow-y-auto py-1" style={{ maxHeight }}>
              {filtered.length === 0 && (
                <p className="px-3 py-2 text-sm text-gray-400">No options.</p>
              )}
              {filtered.map((opt) => {
                const checked = selected.includes(opt.value)
                return (
                  <label
                    key={opt.value}
                    onClick={() => toggle(opt.value)}
                    className="flex items-center gap-2 px-3 py-1.5 text-sm cursor-pointer hover:bg-gray-50"
                  >
                    <span
                      className={`flex h-4 w-4 items-center justify-center rounded border transition-colors ${
                        checked ? 'bg-primary-600 border-primary-600 text-white' : 'border-gray-300 bg-white'
                      }`}
                    >
                      {checked && <Check size={11} />}
                    </span>
                    <span className={checked ? 'font-medium text-gray-900' : 'text-gray-700'}>{opt.label}</span>
                  </label>
                )
              })}
            </div>
            {selected.length > 0 && (
              <div className="flex justify-end border-t border-gray-100 px-2 py-1">
                <button
                  type="button"
                  onClick={() => onChange([])}
                  className="flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-500 hover:bg-gray-100"
                >
                  <X size={11} />
                  Clear
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}