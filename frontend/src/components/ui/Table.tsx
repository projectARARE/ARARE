import { useState, useMemo, useCallback, type ReactNode, type ChangeEvent } from 'react'
import { ChevronUp, ChevronDown, ChevronsUpDown, Search } from 'lucide-react'
import type { ContextMenuItem } from './ContextMenu'
import ContextMenu from './ContextMenu'

export interface Column<T> {
  key: string
  header: string
  render: (row: T, index: number) => ReactNode
  width?: string
  /** Provide this to make the column sortable */
  sortValue?: (row: T) => string | number | null | undefined
}

interface ContextMenuState {
  x: number
  y: number
  items: ContextMenuItem[]
}

interface TableProps<T> {
  columns: Column<T>[]
  data: T[]
  loading?: boolean
  emptyMessage?: string
  keyExtractor: (row: T) => string | number
  /** Show a search box above the table */
  searchable?: boolean
  /** Functions to extract searchable text from a row (defaults to column render text) */
  searchKeys?: ((row: T) => string)[]
  /** Enable checkbox row-selection */
  selectable?: boolean
  onSelectionChange?: (selected: T[]) => void
  /** Build a right-click context menu for a given row */
  onRowContextMenu?: (row: T) => ContextMenuItem[]
}

type SortDir = 'asc' | 'desc' | null

