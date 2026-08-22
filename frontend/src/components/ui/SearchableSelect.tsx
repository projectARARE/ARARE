import { useEffect, useMemo, useRef, useState } from 'react'
import { Check, ChevronDown, Search, X } from 'lucide-react'

export interface SearchableSelectOption {
  value: string | number
  label: string
  group?: string
}

interface SearchableSelectProps {
  options: SearchableSelectOption[]
  value: string | number | null
  onChange: (value: string | number | null) => void
  label?: string
  placeholder?: string
  searchable?: boolean
  allowClear?: boolean
  disabled?: boolean
  error?: string
  helpText?: string
  maxHeight?: number
  maxRendered?: number
  className?: string
}

/**
 * Single-select combobox with type-to-search, keyboard navigation and
 * windowed rendering so it stays usable with thousands of options.
 * Value semantics: a plain `string | number | null` (no 0-sentinel needed).
 */
export default function SearchableSelect({
  options,
  value,
  onChange,
  label,
  placeholder = 'Select…',
  searchable = true,
  allowClear = false,
  disabled = false,
  error,
  helpText,
  maxHeight = 280,
  maxRendered = 40,
  className = '',
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)

  const grouped = useMemo(() => {
    const hasGroups = options.some((o) => o.group)
    if (!hasGroups) return [{ group: null, items: options }]
    const map = new Map<string | null, SearchableSelectOption[]>()
    for (const opt of options) {
      const g = opt.group ?? ''
      if (!map.has(g)) map.set(g, [])
      map.get(g)!.push(opt)
    }
    return [...map.entries()].map(([g, items]) => ({ group: g, items }))
  }, [options])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return grouped
    return grouped
      .map((g) => ({
        group: g.group,
        items: g.items.filter((o) => o.label.toLowerCase().includes(q)),
      }))
      .filter((g) => g.items.length > 0)
  }, [grouped, query])

  const flatItems = useMemo(() => filtered.flatMap((g) => g.items), [filtered])

  useEffect(() => {
    if (!open) return
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
  }, [open])

  useEffect(() => {
    if (open) {
      setActiveIndex(0)
      setQuery('')
    }
  }, [open])

  const selectedOption = options.find((o) => o.value === value) ?? null

  const scrollToActive = (index: number) => {
    const list = listRef.current
    if (!list) return
    const el = list.querySelector<HTMLElement>(`[data-index="${index}"]`)
    el?.scrollIntoView({ block: 'nearest' })
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return
    if (!open && (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault()
      setOpen(true)
      return
    }
    if (!open) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      const next = Math.min(activeIndex + 1, flatItems.length - 1)
      setActiveIndex(next)
      scrollToActive(next)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      const next = Math.max(activeIndex - 1, 0)
      setActiveIndex(next)
      scrollToActive(next)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const item = flatItems[activeIndex]
      if (item) {
        onChange(item.value)
        setOpen(false)
      }
    } else if (e.key === 'Escape') {
      e.preventDefault()
      setOpen(false)
    } else if (e.key === 'Tab') {
      setOpen(false)
    }
  }

  const windowed = flatItems.length > maxRendered ? flatItems.slice(0, maxRendered) : flatItems

  return (
    <div ref={rootRef} className={`space-y-1 ${className}`}>
      {label && <label className="form-label">{label}</label>}
      <div className="relative">
        <button
          type="button"
          disabled={disabled}
          onClick={() => setOpen((v) => !v)}
          onKeyDown={handleKeyDown}
          className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm text-left focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500 disabled:bg-gray-100 disabled:cursor-not-allowed ${
            error ? 'border-red-300' : 'border-gray-300'
          }`}
        >
          <span className="flex items-center justify-between gap-2">
            <span className={`truncate ${selectedOption ? 'text-gray-900' : 'text-gray-400'}`}>
              {selectedOption?.label ?? placeholder}
            </span>
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
                    onChange={(e) => {
                      setQuery(e.target.value)
                      setActiveIndex(0)
                    }}
                    placeholder="Search…"
                    className="w-full rounded-md border border-gray-200 pl-8 pr-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-primary-500"
                  />
                </div>
              </div>
            )}
            <div ref={listRef} className="overflow-y-auto py-1" style={{ maxHeight }}>
              {flatItems.length === 0 && (
                <p className="px-3 py-2 text-sm text-gray-400">No options.</p>
              )}
              {windowed.map((opt, idx) => {
                const flatIdx = flatItems.indexOf(opt)
                return (
                  <div key={opt.value} data-index={flatIdx}>
                    <button
                      type="button"
                      onClick={() => {
                        onChange(opt.value)
                        setOpen(false)
                      }}
                      onMouseEnter={() => setActiveIndex(flatIdx)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-1.5 text-sm text-left ${
                        activeIndex === flatIdx ? 'bg-primary-50 text-primary-800' : 'text-gray-700 hover:bg-gray-50'
                      }`}
                    >
                      <span className="truncate">{opt.label}</span>
                      {value === opt.value && <Check size={13} className="shrink-0 text-primary-600" />}
                    </button>
                    {idx < windowed.length - 1 && windowed[idx + 1].group && (
                      <div className="mt-1" />
                    )}
                  </div>
                )
              })}
              {flatItems.length > maxRendered && (
                <p className="px-3 py-2 text-xs text-gray-400 border-t border-gray-100">
                  {flatItems.length - maxRendered} more match — refine your search
                </p>
              )}
            </div>
            {allowClear && value !== null && value !== '' && (
              <div className="flex justify-end border-t border-gray-100 px-2 py-1">
                <button
                  type="button"
                  onClick={() => {
                    onChange(null)
                    setOpen(false)
                  }}
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
      {helpText && !error && <p className="text-xs text-gray-500">{helpText}</p>}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
