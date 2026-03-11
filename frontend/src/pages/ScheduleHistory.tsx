import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, Trash2, Plus } from 'lucide-react'
import { Card, Button, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { scheduleApi } from '../services/api'
import type { Schedule, ScheduleStatus } from '../types'

const STATUS_VARIANT: Record<ScheduleStatus, 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'> = {
  DRAFT: 'gray',
  ACTIVE: 'green',
  ARCHIVED: 'blue',
  PARTIAL: 'yellow',
  INFEASIBLE: 'red',
}

export default function ScheduleHistory() {
  const navigate = useNavigate()
  const [items, setItems] = useState<Schedule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    scheduleApi.getAll().then(setItems).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleDelete = async (id: number) => {
    if (!window.confirm('Delete this schedule?')) return
    try {
      await scheduleApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const columns: Column<Schedule>[] = [
    { key: 'name', header: 'Name', render: (s) => <span className="font-medium">{s.name}</span> },
    { key: 'scope', header: 'Scope', render: (s) => s.scope },
    { key: 'status', header: 'Status', render: (s) => <Badge label={s.status} variant={STATUS_VARIANT[s.status]} dot /> },
    {
      key: 'score', header: 'Score',
      render: (s) => s.score ? <code className="text-xs bg-gray-100 px-1 rounded">{s.score}</code> : '—',
    },
    {
      key: 'created', header: 'Created',
      render: (s) => s.createdAt ? new Date(s.createdAt).toLocaleString() : '—',
    },
    {
      key: 'actions', header: '', width: '120px',
      render: (s) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Eye size={14} />} onClick={() => navigate(`/schedule/view/${s.id}`)}>View</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(s.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  return (
    <>
      {error && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      <Card
        title="Schedule History"
        description="All generated timetables"
        actions={<Button icon={<Plus size={16} />} onClick={() => navigate('/schedule/generate')}>New Schedule</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(s) => s.id} />
      </Card>
    </>
  )
}
