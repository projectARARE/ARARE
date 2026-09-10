import { useCallback, useState, type ReactNode } from 'react'
import ConfirmDialog from '../components/ui/ConfirmDialog'
import { useToast } from '../contexts/ToastContext'

interface UseBulkDeleteOptions<T> {
  getKey: (row: T) => number
  deleteFn: (id: number) => Promise<unknown>
  reload: () => void
  noun: string
  /** Optional copy overrides for 1-vs-many labels */
  singular?: string
  plural?: string
}

/**
 * ServiceNow List-V2 style bulk actions for any Table.
 * Returns the props to hand to Table (`selectable`, `onSelectionChange`,
 * `clearSignal`), a toolbar bar rendered when rows are selected, and the
 * confirm dialog element. Purely client-side: deletes run sequentially
 * through the existing per-id endpoint, then the page reloads.
 */
export function useBulkDelete<T>({
  getKey,
  deleteFn,
  reload,
  noun,
  singular,
  plural,
}: UseBulkDeleteOptions<T>) {
  const { toast } = useToast()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [clearSignal, setClearSignal] = useState(0)

  const label = (count: number) => (count === 1 ? (singular ?? noun) : (plural ?? `${noun}s`))

  const onSelectionChange = useCallback(
    (selected: T[]) => setSelectedIds(selected.map(getKey)),
    [getKey],
  )

  const clear = useCallback(() => {
    setSelectedIds([])
    setClearSignal((s) => s + 1)
  }, [])

  const runDelete = useCallback(async () => {
    setDeleting(true)
    let ok = 0
    let failed = 0
    let firstError: string | undefined
    for (const id of selectedIds) {
      try {
        await deleteFn(id)
        ok += 1
      } catch (e) {
        failed += 1
        firstError ??= e instanceof Error ? e.message : String(e)
      }
    }
    setDeleting(false)
    setConfirmOpen(false)
    clear()
    if (failed === 0) {
      toast.success(`Deleted ${ok} ${label(ok)}`)
    } else {
      toast.error(`Deleted ${ok} of ${ok + failed}${firstError ? ` — ${firstError}` : ''}`)
    }
    reload()
  }, [selectedIds, deleteFn, clear, toast, label])

  const Bar: ReactNode = selectedIds.length > 0 && (
    <div className="flex items-center gap-2 rounded-md border border-indigo-200 bg-indigo-50 px-3 py-1.5">
      <span className="text-xs font-medium text-indigo-700">
        {selectedIds.length} selected
      </span>
      <button
        onClick={() => setConfirmOpen(true)}
        disabled={deleting}
        className="px-2 py-1 text-xs font-medium text-red-700 border border-red-200 rounded-md bg-white hover:bg-red-50 transition-colors disabled:opacity-50"
      >
        Delete…
      </button>
      <button
        onClick={clear}
        disabled={deleting}
        className="px-2 py-1 text-xs font-medium text-gray-500 rounded-md hover:bg-white hover:text-gray-700 transition-colors disabled:opacity-50"
      >
        Clear
      </button>
    </div>
  )

  const Dialog: ReactNode = (
    <ConfirmDialog
      open={confirmOpen}
      title={`Delete ${selectedIds.length} ${label(selectedIds.length)}?`}
      message="This permanently removes the selected records and anything tied to them. This action cannot be undone."
      confirmLabel={deleting ? 'Deleting…' : `Delete ${selectedIds.length}`}
      loading={deleting}
      onConfirm={runDelete}
      onCancel={() => setConfirmOpen(false)}
    />
  )

  return { selectable: true, onSelectionChange, clearSignal, selectedCount: selectedIds.length, Bar, Dialog }
}