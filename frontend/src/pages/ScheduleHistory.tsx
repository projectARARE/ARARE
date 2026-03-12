import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, Trash2, Plus, GitBranch } from 'lucide-react'
import { Card, Button, Table, Badge, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { scheduleApi } from '../services/api'
import type { Schedule, ScheduleStatus } from '../types'
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
  const [loading, setLoading] = useState(true)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    scheduleApi.getAll()
      .then(setItems)
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

  const getParentName = (s: Schedule): string | null => {
    if (!s.parentScheduleId) return null
    const parent = items.find((i) => i.id === s.parentScheduleId)
    return parent ? parent.name : `#${s.parentScheduleId}`
  }

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
      render: (s) => s.createdAt ? new Date(s.createdAt).toLocaleString() : '—',
    },
    {
      key: 'actions', header: '', width: '130px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Eye size={14} />}
            onClick={() => navigate(`/schedule/view/${s.id}`)}>View</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />}
            className="text-red-600 hover:text-red-700"
            onClick={() => setConfirmId(s.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (s: Schedule): ContextMenuItem[] => [
    { label: 'View', icon: <Eye size={13} />, onClick: () => navigate(`/schedule/view/${s.id}`) },
    { label: 'Continue from this', icon: <GitBranch size={13} />, onClick: () => navigate(`/schedule/generate?parentId=${s.id}`) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(s.id) },
  ]

  return (
    <>
      <Card
        title="Schedule History"
        description="All generated timetables. Right-click any row for options."
        actions={<Button icon={<Plus size={16} />} onClick={() => navigate('/schedule/generate')}>New Schedule</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(s) => s.id}
          searchable
          searchKeys={[(s) => s.name, (s) => s.scope, (s) => s.status]}
          onRowContextMenu={getContextItems}
        />
      </Card>

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
