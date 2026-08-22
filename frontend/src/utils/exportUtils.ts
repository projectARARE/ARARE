import { scheduleApi } from '../services/api'

export interface ExportColumn<T> {
  header: string
  value: (row: T) => string | number | null | undefined
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function cellText(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return ''
  return String(value)
}

export function exportCsv<T>(filename: string, columns: ExportColumn<T>[], rows: T[]) {
  const esc = (v: string) => (v.includes(',') || v.includes('"') || v.includes('\n') ? `"${v.replace(/"/g, '""')}"` : v)
  const lines = [
    columns.map((c) => esc(c.header)).join(','),
    ...rows.map((r) => columns.map((c) => esc(cellText(c.value(r)))).join(',')),
  ]
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
  downloadBlob(blob, filename)
}

export async function exportExcel<T>(filename: string, columns: ExportColumn<T>[], rows: T[]) {
  const blob = await scheduleApi.exportRowsExcel(
    filename.replace(/\.xlsx$/i, ''),
    columns.map((c) => c.header),
    rows.map((r) => columns.map((c) => cellText(c.value(r)))),
  )
  downloadBlob(blob, filename.endsWith('.xlsx') ? filename : `${filename}.xlsx`)
}