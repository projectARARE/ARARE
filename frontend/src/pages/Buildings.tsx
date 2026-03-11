import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Table } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { buildingApi } from '../services/api'
import type { Building, BuildingRequest } from '../types'

const EMPTY: BuildingRequest = { name: '', location: '' }

export default function Buildings() {
  const [items, setItems] = useState<Building[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Building | null>(null)
  const [form, setForm] = useState<BuildingRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    buildingApi.getAll().then(setItems).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setError(null); setOpen(true) }
  const openEdit = (b: Building) => {
    setEditing(b)
    setForm({ name: b.name, location: b.location ?? '' })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { setError('Name is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await buildingApi.update(editing.id, form)
      } else {
        await buildingApi.create(form)
      }
      setOpen(false)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm('Delete this building?')) return
    try {
      await buildingApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const columns: Column<Building>[] = [
    { key: 'name', header: 'Name', render: (b) => <span className="font-medium">{b.name}</span> },
    { key: 'location', header: 'Location', render: (b) => b.location ?? '—' },
    {
      key: 'actions', header: '', width: '96px',
      render: (b) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(b)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700" onClick={() => handleDelete(b.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  return (
    <>
      {error && !open && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      <Card
        title="Buildings"
        description="Manage campus buildings"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Building</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(b) => b.id} />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Building' : 'Add Building'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Engineering Block" required />
          <Input label="Location" value={form.location ?? ''} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="North Campus, Block A" helpText="Human-readable campus location (optional)" />
        </div>
      </Modal>
    </>
  )
}