export default function Table<T>({
  columns,
  data,
  loading = false,
  emptyMessage = 'No records found.',
  keyExtractor,
  searchable = false,
  searchKeys,
  selectable = false,
  onSelectionChange,
  onRowContextMenu,
}: TableProps<T>) {
  const [query, setQuery] = useState('')
  const [sortKey, setSortKey] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<SortDir>(null)
  const [selected, setSelected] = useState<Set<string | number>>(new Set())
  const [ctxMenu, setCtxMenu] = useState<ContextMenuState | null>(null)

  // ─── Search ────────────────────────────────────────────────────────────────

  const filtered = useMemo(() => {
    if (!query.trim()) return data
    const q = query.toLowerCase()
    return data.filter((row) => {
      if (searchKeys) return searchKeys.some((fn) => fn(row).toLowerCase().includes(q))
      return columns.some((col) => {
        const cell = col.render(row, 0)
        if (typeof cell === 'string' || typeof cell === 'number') {
          return String(cell).toLowerCase().includes(q)
        }
        return false
      })
    })
  }, [data, query, searchKeys, columns])

  // ─── Sort ──────────────────────────────────────────────────────────────────

  const sorted = useMemo(() => {
    if (!sortKey || !sortDir) return filtered
    const col = columns.find((c) => c.key === sortKey)
    if (!col?.sortValue) return filtered
    return [...filtered].sort((a, b) => {
      const va = col.sortValue!(a) ?? ''
      const vb = col.sortValue!(b) ?? ''
      const cmp = va < vb ? -1 : va > vb ? 1 : 0
      return sortDir === 'asc' ? cmp : -cmp
    })
  }, [filtered, sortKey, sortDir, columns])

  const handleSort = (key: string) => {
    if (sortKey !== key) { setSortKey(key); setSortDir('asc'); return }
    if (sortDir === 'asc') { setSortDir('desc'); return }
    setSortKey(null); setSortDir(null)
  }

  const SortIcon = ({ colKey }: { colKey: string }) => {
    if (sortKey !== colKey) return <ChevronsUpDown size={12} className="text-gray-400" />
    return sortDir === 'asc'
      ? <ChevronUp size={12} className="text-indigo-600" />
      : <ChevronDown size={12} className="text-indigo-600" />
  }

  // ─── Selection ─────────────────────────────────────────────────────────────

  const allKeys = sorted.map(keyExtractor)
  const allSelected = allKeys.length > 0 && allKeys.every((k) => selected.has(k))
  const someSelected = allKeys.some((k) => selected.has(k)) && !allSelected

  const toggleAll = useCallback(() => {
    const next = allSelected
      ? new Set<string | number>()
      : new Set<string | number>(allKeys)
    setSelected(next)
    onSelectionChange?.(sorted.filter((r) => next.has(keyExtractor(r))))
  }, [allSelected, allKeys, sorted, keyExtractor, onSelectionChange])

  const toggleRow = useCallback((row: T) => {
    const k = keyExtractor(row)
    const next = new Set(selected)
    next.has(k) ? next.delete(k) : next.add(k)
    setSelected(next)
    onSelectionChange?.(sorted.filter((r) => next.has(keyExtractor(r))))
  }, [selected, sorted, keyExtractor, onSelectionChange])

  // ─── Context menu ──────────────────────────────────────────────────────────

  const handleContextMenu = (e: React.MouseEvent, row: T) => {
    if (!onRowContextMenu) return
    e.preventDefault()
    const items = onRowContextMenu(row)
    if (!items.length) return
    setCtxMenu({ x: e.clientX, y: e.clientY, items })
  }

  const colCount = columns.length + (selectable ? 1 : 0)

  return (
    <div className="space-y-3">
      {searchable && (
        <div className="relative">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setQuery(e.target.value)}
            placeholder="Search…"
            className="w-full sm:w-64 pl-8 pr-8 py-1.5 text-sm border border-gray-300 rounded-md
                       placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-xs"
            >
              ✕
            </button>
          )}
        </div>
      )}

      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50">
            <tr>
              {selectable && (
                <th className="px-4 py-3 w-10">
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={(el) => { if (el) el.indeterminate = someSelected }}
                    onChange={toggleAll}
                    className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                  />
                </th>
              )}
              {columns.map((col) => (
                <th
                  key={col.key}
                  className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide"
                  style={col.width ? { width: col.width } : undefined}
                >
                  {col.sortValue ? (
                    <button
                      onClick={() => handleSort(col.key)}
                      className="flex items-center gap-1 hover:text-gray-900 transition-colors"
                    >
                      {col.header}
                      <SortIcon colKey={col.key} />
                    </button>
                  ) : (
                    col.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {loading ? (
              <tr>
                <td colSpan={colCount} className="px-4 py-8 text-center">
                  <div className="flex items-center justify-center gap-2 text-gray-400">
                    <div className="w-4 h-4 border-2 border-gray-300 border-t-indigo-500 rounded-full animate-spin" />
                    Loading…
                  </div>
                </td>
              </tr>
            ) : sorted.length === 0 ? (
              <tr>
                <td colSpan={colCount} className="px-4 py-8 text-center text-gray-400">
                  {query ? `No results matching "${query}"` : emptyMessage}
                </td>
              </tr>
            ) : (
              sorted.map((row, i) => {
                const key = keyExtractor(row)
                const isSelected = selected.has(key)
                return (
                  <tr
                    key={key}
                    onContextMenu={(e) => handleContextMenu(e, row)}
                    className={`transition-colors ${isSelected ? 'bg-indigo-50' : 'hover:bg-gray-50'}`}
                  >
                    {selectable && (
                      <td className="px-4 py-3 w-10">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleRow(row)}
                          className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                        />
                      </td>
                    )}
                    {columns.map((col) => (
                      <td key={col.key} className="px-4 py-3 text-gray-700">
                        {col.render(row, i)}
                      </td>
                    ))}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {!loading && sorted.length > 0 && (
        <p className="text-xs text-gray-400 px-1">
          {query
            ? `${sorted.length} of ${data.length} records`
            : `${data.length} record${data.length !== 1 ? 's' : ''}`}
          {selectable && selected.size > 0 && ` · ${selected.size} selected`}
        </p>
      )}

      {ctxMenu && (
        <ContextMenu
          x={ctxMenu.x}
          y={ctxMenu.y}
          items={ctxMenu.items}
          onClose={() => setCtxMenu(null)}
        />
      )}
    </div>
  )
}
