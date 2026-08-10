import { useEffect, useMemo, useRef, useState } from 'react'
import { ChevronDown, ChevronRight, Download, FileUp, Upload } from 'lucide-react'
import { Button, Card, Spinner } from '../components/ui'
import { importApi } from '../services/api'
import type { CsvImportResponse, CsvZipImportResponse, ImportOrderStep } from '../types'

interface EntityImportState {
  loading: boolean
  exporting: boolean
  result: CsvImportResponse | null
  error: string | null
  parseError: string | null
  dryRun: boolean
  content: string
  fileName: string
  expanded: boolean
}

function summarizeCsv(text: string) {
  const trimmed = text.trim()
  if (!trimmed) {
    return { rowCount: 0, columnCount: 0, headers: [] as string[] }
  }
  const lines = trimmed.split(/\r?\n/).filter((line) => line.trim().length > 0)
  const headers = (lines[0] ?? '').split(',').map((value) => value.trim()).filter(Boolean)
  return {
    rowCount: Math.max(0, lines.length - 1),
    columnCount: headers.length,
    headers,
  }
}

function downloadBlob(fileName: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function EntityImportCard({ step }: { step: ImportOrderStep }) {
  const [state, setState] = useState<EntityImportState>({
    loading: false,
    exporting: false,
    result: null,
    error: null,
    parseError: null,
    dryRun: false,
    content: '',
    fileName: '',
    expanded: false,
  })
  const fileInputRef = useRef<HTMLInputElement>(null)
  const csvPreview = useMemo(() => summarizeCsv(state.content), [state.content])

  const patch = (p: Partial<EntityImportState>) => setState((prev) => ({ ...prev, ...p }))

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      patch({ content: text, fileName: file.name, parseError: null })
    } catch {
      patch({ parseError: 'Could not read the file.' })
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const runImport = async () => {
    patch({ loading: true, error: null, result: null })
    try {
      const res = await importApi.importCsv(step.name, state.content, state.dryRun)
      patch({ result: res })
    } catch (err) {
      patch({ error: err instanceof Error ? err.message : 'Import failed' })
    } finally {
      patch({ loading: false })
    }
  }

  const runExport = async () => {
    patch({ exporting: true, error: null })
    try {
      const blob = await importApi.exportTemplateCsv(step.name)
      downloadBlob(step.fileName.replace('.csv', '-template.csv'), blob)
    } catch (err) {
      patch({ error: err instanceof Error ? err.message : 'Export failed' })
    } finally {
      patch({ exporting: false })
    }
  }

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden bg-white">
      <button
        type="button"
        onClick={() => patch({ expanded: !state.expanded })}
        className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-gray-50"
      >
        <span className="flex items-center gap-2">
          {state.expanded ? <ChevronDown size={14} className="text-gray-400" /> : <ChevronRight size={14} className="text-gray-400" />}
          <span className="font-medium text-sm text-gray-900">{step.displayName}</span>
          <code className="text-xs text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">{step.fileName}</code>
        </span>
        <span className="flex items-center gap-2">
          {step.dependencies.length > 0 && (
            <span className="text-xs text-gray-500 hidden sm:inline">
              needs {step.dependencies.join(', ')}
            </span>
          )}
          {state.loading && <Spinner size="sm" />}
          {state.result && !state.result.errors.length && (
            <span className="text-xs text-green-600 font-medium">
              +{state.result.created} ~{state.result.updated} ✓
            </span>
          )}
          {state.result && state.result.errors.length > 0 && (
            <span className="text-xs text-red-600 font-medium">{state.result.errors.length} errors</span>
          )}
        </span>
      </button>

      {state.expanded && (
        <div className="px-4 pb-4 space-y-3 border-t border-gray-100 pt-3">
          <div className="flex flex-wrap items-center gap-3">
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,text/csv"
              className="hidden"
              onChange={handleFile}
            />
            <Button variant="secondary" size="sm" icon={<FileUp size={13} />} onClick={() => fileInputRef.current?.click()}>
              Load CSV file
            </Button>
            {state.fileName && <span className="text-xs text-gray-500">{state.fileName}</span>}
            <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer select-none ml-auto">
              <input
                type="checkbox"
                checked={state.dryRun}
                onChange={(e) => patch({ dryRun: e.target.checked })}
              />
              Dry run
            </label>
          </div>

          <textarea
            value={state.content}
            onChange={(e) => patch({ content: e.target.value, parseError: null })}
            placeholder={`Paste ${step.fileName} content here (or load a file) — UTF-8 CSV, header row required.`}
            rows={5}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />

          <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium text-gray-800">Preview</span>
              <span>{csvPreview.rowCount} data row{csvPreview.rowCount === 1 ? '' : 's'}</span>
              <span>{csvPreview.columnCount} column{csvPreview.columnCount === 1 ? '' : 's'}</span>
            </div>
            {csvPreview.headers.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {csvPreview.headers.map((header) => (
                  <span key={header} className="rounded-full border border-gray-200 bg-white px-2 py-0.5 text-[11px] text-gray-700">
                    {header}
                  </span>
                ))}
              </div>
            )}
            {state.content.trim() && csvPreview.rowCount === 0 && (
              <p className="text-amber-700">Add sample rows below the header so the import guidance stays useful.</p>
            )}
          </div>

          {state.parseError && <p className="text-xs text-red-600">{state.parseError}</p>}
          {state.error && (
            <div className="rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {state.error}
            </div>
          )}

          <div className="flex items-center gap-3">
            <Button size="sm" icon={<Upload size={13} />} loading={state.loading} disabled={!state.content.trim()} onClick={runImport}>
              Import
            </Button>
            <Button variant="secondary" size="sm" icon={<Download size={13} />} loading={state.exporting} onClick={runExport}>
              Download template CSV
            </Button>
          </div>

          {state.result && (
            <div className="rounded-md border border-gray-200 bg-gray-50 p-3 text-sm">
              <p className="flex items-center gap-3 text-gray-700">
                {state.result.dryRun && <span className="text-amber-700">Dry run — nothing written.</span>}
                <span className="text-green-600">Created: {state.result.created}</span>
                <span className="text-blue-600">Updated: {state.result.updated}</span>
                <span className="text-amber-600">Skipped: {state.result.skipped}</span>
              </p>
              {state.result.errors.length > 0 && (
                <div className="mt-2 max-h-40 overflow-y-auto space-y-1 text-xs text-red-700 bg-red-50 border border-red-100 rounded p-2">
                  {state.result.errors.map((err, idx) => (
                    <div key={idx}>{err}</div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default function CsvImport() {
  const [order, setOrder] = useState<ImportOrderStep[]>([])
  const [orderLoading, setOrderLoading] = useState(true)
  const [zipLoading, setZipLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [zipResult, setZipResult] = useState<CsvZipImportResponse | null>(null)
  const [dryRun, setDryRun] = useState(false)

  const zipInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    importApi.importOrder()
      .then(setOrder)
      .catch(() => setError('Could not load the import order — export still works.'))
      .finally(() => setOrderLoading(false))
  }, [])

  const handleZipUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setZipLoading(true)
    setError(null)
    setZipResult(null)
    try {
      const res = await importApi.importZip(file, dryRun)
      setZipResult(res)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'ZIP Import failed')
    } finally {
      setZipLoading(false)
      if (zipInputRef.current) zipInputRef.current.value = ''
    }
  }

  const handleExportZip = async () => {
    setExportLoading(true)
    try {
      const blob = await importApi.exportZip()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'arare_full_export.zip'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Export failed')
    } finally {
      setExportLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <Card title="Import & Export" description="Use fixed sample CSV templates from the backend for single entities, or the full relational ZIP package for round-tripping all relationships.">
        <div className="rounded-lg border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm text-indigo-800">
          Template CSV downloads are backend-generated examples, not live database exports. They include complete columns and sample rows so you can edit them safely before importing.
        </div>
        <div className="flex flex-wrap items-center gap-4">
          <input
            ref={zipInputRef}
            type="file"
            accept=".zip,application/zip"
            className="hidden"
            onChange={handleZipUpload}
          />
          <Button onClick={() => zipInputRef.current?.click()} loading={zipLoading}>
            Upload Relational ZIP Archive
          </Button>
          <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={dryRun}
              onChange={(e) => setDryRun(e.target.checked)}
            />
            Dry run (validate without writing)
          </label>
          <Button variant="secondary" onClick={handleExportZip} loading={exportLoading}>
            Export Full System Archive (ZIP)
          </Button>
        </div>
      </Card>

      {error && (
        <Card title="Import Error">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-red-700">{error}</p>
            <Button variant="ghost" size="sm" onClick={() => setError(null)}>Dismiss</Button>
          </div>
        </Card>
      )}

      <Card
        title="Single Entity Import"
        description="Files are imported in dependency order (top to bottom). Each file upserts by natural key; its dependencies must already exist."
      >
        {orderLoading ? (
          <div className="flex items-center justify-center py-8"><Spinner /></div>
        ) : order.length === 0 ? (
          <p className="text-sm text-gray-400">No importable entity types.</p>
        ) : (
          <div className="space-y-3">
            {order.map((step) => (
              <EntityImportCard key={step.name} step={step} />
            ))}
          </div>
        )}
      </Card>

      {zipResult && (
        <Card title="ZIP Import Summary">
          {zipResult.dryRun && (
            <p className="mb-3 text-sm font-medium text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2">
              Dry run — the archive was validated and no changes were written.
            </p>
          )}
          <div className="space-y-3">
            {Object.entries(zipResult.fileStats).map(([filename, stats]) => (
              <div key={filename} className="border border-gray-200 rounded p-3 text-sm bg-white">
                <div className="flex justify-between items-center mb-1">
                  <span className="font-semibold text-gray-900">{filename}</span>
                  <div className="flex gap-3 text-xs text-gray-600">
                    <span className="text-green-600 font-medium">Created: {stats.created}</span>
                    <span className="text-blue-600 font-medium">Updated: {stats.updated}</span>
                    <span className="text-amber-600 font-medium">Skipped: {stats.skipped}</span>
                  </div>
                </div>
                {stats.errors.length > 0 && (
                  <div className="mt-2 text-xs bg-red-50 text-red-700 p-2 rounded max-h-32 overflow-y-auto">
                    {stats.errors.map((err, idx) => (
                      <div key={idx}>{err}</div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}