import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { buildingApi } from '../services/api'
import type { Building, BuildingRequest } from '../types'
import { useToast } from '../contexts/ToastContext'

const EMPTY: BuildingRequest = { name: '', location: '' }

export default function Buildings() {
  const { toast } = useToast()
  const [items, setItems] = useState<Building[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Building | null>(null)
  const [form, setForm] = useState<BuildingRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    buildingApi.getAll().then(setItems).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (b: Building) => {
    setEditing(b)
    setForm({ name: b.name, location: b.location ?? '' })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await buildingApi.update(editing.id, form)
        toast.success('Building updated')
      } else {
        await buildingApi.create(form)
        toast.success('Building created')
      }
      setOpen(false)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirmId == null) return
    setDeleting(true)
    try {
      await buildingApi.delete(confirmId)
      toast.success('Building deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const columns: Column<Building>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (b) => b.name,
      render: (b) => <span className="font-medium">{b.name}</span>,
    },
    { key: 'location', header: 'Location', render: (b) => b.location ?? '—' },
    {
      key: 'actions', header: '', width: '96px',
      render: (b) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(b)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700" onClick={() => setConfirmId(b.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (b: Building): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(b) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(b.id) },
  ]

  return (
    <>
      <Card
        title="Buildings"
        description="Manage campus buildings"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Building</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(b) => b.id}
          searchable
          searchKeys={[(b) => b.name, (b) => b.location ?? '']}
          onRowContextMenu={getContextItems}
        />
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
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Engineering Block" required />
          <Input label="Location" value={form.location ?? ''} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="North Campus, Block A" helpText="Human-readable campus location (optional)" />
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Building"
        message="This will also remove all rooms and clear related session assignments. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
