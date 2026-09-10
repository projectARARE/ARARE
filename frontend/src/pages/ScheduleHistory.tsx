import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, Trash2, Plus, GitBranch, CheckCircle2, Archive, ShieldCheck, Columns2, RotateCcw } from 'lucide-react'
import { Card, Button, Table, Badge, ConfirmDialog, Modal, Select, FilterPanel, SearchableSelect } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { scheduleApi, instituteApi } from '../services/api'
import type { Schedule, ScheduleStatus, ClassSession, Institute } from '../types'
import { useToast } from '../contexts/ToastContext'

const STATUS_VARIANT: Record<ScheduleStatus, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  DRAFT: 'gray',
  ACTIVE: 'green',
  ARCHIVED: 'blue',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
}

export default function ScheduleHistory() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const [items, setItems] = useState<Schedule[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loading, setLoading] = useState(true)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [statusFilter, setStatusFilter] = useState<string | null>(null)
  const [instituteFilter, setInstituteFilter] = useState<number | null>(null)
  const [scopeFilter, setScopeFilter] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [revalidateResult, setRevalidateResult] = useState<{ scheduleId: number; name: string; result: Schedule } | null>(null)
  const [compareA, setCompareA] = useState<number | null>(null)
  const [compareB, setCompareB] = useState<number | null>(null)
  const [compareData, setCompareData] = useState<{ a: { s: Schedule; sessions: ClassSession[] }; b: { s: Schedule; sessions: ClassSession[] } } | null>(null)
  const [loadingCompare, setLoadingCompare] = useState(false)
  const load = () => {
    setLoading(true)
    Promise.allSettled([scheduleApi.getAll(), instituteApi.getAll()])
      .then(([s, i]) => {
        if (s.status === 'fulfilled') setItems(s.value)
        if (i.status === 'fulfilled') setInstitutes(i.value)
        const failed = [s, i].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some data failed to refresh (${failed}/2)`)
      })
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to load schedules'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleDelete = async () => {
    if (confirmId == null) return
    setDeleting(true)
    try {
      await scheduleApi.delete(confirmId)
      toast.success('Schedule deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const handleActivate = async (s: Schedule) => {
    if (busyId !== null) return
    setBusyId(s.id)
    try {
      await scheduleApi.activate(s.id)
      toast.success(`"${s.name}" is now active`)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to activate schedule')
    } finally {
      setBusyId(null)
    }
  }

  const handleArchive = async (s: Schedule) => {
    if (busyId !== null) return
    setBusyId(s.id)
    try {
      await scheduleApi.archive(s.id)
      toast.success(`"${s.name}" archived`)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to archive schedule')
    } finally {
      setBusyId(null)
    }
  }

  const handleRevalidate = async (s: Schedule) => {
    if (busyId !== null) return
    setBusyId(s.id)
    try {
      const updated = await scheduleApi.revalidate(s.id)
      setItems((prev) => prev.map((x) => (x.id === s.id ? updated : x)))
      setRevalidateResult({ scheduleId: s.id, name: updated.name, result: updated })
      if (updated.status !== 'INFEASIBLE') {
        toast.success(`"${updated.name}" passes hard-constraint checks (${updated.score ?? 'no score'} on board)`)
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Revalidation failed')
    } finally {
      setBusyId(null)
    }
  }

  const handleCompare = async () => {
    if (compareA == null || compareB == null) return
    const a = items.find((s) => s.id === compareA)
    const b = items.find((s) => s.id === compareB)
    if (!a || !b) return
    setLoadingCompare(true)
    setCompareData(null)
    try {
      const [sessionsA, sessionsB] = await Promise.all([scheduleApi.getSessions(a.id), scheduleApi.getSessions(b.id)])
      setCompareData({ a: { s: a, sessions: sessionsA }, b: { s: b, sessions: sessionsB } })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to load sessions for comparison')
    } finally {
      setLoadingCompare(false)
    }
  }

  const getCompareOptions = (exclude?: number) =>
    items
      .filter((s) => s.id !== exclude)
      .map((s) => ({ id: s.id, label: `${s.name} [${s.status}${s.score ? ` · ${s.score}` : ''}]` }))

  const compareRows = useMemo(() => {
    const rows: { label: string; a: string; b: string }[] = []
    if (!compareData) {
      rows.push({ label: 'No data loaded yet', a: '—', b: '—' })
      return rows
    }
    const { a, b } = compareData
    const push = (label: string, av: string, bv: string) => rows.push({ label, a: av, b: bv })
    push('Name', a.s.name, b.s.name)
    push('Scope', a.s.scope, b.s.scope)
    push('Status', a.s.status, b.s.status)
    push('Score', a.s.score ?? '—', b.s.score ?? '—')
    push('Sessions', String(a.sessions.length), String(b.sessions.length))
    push('Locked sessions', String(a.sessions.filter((x) => x.isLocked).length), String(b.sessions.filter((x) => x.isLocked).length))
    push('Created', a.s.createdAt ? new Date(a.s.createdAt).toLocaleString() : '—', b.s.createdAt ? new Date(b.s.createdAt).toLocaleString() : '—')
    push('Base schedule', a.s.parentScheduleId ? `#${a.s.parentScheduleId}` : '—', b.s.parentScheduleId ? `#${b.s.parentScheduleId}` : '—')
    return rows
  }, [compareData])

  const getParentName = (s: Schedule): string | null => {
    if (!s.parentScheduleId) return null
    const parent = items.find((i) => i.id === s.parentScheduleId)
    return parent ? parent.name : `#${s.parentScheduleId}`
  }

  const statusOptions = Array.from(new Set(items.map((s) => s.status))).map((st) => ({ value: st, label: st }))
  const scopeOptions: { value: string; label: string }[] = ['DEPARTMENT', 'INSTITUTE', 'UNIVERSITY'].map((sc) => ({ value: sc, label: sc }))
  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))
  const filteredItems = items.filter((s) => {
    if (statusFilter != null && s.status !== statusFilter) return false
    if (instituteFilter != null && s.instituteId !== instituteFilter) return false
    if (scopeFilter != null && s.scope !== scopeFilter) return false
    return true
  })

  const columns: Column<Schedule>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (s) => s.name,
      render: (s) => (
        <div>
          <span className="font-medium">{s.name}</span>
          {s.parentScheduleId && (
            <div className="flex items-center gap-1 text-xs text-gray-400 mt-0.5">
              <GitBranch size={10} />
              derived from {getParentName(s)}
            </div>
          )}
        </div>
      ),
    },
    {
      key: 'scope', header: 'Scope',
      sortValue: (s) => s.scope,
      render: (s) => <span className="text-sm text-gray-600">{s.scope}</span>,
    },
    {
      key: 'status', header: 'Status',
      sortValue: (s) => s.status,
      render: (s) => <Badge label={s.status} variant={STATUS_VARIANT[s.status]} dot />,
    },
    {
      key: 'score', header: 'Score',
      render: (s) => s.score
        ? <code className="text-xs bg-gray-100 px-1 rounded">{s.score}</code>
        : <span className="text-gray-400">—</span>,
    },
    {
      key: 'created', header: 'Created',
      sortValue: (s) => s.createdAt ?? '',
      render: (s) => {
        if (!s.createdAt) return '—'
        const d = new Date(s.createdAt)
        return isNaN(d.getTime()) ? '—' : d.toLocaleString()
      },
    },
    {
      key: 'actions', header: '', width: '190px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Eye size={14} />} className="px-1.5"
            onClick={() => navigate(`/schedule/view/${s.id}`)} title="View" aria-label="View" />
          {s.status === 'DRAFT' && (
            <Button variant="ghost" size="sm" icon={<CheckCircle2 size={14} />}
              className="text-green-600 hover:text-green-700 px-1.5"
              loading={busyId === s.id}
              disabled={busyId !== null}
              onClick={() => handleActivate(s)} title="Publish" aria-label="Publish" />
          )}
          {(s.status === 'ACTIVE' || s.status === 'PARTIAL') && (
            <Button variant="ghost" size="sm" icon={<Archive size={14} />}
              className="px-1.5"
              loading={busyId === s.id}
              disabled={busyId !== null}
              onClick={() => handleArchive(s)} title="Archive" aria-label="Archive" />
          )}
          {(s.status === 'DRAFT' || s.status === 'INFEASIBLE') && (
            <Button variant="ghost" size="sm" icon={<ShieldCheck size={14} />}
              className="text-cyan-700 hover:text-cyan-800 px-1.5"
              loading={busyId === s.id}
              disabled={busyId !== null}
              onClick={() => handleRevalidate(s)} title="Revalidate" aria-label="Revalidate" />
          )}
          {s.status === 'ARCHIVED' && (
            <Button variant="ghost" size="sm" icon={<RotateCcw size={14} />}
              className="text-indigo-600 hover:text-indigo-700 px-1.5"
              loading={busyId === s.id}
              disabled={busyId !== null}
              onClick={() => handleRevalidate(s)} title="Unarchive (re-open for editing)" aria-label="Unarchive" />
          )}
          <Button variant="ghost" size="sm" icon={<Columns2 size={14} />} className="px-1.5"
            onClick={() => { setCompareA(s.id); setCompareB(null); setCompareData(null) }} title="Compare" aria-label="Compare" />
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />}
            className="text-red-600 hover:text-red-700 px-1.5"
            onClick={() => setConfirmId(s.id)} title="Delete" aria-label="Delete" />
        </div>
      ),
    },
  ]

  const getContextItems = (s: Schedule): ContextMenuItem[] => {
    const items: ContextMenuItem[] = [
      { label: 'View', icon: <Eye size={13} />, onClick: () => navigate(`/schedule/view/${s.id}`) },
      { label: 'Continue from this', icon: <GitBranch size={13} />, onClick: () => navigate(`/schedule/generate?parentId=${s.id}`) },
    ]
    if (s.status === 'DRAFT') {
      items.push({ label: 'Publish', icon: <CheckCircle2 size={13} />, onClick: () => handleActivate(s) })
    }
    if (s.status === 'ACTIVE' || s.status === 'PARTIAL') {
      items.push({ label: 'Archive', icon: <Archive size={13} />, onClick: () => handleArchive(s) })
    }
    if (s.status === 'DRAFT' || s.status === 'INFEASIBLE') {
      items.push({ label: 'Revalidate', icon: <ShieldCheck size={13} />, onClick: () => handleRevalidate(s) })
    }
    if (s.status === 'ARCHIVED') {
      items.push({ label: 'Unarchive (re-open for editing)', icon: <RotateCcw size={13} />, onClick: () => handleRevalidate(s) })
    }
    items.push({ label: 'Compare…', icon: <Columns2 size={13} />, onClick: () => { setCompareA(s.id); setCompareB(null); setCompareData(null) } })
    items.push({ label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(s.id) })
    return items
  }

  return (
    <>
      <Card
        title="Schedule History"
        description="All generated timetables. Right-click any row for options."
        actions={<Button icon={<Plus size={16} />} onClick={() => navigate('/schedule/generate')}>New Schedule</Button>}
      >
        <FilterPanel activeCount={(statusFilter != null ? 1 : 0) + (instituteFilter != null ? 1 : 0) + (scopeFilter != null ? 1 : 0)} persistKey="arare.scheduleHistory.filters.open">
          {institutes.length > 0 && (
            <SearchableSelect
              label="Institute"
              value={instituteFilter}
              onChange={(v) => setInstituteFilter(v == null ? null : +v)}
              options={instituteOptions}
              placeholder="All institutes"
              allowClear
              className="w-72"
            />
          )}
          <Select
            label="Scope"
            value={scopeFilter ?? ''}
            onChange={(e) => setScopeFilter(e.target.value || null)}
            options={[{ value: '', label: 'All scopes' }, ...scopeOptions]}
          />
          <Select
            label="Status"
            value={statusFilter ?? ''}
            onChange={(e) => setStatusFilter(e.target.value || null)}
            options={[{ value: '', label: 'All statuses' }, ...statusOptions]}
          />
        </FilterPanel>
        <Table
          columns={columns}
          data={filteredItems}
          loading={loading}
          keyExtractor={(s) => s.id}
          searchable
          exportable
          exportFilename="schedule-history"
          searchKeys={[(s) => s.name, (s) => s.scope, (s) => s.status]}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal
        open={revalidateResult !== null}
        title={`Revalidate "${revalidateResult?.name ?? ''}"`}
        onClose={() => setRevalidateResult(null)}
        footer={
          <Button variant="secondary" onClick={() => setRevalidateResult(null)}>Close</Button>
        }
      >
        {revalidateResult && (
          <div className="space-y-3 text-sm">
            <div className={`rounded-lg border px-4 py-3 ${revalidateResult.result.status === 'INFEASIBLE' ? 'border-rose-200 bg-rose-50 text-rose-800' : 'border-emerald-200 bg-emerald-50 text-emerald-800'}`}>
              <p className="font-medium">
                {revalidateResult.result.status === 'INFEASIBLE'
                  ? 'Hard constraints are violated — the schedule can no longer be published as-is.'
                  : 'All hard constraints still hold.'}
              </p>
              <p className="mt-1 text-xs">
                Status: {revalidateResult.result.status} · Score:{' '}
                <code className="bg-white/60 px-1 rounded">{revalidateResult.result.score ?? '—'}</code>
              </p>
            </div>
            {revalidateResult.result.scoreExplanation && (
              <div>
                <p className="text-xs uppercase tracking-wide font-medium text-gray-500 mb-1">Live score breakdown</p>
                <pre className="whitespace-pre-wrap text-xs text-gray-700 bg-gray-50 rounded-lg p-3 font-mono">{revalidateResult.result.scoreExplanation}</pre>
              </div>
            )}
            <p className="text-xs text-gray-500">
              Revalidation re-checks every assignment against the same hard constraints the solver enforces,
              without re-running the solver.
            </p>
          </div>
        )}
      </Modal>

      <Modal
        open={compareA !== null}
        title="Compare schedules"
        onClose={() => { setCompareA(null); setCompareB(null); setCompareData(null) }}
        footer={
          <>
            <Button variant="secondary" onClick={() => { setCompareA(null); setCompareB(null); setCompareData(null) }}>Close</Button>
            <Button
              loading={loadingCompare}
              disabled={compareA == null || compareB == null || compareA === compareB}
              onClick={handleCompare}
            >
              Run comparison
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Schedule A</label>
              <select
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                value={compareA ?? ''}
                onChange={(e) => { setCompareA(e.target.value === '' ? null : +e.target.value); setCompareData(null) }}
              >
                <option value="">— choose —</option>
                {getCompareOptions(compareB ?? undefined).map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Schedule B</label>
              <select
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                value={compareB ?? ''}
                onChange={(e) => { setCompareB(e.target.value === '' ? null : +e.target.value); setCompareData(null) }}
              >
                <option value="">— choose —</option>
                {getCompareOptions(compareA ?? undefined).map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
              </select>
            </div>
          </div>
          {compareData && (
            <div className="overflow-hidden border border-gray-200 rounded-lg">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 text-left text-xs text-gray-500">
                    <th className="px-3 py-2 font-medium">Property</th>
                    <th className="px-3 py-2 font-medium">A</th>
                    <th className="px-3 py-2 font-medium">B</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {compareRows.map((row) => (
                    <tr key={row.label}>
                      <td className="px-3 py-2 text-gray-500">{row.label}</td>
                      <td className={`px-3 py-2 ${row.a === row.b ? '' : 'font-medium text-cyan-800'}`}>{row.a}</td>
                      <td className={`px-3 py-2 ${row.a === row.b ? '' : 'font-medium text-cyan-800'}`}>{row.b}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Schedule"
        message="This will delete the schedule and all its sessions. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
